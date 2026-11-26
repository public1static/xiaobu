<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import {
  createGiftBook,
  dailyGiftBook,
  listGiftActivity,
  listGiftBooks,
  listGiftContacts,
  setGiftBookArchived,
  type GiftActivity,
  type GiftBook,
  type GiftContact,
} from '../../api/ledger-gifts';
import { giftEntryPreset } from './gift-entry-extension';
import type { TransactionEntryPreset } from './transaction-extension-ui';
import { ApiRequestError } from '../../api/request';
import { formatCents, localDay, type TransactionDirection } from './transaction';

const props = defineProps<{
  ledgerId: string;
  writable: boolean;
  revision: number;
  covered?: boolean;
}>();
const emit = defineEmits<{
  (event: 'close'): void;
  (event: 'quick', preset: TransactionEntryPreset): void;
  (event: 'detail', id: string): void;
}>();
const tab = ref<'activity' | 'books' | 'contacts'>('activity');
const currentBook = ref<GiftBook | null>(null);
const currentContact = ref<GiftContact | null>(null);
const books = ref<GiftBook[]>([]);
const contacts = ref<GiftContact[]>([]);
const rows = ref<GiftActivity[]>([]);
const summary = ref({ incomeCents: 0, expenseCents: 0, totalCount: 0 });
const currentPage = ref(0);
let searchDelay: ReturnType<typeof setTimeout> | undefined;
const query = ref('');
const busy = ref(false);
const error = ref('');
const loading = ref(false);
const creating = ref(false);
const name = ref('');
const direction = ref<TransactionDirection>('INCOME');
const defaultDate = ref('');
const defaultReason = ref('');
const tailToday = localDay();
let requestSequence = 0;

const displayedBooks = computed(() => [
  dailyGiftBook('INCOME'),
  dailyGiftBook('EXPENSE'),
  ...books.value,
]);
const visibleContacts = computed(() =>
  contacts.value.filter((person) =>
    (person.name + person.distinction).includes(query.value.trim()),
  ),
);
function errorText(failure: unknown, fallback: string) {
  return failure instanceof ApiRequestError ? failure.message : fallback;
}

async function loadRows(append = false) {
  clearTimeout(searchDelay);
  const sequence = ++requestSequence;
  loading.value = true;
  error.value = '';
  if (!append) {
    rows.value = [];
    summary.value = { incomeCents: 0, expenseCents: 0, totalCount: 0 };
  }
  try {
    const filter = currentBook.value
      ? { bookId: currentBook.value.id }
      : currentContact.value
        ? { contactId: currentContact.value.id }
        : {};
    const result = await listGiftActivity(props.ledgerId, {
      ...filter,
      keyword: query.value.trim(),
      page: append ? currentPage.value + 1 : 0,
      pageSize: 20,
    });
    if (sequence !== requestSequence) return;
    rows.value = append ? [...rows.value, ...result.entries] : result.entries;
    currentPage.value = result.page;
    summary.value = {
      incomeCents: result.incomeCents,
      expenseCents: result.expenseCents,
      totalCount: result.totalCount,
    };
  } catch (failure) {
    if (sequence === requestSequence) error.value = errorText(failure, '往来加载失败。');
  } finally {
    if (sequence === requestSequence) loading.value = false;
  }
}

async function refresh() {
  try {
    const [nextBooks, nextContacts] = await Promise.all([
      listGiftBooks(props.ledgerId),
      listGiftContacts(props.ledgerId),
    ]);
    books.value = nextBooks;
    contacts.value = nextContacts;
  } catch (failure) {
    error.value = errorText(failure, '礼单或亲友暂未加载。');
  }
  if (tab.value === 'activity' || currentBook.value || currentContact.value) await loadRows();
}

function selectTab(selection: typeof tab.value) {
  tab.value = selection;
  currentBook.value = null;
  currentContact.value = null;
  creating.value = false;
  query.value = '';
  error.value = '';
  if (selection === 'activity') void loadRows();
}

function showBook(book: GiftBook) {
  creating.value = false;
  currentBook.value = book;
  query.value = '';
  error.value = '';
  void loadRows();
}

function showContact(contact: GiftContact) {
  currentContact.value = contact;
  query.value = '';
  error.value = '';
  void loadRows();
}

function showCreate() {
  creating.value = true;
  error.value = '';
  name.value = '';
  direction.value = 'INCOME';
  defaultDate.value = '';
  defaultReason.value = '';
}

async function submitBook() {
  if (busy.value) return;
  if (!name.value.trim()) {
    error.value = '请输入礼单名称。';
    return;
  }
  busy.value = true;
  try {
    const book = await createGiftBook(props.ledgerId, {
      name: name.value.trim(),
      direction: direction.value,
      defaultDate: defaultDate.value || null,
      defaultReason: defaultReason.value.trim() || null,
    });
    books.value = [book, ...books.value];
    showBook(book);
  } catch (failure) {
    error.value = errorText(failure, '礼单未保存。');
  } finally {
    busy.value = false;
  }
}

