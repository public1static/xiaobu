<script setup lang="ts">
import { pinyin } from 'pinyin-pro';
import GiftReasonPicker from './GiftReasonPicker.vue';
import LedgerPicker from '../../components/LedgerPicker.vue';
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import {
  createGiftBook,
  createGiftContact,
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
// 姓名读音同时用于拼音搜索、首字母索引；组内保留服务端返回的排序。
const alphabet = [...'ABCDEFGHIJKLMNOPQRSTUVWXYZ', '#'];
const directory = computed(() =>
  contacts.value.map((item) => {
    const words = pinyin(item.name, { toneType: 'none', type: 'array' });
    const initials = words
      .map((word) => word[0])
      .join('')
      .toUpperCase();
    return {
      item,
      initial: /^[A-Z]/.test(initials) ? initials[0] : '#',
      terms: [item.name, item.distinction, words.join(''), initials].join(' ').toLowerCase(),
    };
  }),
);
const groups = computed(() => {
  const result: Record<string, GiftContact[]> = {};
  for (const entry of directory.value) {
    if (entry.terms.includes(query.value.trim().toLowerCase()))
      (result[entry.initial] ??= []).push(entry.item);
  }
  return result;
});
const scrollTarget = ref('');
const activeInitial = ref('');
// 滑过字母时只定位到存在的分组，与点击索引使用同一入口。
function slideIndex(event: { touches: { clientY: number }[] }) {
  const point = event.touches[0];
  if (!point) return;
  const viewport = uni.getWindowInfo().windowHeight;
  const height = Math.min(viewport * 0.59, 430);
  const index = Math.floor(
    ((point.clientY - (viewport * 0.5 - height * 0.45)) / height) * alphabet.length,
  );
  const key = alphabet[index];
  if (key && groups.value[key] && key !== activeInitial.value) void locate(key);
}
const contactForm = ref(false),
  contactName = ref(''),
  contactDistinction = ref('');
// 同一字母可重复定位，适用于触屏与键盘按钮。
async function locate(key: string) {
  activeInitial.value = key;
  scrollTarget.value = '';
  await nextTick();
  scrollTarget.value = 'people-' + (key === '#' ? 'other' : key);
}
// 通过现有账本亲友接口新增，成功后打开本人往来。
async function saveContact() {
  if (busy.value) return;
  if (!contactName.value.trim()) {
    error.value = '请输入姓名。';
    return;
  }
  busy.value = true;
  error.value = '';
  try {
    const item = await createGiftContact(
      props.ledgerId,
      contactName.value.trim(),
      contactDistinction.value.trim(),
    );
    await refresh();
    contactForm.value = false;
    showContact(item);
  } catch (e) {
    error.value =
      e instanceof ApiRequestError && e.status === 409
        ? '请填写不同的称呼或关系，以区分同名亲友。'
        : '亲友未保存，请重试。';
  } finally {
    busy.value = false;
  }
}
function errorText(failure: unknown, fallback: string) {
  return failure instanceof ApiRequestError ? failure.message : fallback;
}

// 按当前筛选加载往来；跨页期间账本变更时从首页重新读取。
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
    if (append) {
      const loaded = new Set(rows.value.map((row) => row.transactionId));
      if (
        result.totalCount !== summary.value.totalCount ||
        result.incomeCents !== summary.value.incomeCents ||
        result.expenseCents !== summary.value.expenseCents ||
        result.entries.some((row) => loaded.has(row.transactionId))
      ) {
        await loadRows();
        return;
      }
    }
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
  contactForm.value = false;
  activeInitial.value = '';
  scrollTarget.value = '';
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
      :aria-label="
        contactForm
          ? '新增亲友'
          : creating
            ? '新建礼单'
            : currentBook?.name || (currentContact ? '亲友详情' : '礼金往来')
      "
      aria-modal="true"
    >
      <view class="workspace-head">
        <button
          v-if="contactForm || creating || currentBook || currentContact"
          class="text-button"
          aria-label="返回礼金往来"
          @click="selectTab(tab)"
        >
          ‹
        </button>
        <button v-else class="text-button" aria-label="关闭礼金往来" @click="emit('close')">
          ‹
        </button>
        <text>{{
          contactForm
            ? '新增亲友'
            : creating
              ? '新建礼单'
              : currentBook?.name || (currentContact ? '亲友详情' : '礼金往来')
        }}</text>
        <button
          v-if="writable && tab === 'contacts' && !currentContact && !contactForm"
          class="text-button"
          aria-label="新增亲友"
          @click="
            contactForm = true;
            contactName = '';
            contactDistinction = '';
            error = '';
          "
        >
          新增
        </button>
        <button
          v-else-if="writable && tab === 'books' && !creating && !currentBook"
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
      <view
        v-if="!creating && !currentBook && !currentContact && !contactForm"
        class="workspace-tabs"
      >
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

      <template v-if="contactForm"
        ><view class="meta"
          ><view class="form-row"
            ><text>姓名<text class="star">*</text></text
            ><input aria-label="亲友姓名" v-model="contactName" maxlength="60" /></view
          ><view class="form-row"
            ><text>称呼或关系</text
            ><input
              aria-label="称呼或关系"
              v-model="contactDistinction"
              maxlength="60" /></view></view
        ><button class="primary" :disabled="busy" @click="saveContact">保存亲友</button></template
      >
      <template v-else-if="creating">
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
            <view class="clear-field"
              ><LedgerPicker
                label="选择默认日期"
                mode="date"
                :value="defaultDate || tailToday"
                @change="defaultDate = $event.detail.value"
              >
                <text>{{ defaultDate || '选择日期' }} ›</text> </LedgerPicker
              ><button
                v-if="defaultDate"
                aria-label="清除默认日期"
                class="clear-control"
                @click="defaultDate = ''"
              >
                ⊗
              </button></view
            >
          </view>
          <GiftReasonPicker
            :ledger-id="ledgerId"
            label="默认事由"
            :value="defaultReason"
            :disabled="busy"
            @select="defaultReason = $event"
          />
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
        <view class="directory-search"
          ><input v-model="query" placeholder="搜索姓名、称呼或拼音" aria-label="搜索亲友"
        /></view>
        <scroll-view class="directory-list" scroll-y :scroll-into-view="scrollTarget">
          <template v-for="key in alphabet" :key="key"
            ><view
              v-if="groups[key]"
              :id="'people-' + (key === '#' ? 'other' : key)"
              class="people-group"
              ><text class="initial">{{ key }}</text>
              <button
                v-for="person in groups[key]"
                :key="person.id"
                class="person-link"
                @click="showContact(person)"
              >
                <text class="avatar">{{ Array.from(person.name)[0] }}</text><view
                  class="person-copy"
                  ><text>{{ person.name }}</text
                  ><text v-if="person.distinction" class="workspace-caption">{{
                    person.distinction
                  }}</text></view
                ><text>›</text>
              </button></view
            ></template
          >
          <text v-if="!Object.keys(groups).length" class="workspace-empty"
            >没有匹配的亲友</text
          ></scroll-view
        >
        <view
          v-if="Object.keys(groups).length"
          class="index-rail"
          aria-label="亲友拼音索引"
          @touchmove.stop.prevent="slideIndex"
          ><button
            v-for="key in alphabet"
            :key="key"
            :disabled="!groups[key]"
            :class="{ located: activeInitial === key }"
            :aria-label="'定位 ' + key"
            @click="locate(key)"
          >
            {{ key }}
          </button></view
        >
      </template>

      <template v-else>
        <view v-if="currentBook" class="workspace-book-subtitle">
          <text>{{ bookLabel(currentBook) }}</text>
          <LedgerPicker
            v-if="writable && !currentBook.daily"
            label="礼单更多操作"
            :range="[currentBook.archived ? '重新开放' : '归档礼单']"
            :disabled="busy"
            @change="toggleArchive"
            ><text class="workspace-more">更多 ›</text></LedgerPicker
          >
        </view>
        <view class="workspace-totals"
          ><view v-if="currentContact" class="person-name"
            ><text>{{ currentContact.name }}</text
            ><text>{{ currentContact.distinction }}</text></view
          >
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

<style scoped src="./ledger.css">
.directory-search {
  background: #edf1ec;
  border-radius: 11px;
  padding: 10px 13px;
  font-size: 14px;
}
.gift-workspace .directory-list {
  flex: 1;
  min-height: 0;
  height: 0;
}
.people-group {
  margin-right: 24px;
}
.initial {
  display: block;
  font: 650 12px/24px system-ui;
  margin: 9px 0 2px 9px;
  color: #596b5f;
}
.person-link {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  min-height: 64px;
  padding: 8px 10px;
  background: #fff;
  text-align: left;
  border-bottom: 1px solid #e4eae5;
  color: #182623;
  font-size: 15px;
  line-height: 1.4;
  border-radius: 0;
}
.avatar {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 13px;
  background: #e1ece0;
  color: #45654c;
  font-size: 17px;
}
.person-copy {
  flex: 1;
  min-width: 0;
  overflow-wrap: anywhere;
  font-weight: 650;
}
.index-rail {
  position: fixed;
  right: max(2px, calc((100vw - 560px) / 2 + 2px));
  top: 50%;
  transform: translateY(-45%);
  width: 27px;
  height: min(59vh, 430px);
  display: grid;
  grid-template-rows: repeat(27, 1fr);
}
.index-rail button {
  background: transparent;
  color: #176a57;
  font: 700 11px/1 system-ui;
  margin: 0;
  padding: 0;
  min-height: 0;
}
.index-rail button.located {
  background: #176a57;
  color: #fff;
  border-radius: 12px;
}
.index-rail button[disabled] {
  color: #a6afa7;
  background: transparent;
}
.person-name {
  grid-column: 1/-1;
  font-weight: 700;
  margin-bottom: 8px;
}
</style>
<style scoped>
.overlay.gift-workspace-overlay {
  z-index: 2;
}
.gift-workspace {
  padding-top: calc(8px + env(safe-area-inset-top));
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
  gap: 4px;
  padding: 4px;
  background: #e8ede8;
  border-radius: 13px;
}
.workspace-tabs button {
  min-height: 37px;
  border-radius: 10px;
  color: var(--muted);
}
.workspace-tabs .current {
  background: #fff;
  color: #176a57;
  box-shadow: 0 1px 4px #39473716;
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
.workspace-book-subtitle > :last-child {
  width: auto;
}
.workspace-more {
  display: block;
  min-height: 44px;
  line-height: 44px;
  color: #176a57;
}
.workspace-totals {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  background: #124334;
  border-radius: 16px;
  padding: 19px 17px;
  color: white;
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
.directory-search {
  background: #edf1ec;
  border-radius: 11px;
  padding: 10px 13px;
  font-size: 14px;
}
.gift-workspace .directory-list {
  flex: 1;
  min-height: 0;
  height: 0;
}
.people-group {
  margin-right: 24px;
}
.initial {
  display: block;
  font: 650 12px/24px system-ui;
  margin: 9px 0 2px 9px;
  color: #596b5f;
}
.person-link {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  min-height: 64px;
  padding: 8px 10px;
  background: #fff;
  text-align: left;
  border-bottom: 1px solid #e4eae5;
  color: #182623;
  font-size: 15px;
  line-height: 1.4;
  border-radius: 0;
}
.avatar {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 13px;
  background: #e1ece0;
  color: #45654c;
  font-size: 17px;
}
.person-copy {
  flex: 1;
  min-width: 0;
  overflow-wrap: anywhere;
  font-weight: 650;
}
.index-rail {
  position: fixed;
  right: max(2px, calc((100vw - 560px) / 2 + 2px));
  top: 50%;
  transform: translateY(-45%);
  width: 27px;
  height: min(59vh, 430px);
  display: grid;
  grid-template-rows: repeat(27, 1fr);
}
.index-rail button {
  background: transparent;
  color: #176a57;
  font: 700 11px/1 system-ui;
  margin: 0;
  padding: 0;
  min-height: 0;
}
.index-rail button.located {
  background: #176a57;
  color: #fff;
  border-radius: 12px;
}
.index-rail button[disabled] {
  color: #a6afa7;
  background: transparent;
}
.person-name {
  grid-column: 1/-1;
  font-weight: 700;
  margin-bottom: 8px;
}
</style>
