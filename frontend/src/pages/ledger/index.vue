<script setup lang="ts">
import { computed, ref } from 'vue';
import { listExpenses, saveExpense, type Expense } from '../../api/expenses';
import {
  CATEGORIES,
  categoryLabel,
  formatCents,
  localDay,
  parseCents,
  type CategoryCode,
} from './expense';

const today = localDay();
const month = ref(today.slice(0, 7));
const monthText = computed(() => {
  const [year, value] = month.value.split('-');
  return `${year}年${Number(value)}月`;
});
const items = ref<Expense[]>([]);
const loading = ref(true);
const listError = ref('');
const savedMessage = ref('');
const open = ref(false);
const saving = ref(false);
const amount = ref('');
const date = ref(today);
const category = ref<CategoryCode | ''>('');
const note = ref('');
const fieldError = ref({ amount: '', category: '' });
const saveError = ref('');
let generation = 0;

const total = computed(() =>
  items.value.reduce((value, expense) => value + expense.amountCents, 0),
);
const groups = computed(() => {
  const dates = new Map<string, Expense[]>();
  for (const expense of items.value) {
    const list = dates.get(expense.bookedOn) ?? [];
    list.push(expense);
    dates.set(expense.bookedOn, list);
  }
  return [...dates].map(([day, expenses]) => ({
    day,
    expenses,
    total: expenses.reduce((value, expense) => value + expense.amountCents, 0),
  }));
});

async function refresh() {
  const request = ++generation;
  const selectedMonth = month.value;
  loading.value = true;
  listError.value = '';
  try {
    const rows = await listExpenses(selectedMonth);
    if (request === generation) items.value = rows;
  } catch {
    if (request === generation) listError.value = '流水加载失败，点此重试';
  } finally {
    if (request === generation) loading.value = false;
  }
}

function newExpense() {
  date.value = today;
  amount.value = '';
  category.value = '';
  note.value = '';
  fieldError.value = { amount: '', category: '' };
  saveError.value = '';
  savedMessage.value = '';
  open.value = true;
}

function closeExpense() {
  if (!saving.value) open.value = false;
}

async function submit() {
  if (saving.value) return;
  const cents = parseCents(amount.value);
  const selected = category.value;
  fieldError.value = {
    amount: cents === null ? '请输入大于 0 的金额，最多两位小数' : '',
    category: selected ? '' : '请选择分类',
  };
  if (cents === null || !selected) return;
  const payload = {
    amountCents: cents,
    categoryCode: selected,
    bookedOn: date.value,
    note: note.value.trim(),
  };
  // The current preview has no server-side replay contract.
  // #ifndef H5
  saveError.value = '此里程碑仅支持 H5 本地联调';
  return;
  // #endif
  saving.value = true;
  saveError.value = '';
  try {
    const stored = await saveExpense(payload);
    const nextMonth = stored.bookedOn.slice(0, 7);
    const current = nextMonth === month.value ? items.value : [];
    month.value = nextMonth;
    items.value = [stored, ...current.filter((row) => row.id !== stored.id)].sort(
      (a, b) => b.bookedOn.localeCompare(a.bookedOn) || b.id.localeCompare(a.id),
    );
    open.value = false;
    savedMessage.value = '已记一笔';
    await refresh();
  } catch (error) {
    saveError.value =
      error instanceof Error && 'status' in error && error.status === 400
        ? error.message
        : '保存结果未确认。请先返回刷新流水，避免重复记账。';
  } finally {
    saving.value = false;
  }
}

const showDay = (day: string) =>
  day === today ? '今天' : `${Number(day.slice(5, 7))}月${Number(day.slice(8))}日`;

void refresh();
</script>