async function toggleArchive() {
  const book = currentBook.value;
  if (!book || book.daily || busy.value) return;
  busy.value = true;
  try {
    await setGiftBookArchived(props.ledgerId, book.id, !book.archived);
    const updated = { ...book, archived: !book.archived };
    currentBook.value = updated;
    books.value = books.value.map((item) => (item.id === book.id ? updated : item));
    error.value = '';
  } catch (failure) {
    error.value = errorText(failure, '礼单状态未更新。');
  } finally {
    busy.value = false;
  }
}

function bookLabel(book: GiftBook) {
  return [
    book.daily ? '默认礼单' : book.direction === 'INCOME' ? '收礼' : '送礼',
    book.defaultDate,
    book.defaultReason,
    book.archived ? '已归档' : '',
  ]
    .filter(Boolean)
    .join(' · ');
}
function rowTitle(row: GiftActivity) {
  return [
    row.personName || (row.direction === 'INCOME' ? '未登记送礼人' : '未登记随礼对象'),
    row.distinction,
  ]
    .filter(Boolean)
    .join(' · ');
}
onMounted(refresh);
watch(() => props.revision, refresh);
watch(query, () => {
  if (creating.value || (!currentBook.value && !currentContact.value && tab.value !== 'activity'))
    return;
  requestSequence++;
  clearTimeout(searchDelay);
  loading.value = true;
  searchDelay = setTimeout(() => void loadRows(), 250);
});
onBeforeUnmount(() => {
  requestSequence++;
  clearTimeout(searchDelay);
});
</script>

<template>
  <view class="overlay gift-workspace-overlay" :aria-hidden="covered || undefined">
    <view
      class="sheet gift-workspace"
      role="dialog"
      :aria-label="creating ? '新建礼单' : currentBook?.name || currentContact?.name || '礼金往来'"
      aria-modal="true"
    >
      <view class="workspace-head">
        <button
          v-if="creating || currentBook || currentContact"
          class="text-button"
          aria-label="返回礼金往来"
          @click="selectTab(tab)"
        >
          ‹
        </button>
        <button v-else class="text-button" aria-label="关闭礼金往来" @click="emit('close')">
          ×
        </button>
        <text>{{
          creating ? '新建礼单' : currentBook?.name || currentContact?.name || '礼金往来'
        }}</text>
        <button
          v-if="writable && tab === 'books' && !creating && !currentBook"
          class="text-button"
          @click="showCreate"
        >
          新增
        </button>
        <button
          v-else-if="writable && currentBook && !currentBook.archived"
          class="text-button"
          aria-label="新增礼单往来"
          @click="emit('quick', giftEntryPreset(currentBook))"
        >
          新增
        </button>
      </view>
      <view v-if="!creating && !currentBook && !currentContact" class="workspace-tabs">
        <button
          class="text-button"
          :class="{ current: tab === 'activity' }"
          @click="selectTab('activity')"
        >
          往来
        </button>
        <button
          class="text-button"
          :class="{ current: tab === 'books' }"
          @click="selectTab('books')"
        >
          礼单
        </button>
        <button
          class="text-button"
          :class="{ current: tab === 'contacts' }"
          @click="selectTab('contacts')"
        >
          亲友
        </button>
      </view>

      <template v-if="creating">
        <view class="meta">
          <view class="workspace-field"
            ><text>收送方向<text class="star">*</text></text>
            <view class="direction-tabs">
              <button
                class="direction-tab"
                :class="{ selected: direction === 'INCOME' }"
                @click="direction = 'INCOME'"
              >
                收礼
              </button>
              <button
                class="direction-tab"
                :class="{ selected: direction === 'EXPENSE' }"
                @click="direction = 'EXPENSE'"
              >
                送礼
              </button>
            </view>
          </view>
          <view class="workspace-field"
            ><text>礼单名称<text class="star">*</text></text>
            <input
              v-model="name"
              :maxlength="60"
              aria-label="礼单名称"
              placeholder="输入礼单名称"
            />
          </view>
          <view class="workspace-field"
            ><text>默认日期</text>
            <picker
              mode="date"
              :value="defaultDate || tailToday"
              @change="defaultDate = $event.detail.value"
            >
              <text>{{ defaultDate || '选择日期' }} ›</text>
            </picker>
          </view>
          <view class="workspace-field"
            ><text>默认事由</text>
            <input v-model="defaultReason" :maxlength="60" aria-label="默认事由" />
          </view>
        </view>
        <button class="primary save" :disabled="busy" @click="submitBook">创建礼单</button>
      </template>

      <template v-else-if="tab === 'books' && !currentBook">
        <view class="meta">
          <button
            v-for="book in displayedBooks"
            :key="book.id"
            class="workspace-row"
            @click="showBook(book)"
          >
            <view
              ><text class="workspace-title">{{ book.name }}</text
              ><text class="workspace-caption">{{ bookLabel(book) }}</text></view
            >
            <text aria-hidden="true">›</text>
          </button>
        </view>
      </template>

      <template v-else-if="tab === 'contacts' && !currentContact">
        <view class="workspace-search"
          ><input v-model="query" placeholder="搜索亲友" aria-label="搜索亲友"
        /></view>
        <view class="meta">
          <button
            v-for="contact in visibleContacts"
            :key="contact.id"
            class="workspace-row"
            @click="showContact(contact)"
          >
            <view
              ><text class="workspace-title">{{ contact.name }}</text
              ><text v-if="contact.distinction" class="workspace-caption">{{
                contact.distinction
              }}</text></view
            >
            <text aria-hidden="true">›</text>
          </button>
          <text v-if="!visibleContacts.length" class="workspace-empty">没有匹配的亲友</text>
        </view>
      </template>

      <template v-else>
        <view v-if="currentBook" class="workspace-book-subtitle">
          <text>{{ bookLabel(currentBook) }}</text>
          <button
            v-if="writable && !currentBook.daily"
            class="text-button"
            :disabled="busy"
            @click="toggleArchive"
          >
            {{ currentBook.archived ? '重新开放' : '归档礼单' }}
          </button>
        </view>
        <text v-if="currentContact?.distinction" class="workspace-caption">{{
          currentContact.distinction
        }}</text>
        <view class="workspace-totals">
          <view
            ><text>收礼</text
            ><text class="workspace-total-value"
              >¥{{ formatCents(summary.incomeCents) }}</text
            ></view
          >
          <view
            ><text>送礼</text
            ><text class="workspace-total-value"
              >¥{{ formatCents(summary.expenseCents) }}</text
            ></view
          >
        </view>
        <view class="workspace-search"
          ><input v-model="query" placeholder="找人或备注" aria-label="找人或备注" :maxlength="100"
        /></view>
        <view class="meta">
          <button
            v-for="row in rows"
            :key="row.transactionId"
            class="workspace-row"
            @click="emit('detail', row.transactionId)"
          >
            <view
              ><text class="workspace-title">{{ rowTitle(row) }}</text
              ><text class="workspace-caption">{{
                [row.bookedOn, row.reason || row.note].filter(Boolean).join(' · ')
              }}</text></view
            >
            <text
              >{{ row.direction === 'INCOME' ? '+' : '−' }}¥{{ formatCents(row.amountCents) }}</text
            >
          </button>
          <text v-if="loading" class="workspace-empty">正在加载往来</text>
          <text v-else-if="!rows.length" class="workspace-empty">这段还没有往来</text>
        </view>
        <button
          v-if="rows.length < summary.totalCount"
          class="text-button"
          :disabled="loading"
          @click="loadRows(true)"
        >
          加载更多
        </button>
      </template>
      <text v-if="error" class="error" role="alert">{{ error }}</text>
      <button v-if="error && !creating" class="text-button" @click="refresh">重新加载</button>
    </view>
  </view>
