<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import {
  dailyGiftBook,
  listGiftBooks,
  listGiftContacts,
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
const contacts = ref<GiftContact[] | null>(null);
const chooser = ref(false);
const focused = ref(false);
const error = ref('');
const query = computed(() => String(props.detail.personName ?? ''));
const distinction = computed(() => String(props.detail.distinction ?? ''));
const matching = computed(() =>
  (contacts.value ?? []).filter((item) => item.name.includes(query.value.trim())).slice(0, 8),
);
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

async function load() {
  error.value = '';
  try {
    const [activityBooks, people] = await Promise.all([
      listGiftBooks(props.ledgerId),
      listGiftContacts(props.ledgerId),
    ]);
    books.value = activityBooks;
    contacts.value = people;
    if (props.detail.needsContactLookup) updateName(query.value);
  } catch {
    error.value = '礼单或亲友暂未加载。';
  }
}

function selectBook(book: GiftBook) {
  chooser.value = false;
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
  const exact = (contacts.value ?? []).filter((item) => item.name === clean);
  if (clean && exact.length === 1) {
    const item = exact[0];
    change({
      personName: name,
      distinction: item.distinction,
      contactId: item.id,
      needsContactLookup: false,
      ambiguous: false,
      createContact: false,
      sameName: false,
    });
    return;
  }
  change({
    personName: name,
    contactId: null,
    distinction: '',
    ambiguous: exact.length > 1,
    needsContactLookup: Boolean(clean) && contacts.value === null,
    createContact: Boolean(clean) && contacts.value !== null && exact.length === 0,
    sameName: false,
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
onMounted(load);
</script>

<template>
  <view class="gift-fields" aria-label="往来明细">
    <view v-if="quick" class="gift-field"
      ><text>所属礼单</text><text>{{ title }}</text></view
    >
    <button v-else class="gift-field gift-choice" :disabled="disabled" @click="chooser = !chooser">
      <text>所属礼单</text><text>{{ title }}</text
      ><text aria-hidden="true">›</text>
    </button>
    <view v-if="chooser" class="gift-options">
      <button v-for="book in eligible" :key="book.id" class="gift-option" @click="selectBook(book)">
        {{ book.name }}
      </button>
    </view>
    <view class="gift-field">
      <text>{{ personLabel }}<text v-if="quick" class="gift-required">*</text></text>
      <input
        :value="query"
        :aria-label="personLabel"
        :maxlength="60"
        :disabled="disabled"
        @focus="focused = true"
        @input="updateName($event.detail.value)"
      />
    </view>
    <view
      v-if="focused && query.trim() && matching.length && !detail.sameName"
      class="gift-options"
    >
      <button
        v-for="item in matching"
        :key="item.id"
        class="gift-option"
        @click="selectContact(item)"
      >
        {{ [item.name, item.distinction].filter(Boolean).join(' · ') }}
      </button>
      <button
        v-if="matching.some((item) => item.name === query.trim())"
        class="gift-option"
        @click="createSameName"
      >
        新增同名
      </button>
    </view>
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
    <view class="gift-field">
      <text>事由</text>
      <input
        :value="String(detail.reason ?? '')"
        aria-label="事由"
        :maxlength="60"
        :disabled="disabled"
        @input="change({ reason: $event.detail.value })"
      />
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
</style>
