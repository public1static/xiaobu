<script setup lang="ts">
import GiftReasonPicker from './GiftReasonPicker.vue';
import LedgerPicker from '../../components/LedgerPicker.vue';
import { useLedgerContactMatches } from './ledger-contact-matches';
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import {
  dailyGiftBook,
  listGiftBooks,
  type GiftBook,
  type GiftContact,
} from '../../api/ledger-gifts';
import type { TransactionExtensionDetail } from '../../api/transaction-extensions';
import type { TransactionDirection } from './transaction';

const props = defineProps<{
  ledgerId: string;
  detail: TransactionExtensionDetail;
  direction: TransactionDirection;
  disabled?: boolean;
  quick?: boolean;
}>();
const emit = defineEmits<{
  (event: 'change', detail: TransactionExtensionDetail): void;
  (event: 'date-default', value: string): void;
}>();

const books = ref<GiftBook[]>([]);
const {
  matches: matching,
  count: matchCount,
  pending: matchPending,
  failure: matchError,
  search: searchMatches,
  retry: retryMatches,
  more: moreMatches,
} = useLedgerContactMatches(() => props.ledgerId);

const focused = ref(false);
const error = ref('');
const query = computed(() => String(props.detail.personName ?? ''));
const distinction = computed(() => String(props.detail.distinction ?? ''));
const eligible = computed(() => [
  dailyGiftBook(props.direction),
  ...books.value.filter((item) => item.direction === props.direction && !item.archived),
]);
const title = computed(() => {
  if (!props.detail.bookId) return dailyGiftBook(props.direction).name;
  return (
    books.value.find((item) => item.id === props.detail.bookId)?.name ??
    String(props.detail.bookName ?? '活动礼单')
  );
});
const personLabel = computed(() => (props.direction === 'EXPENSE' ? '随礼对象' : '送礼人'));

function change(values: TransactionExtensionDetail) {
  emit('change', { ...props.detail, ...values });
}

// 切换账本或离开表单后丢弃未完成的亲友和礼单请求。
let loadGeneration = 0;
async function load() {
  const generation = ++loadGeneration;
  const ledger = props.ledgerId;
  error.value = '';
  try {
    const activityBooks = await listGiftBooks(ledger);
    if (generation !== loadGeneration || ledger !== props.ledgerId) return;
    books.value = activityBooks;
  } catch {
    if (generation === loadGeneration && ledger === props.ledgerId) error.value = '礼单暂未加载。';
  }
}

function selectBook(book: GiftBook) {
  if ((book.daily ? null : book.id) === (props.detail.bookId ?? null)) return;
  change({
    bookId: book.daily ? null : book.id,
    bookName: book.name,
    ...(book.defaultReason ? { reason: book.defaultReason } : {}),
  });
  if (book.defaultDate) emit('date-default', book.defaultDate);
}

function selectContact(item: GiftContact) {
  change({
    personName: item.name,
    distinction: item.distinction,
    contactId: item.id,
    needsContactLookup: false,
    ambiguous: false,
    createContact: false,
    sameName: false,
  });
  focused.value = false;
}

function updateName(name: string) {
  const clean = name.trim();
  change({
    personName: name,
    contactId: null,
    distinction: '',
    ambiguous: false,
    needsContactLookup: !!clean,
    createContact: false,
    sameName: false,
  });
  searchMatches(name, (result) => {
    if (query.value.trim() !== clean || props.detail.sameName || props.detail.contactId) return;
    const item = result.totalCount === 1 ? result.entries[0] : undefined;
    change({
      personName: name,
      contactId: item?.id ?? null,
      distinction: item?.distinction ?? '',
      ambiguous: result.totalCount > 1,
      needsContactLookup: false,
      createContact: result.totalCount === 0,
    });
  });
}

function createSameName() {
  change({
    contactId: null,
    ambiguous: false,
    createContact: true,
    distinction: '',
    sameName: true,
  });
}

watch(
  () => props.direction,
  () => {
    if (props.detail.bookId) {
      const previous = books.value.find((item) => item.id === props.detail.bookId);
      if (!previous || previous.direction !== props.direction)
        change({ bookId: null, bookName: '' });
    }
  },
);
watch(
  () => props.ledgerId,
  () => {
    books.value = [];
    focused.value = false;
    void load();
    if (props.detail.needsContactLookup) updateName(query.value);
    else searchMatches(query.value);
  },
  { immediate: true },
);
onBeforeUnmount(() => {
  loadGeneration++;
});
</script>