</template>

<style scoped src="./ledger.css"></style>
<style scoped>
.overlay.gift-workspace-overlay {
  z-index: 2;
}
.gift-workspace {
  height: 100vh;
  height: 100dvh;
  max-height: 100dvh;
  border-radius: 0;
}
.gift-workspace > * {
  flex-shrink: 0;
}
.workspace-head {
  display: grid;
  grid-template-columns: 54px minmax(0, 1fr) 54px;
  align-items: center;
  text-align: center;
  font-size: 16px;
  font-weight: 700;
}
.workspace-head button {
  justify-self: center;
}
.workspace-tabs {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  border-bottom: 1px solid #e4eae5;
}
.workspace-tabs .current {
  border-bottom: 2px solid #176a57;
  font-weight: 700;
}
.workspace-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  min-height: 53px;
  text-align: left;
  gap: 12px;
  padding: 9px 13px;
  background: #fff;
  color: #182623;
  font-size: 14px;
  line-height: 1.4;
  border-radius: 0;
}
.workspace-row + .workspace-row,
.workspace-field + .workspace-field {
  border-top: 1px solid #e4eae5;
}
.workspace-title,
.workspace-caption {
  display: block;
  overflow-wrap: anywhere;
}
.workspace-caption {
  color: #536660;
  font-size: 12px;
}
.workspace-search {
  background: #fff;
  border: 1px solid #e4eae5;
  border-radius: 18px;
  padding: 9px 13px;
  font-size: 14px;
}
.workspace-field {
  display: grid;
  grid-template-columns: 84px minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  min-height: 47px;
  padding: 6px 13px;
  font-size: 14px;
}
.workspace-field .star {
  margin-left: 3px;
}
.workspace-book-subtitle {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 13px;
  color: #536660;
}
.workspace-totals {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  background: #eaf4ee;
  border-radius: 14px;
  padding: 12px 13px;
  color: #176a57;
}
.workspace-totals view {
  display: grid;
  gap: 4px;
  font-size: 13px;
}
.workspace-total-value {
  font-size: 17px;
}
.workspace-empty {
  display: block;
  padding: 19px 13px;
  text-align: center;
  color: #536660;
  font-size: 14px;
}
</style>