<template>
  <view class="shell">
    <view class="head">
      <view class="eyebrow"><text class="brand-dot" /> 我的账本</view>
      <text class="brand">小簿</text>
    </view>

    <view class="main">
      <view class="summary">
        <view class="summary-line">
          <text class="summary-label">收支结余</text>
          <text class="period">{{ monthText }}</text>
        </view>
        <view class="balance"
          ><text class="yuan">{{ total ? '−¥' : '¥' }}</text
          >{{ formatCents(total) }}</view
        >
        <view class="metrics">
          <view><text>收入</text><text>¥0.00</text></view>
          <view
            ><text>支出</text><text>¥{{ formatCents(total) }}</text></view
          >
        </view>
      </view>
      <view class="section-title"><text>流水</text><text>个人账</text></view>
      <text v-if="savedMessage" class="sr-only" role="status">{{ savedMessage }}</text>
      <button v-if="listError" class="empty retry" @click="refresh">{{ listError }}</button>
      <view v-else-if="loading && !items.length" class="empty">正在加载流水</view>
      <view v-else-if="!items.length" class="empty">这段时间还没有流水</view>
      <view v-for="group in groups" :key="group.day" class="day">
        <view class="day-head"
          ><text>{{ showDay(group.day) }}</text
          ><text>支出 ¥{{ formatCents(group.total) }}</text></view
        >
        <view class="rows">
          <view v-for="row in group.expenses" :key="row.id" class="row">
            <view class="row-icon" aria-hidden="true"
              ><image :src="`/static/ledger/${row.categoryCode}.svg`"
            /></view>
            <view class="row-copy"
              ><text>{{ categoryLabel(row.categoryCode) }}</text
              ><text v-if="row.note">{{ row.note }}</text></view
            >
            <text class="row-amount">−¥{{ formatCents(row.amountCents) }}</text>
          </view>
        </view>
      </view>
    </view>

    <view class="dock"><button class="primary" @click="newExpense">＋ 记一笔</button></view>

    <view v-if="open" class="overlay">
      <view class="sheet" role="dialog" aria-label="记一笔支出" aria-modal="true">
        <view class="sheet-head"
          ><text>记一笔</text
          ><button class="close" aria-label="关闭记账" :disabled="saving" @click="closeExpense">
            ×
          </button></view
        >
        <view class="kind">支出</view>
        <view class="amount-field">
          <text class="field-label">支出金额 <text class="star">*</text></text>
          <view class="amount-line"
            ><text>¥</text
            ><input
              v-model="amount"
              type="digit"
              :maxlength="11"
              placeholder="0.00"
              @input="fieldError.amount = ''"
              aria-label="支出金额"
          /></view>
          <text v-if="fieldError.amount" class="error" role="alert">{{ fieldError.amount }}</text>
        </view>
        <view class="category-head">分类 <text class="star">*</text></view>
        <scroll-view scroll-x class="category-rail">
          <view class="category-inner">
            <button
              v-for="item in CATEGORIES"
              :key="item[0]"
              class="category"
              :class="{ selected: category === item[0] }"
              :aria-pressed="category === item[0]"
              @click="
                category = item[0];
                fieldError.category = '';
              "
            >
              <image :src="`/static/ledger/${item[0]}.svg`" aria-hidden="true" /><text>{{
                item[1]
              }}</text>
            </button>
          </view>
        </scroll-view>
        <text v-if="fieldError.category" class="error" role="alert">{{ fieldError.category }}</text>
        <view class="meta">
          <picker
            mode="date"
            :value="date"
            :end="today"
            start="1900-01-01"
            @change="date = $event.detail.value"
          >
            <view class="meta-row"
              ><text>日期 <text class="star">*</text></text
              ><text>{{ date === today ? '今天 · ' : '' }}{{ date }} ›</text></view
            >
          </picker>
          <view class="meta-row"><text>账本</text><text>个人账</text></view>
          <view class="meta-row"
            ><text>备注</text
            ><input v-model="note" :maxlength="100" placeholder="选填" aria-label="备注"
          /></view>
        </view>
        <text v-if="saveError" class="error" role="alert">{{ saveError }}</text>
        <button class="primary save" :disabled="saving" @click="submit">
          {{ saving ? '保存中…' : '保存账目' }}
        </button>
      </view>
    </view>
  </view>
</template>

<style scoped src="./ledger.css"></style>