<template>
  <view class="gift-fields" aria-label="往来明细">
    <view v-if="quick" class="gift-field"
      ><text>所属礼单</text><text>{{ title }}</text></view
    >
    <LedgerPicker
      v-else
      label="选择所属礼单"
      :range="eligible"
      range-key="name"
      :value="eligible.findIndex((b) => b.id === (detail.bookId || dailyGiftBook(direction).id))"
      :disabled="disabled"
      @change="selectBook(eligible[Number($event.detail.value)])"
      ><view class="gift-field gift-choice"
        ><text>所属礼单</text><text>{{ title }}</text
        ><text>›</text></view
      ></LedgerPicker
    >
    <view class="gift-field contact-field">
      <text>{{ personLabel }}<text v-if="quick" class="gift-required">*</text></text>
      <view class="contact-input"
        ><input
          :value="query"
          placeholder="输入姓名"
          role="combobox"
          :aria-expanded="focused && matchCount > 1 && !detail.contactId && !detail.sameName"
          :aria-label="personLabel"
          :maxlength="60"
          :disabled="disabled"
          @focus="focused = true"
          @blur="focused = false"
          @input="
            focused = true;
            updateName($event.detail.value);
          "
        />
        <button
          v-if="matchCount > 0"
          :disabled="disabled"
          class="same"
          @click="detail.sameName ? updateName(query) : createSameName()"
        >
          {{ detail.sameName ? '取消新增' : '新增同名' }}
        </button>
        <button
          v-if="query"
          :disabled="disabled"
          aria-label="清空对象"
          @click="
            updateName('');
            focused = false;
          "
        >
          ⊗
        </button>
        <view
          v-if="focused && matchCount > 1 && !detail.contactId && !detail.sameName"
          class="contact-panel"
          role="listbox"
        >
          <button
            v-for="item in matching"
            :key="item.id"
            role="option"
            :aria-selected="item.id === detail.contactId"
            @mousedown.prevent
            @touchstart.stop.prevent="selectContact(item)"
            @click="selectContact(item)"
          >
            {{ item.name }}<text>{{ item.distinction || '无称呼' }}</text>
          </button>
          <button
            v-if="matching.length < matchCount"
            :disabled="matchPending"
            @mousedown.prevent
            @click="moreMatches"
          >
            加载更多
          </button>
        </view>
      </view>
    </view>
    <text v-if="detail.contactId && distinction" class="selected-distinction">{{
      distinction
    }}</text>
    <view v-if="detail.sameName" class="gift-field">
      <text>称呼或关系<text class="gift-required">*</text></text>
      <input
        :value="distinction"
        aria-label="称呼或关系"
        :maxlength="60"
        :disabled="disabled"
        @input="change({ distinction: $event.detail.value })"
      />
    </view>
    <GiftReasonPicker
      :ledger-id="ledgerId"
      :value="String(detail.reason ?? '')"
      :disabled="disabled"
      @select="change({ reason: $event })"
    />
    <view v-if="matchError" class="gift-options gift-required">
      <text>联系人查询失败。</text><button class="gift-option" @click="retryMatches">重试</button>
    </view>
    <view v-if="error" class="gift-options gift-required">
      <text>{{ error }}</text><button class="gift-option" @click="load">重试</button>
    </view>
  </view>
</template>

<style scoped>
.gift-fields {
  background: #fff;
  border: 1px solid var(--line, #e4eae5);
  border-radius: 14px;
}
.gift-field {
  min-height: 46px;
  box-sizing: border-box;
  display: grid;
  grid-template-columns: 84px minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  padding: 8px 13px;
  font-size: 14px;
}
.gift-field:not(:first-child) {
  border-top: 1px solid var(--line, #e4eae5);
}
.gift-field input {
  width: 100%;
  font-size: 14px;
}
.gift-choice {
  width: 100%;
  grid-template-columns: 84px minmax(0, 1fr) 16px;
  text-align: left;
  background: transparent;
}
.gift-choice::after,
.gift-option::after {
  border: 0;
}
.gift-options {
  padding: 2px 13px 6px 107px;
}
.gift-option {
  margin: 0;
  padding: 4px 0;
  text-align: left;
  background: transparent;
  color: #176a57;
  font-size: 14px;
  line-height: 1.6;
}
.gift-required {
  color: #a23832;
}
.selected-distinction {
  display: block;
  margin: -4px 13px 8px 107px;
  color: #536660;
  font-size: 12px;
}
.contact-field {
  padding-top: 0;
  padding-bottom: 0;
}
.contact-input {
  position: relative;
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}
.contact-input input {
  flex: 1;
  min-width: 0;
}
.contact-input button {
  flex: none;
  margin: 0;
  padding: 0 4px;
  font-size: 13px;
  line-height: 32px;
  color: #176a57;
  background: transparent;
}
.contact-input button::after {
  border: 0;
}
.contact-panel {
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  z-index: 2;
  background: white;
  border: 1px solid #dce6df;
  border-radius: 10px;
  max-height: 170px;
  overflow: auto;
  box-shadow: 0 4px 14px #18262320;
}
.contact-panel button {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 4px 10px;
  text-align: left;
  color: #182623;
}
.contact-panel text {
  color: #536660;
  font-size: 12px;
}
</style>
