<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import LedgerPicker from '../../components/LedgerPicker.vue';
import { listLedgerAccounts, type LedgerAccount } from '../../api/ledger-accounts';
import { ApiRequestError } from '../../api/request';
import {
  editRentalDeposit,
  listRentalDeposits,
  recordRentalDeposit,
  type RentalDeposit,
  type RentalDepositDraft,
} from '../../api/rentals';
import { formatCents, formatCentsForInput, ledgerToday, parseCents } from './transaction';

const props = defineProps<{
  ledgerId: string;
  contractId: string;
  homeName: string;
  agreedCents: number;
  writable: boolean;
  form: boolean;
}>();
const emit = defineEmits<{ open: [title: string]; saved: []; busy: [value: boolean] }>();
const today = ledgerToday();
const rows = ref<RentalDeposit[]>([]);
const held = ref<number>();
const page = ref(0);
const total = ref(0);
const loadBusy = ref(false);
const loadError = ref(false);
const accounts = ref<LedgerAccount[]>([]);
const accountError = ref(false);
const accountBusy = ref(false);
const editing = ref<RentalDeposit>();
const kind = ref<RentalDeposit['kind']>('PAY');
const amount = ref('');
const bookedOn = ref(today);
const note = ref('');
const accountId = ref<string | null>(null);
const saving = ref(false);
const error = ref<{ field: string; text: string }>();
let pageSequence = 0;

const selectedAccounts = computed(() => {
  const choices: { id: string | null; name: string }[] = [
    { id: null, name: '不记账户' },
    ...accounts.value.filter((row) => row.active),
  ];
  if (editing.value?.accountId && !choices.some((row) => row.id === editing.value?.accountId)) {
    choices.push({ id: editing.value.accountId, name: editing.value.accountName || '原账户' });
  }
  return choices;
});
const accountIndex = computed(() =>
  Math.max(
    0,
    selectedAccounts.value.findIndex((row) => row.id === accountId.value),
  ),
);
const name = (kind: RentalDeposit['kind']) => (kind === 'PAY' ? '交付押金' : '退回押金');

async function load(next = 0) {
  const sequence = ++pageSequence;
  loadBusy.value = true;
  loadError.value = false;
  if (!next) rows.value = [];
  try {
    const result = await listRentalDeposits(props.ledgerId, props.contractId, next);
    if (sequence !== pageSequence) return;
    if (
      next &&
      (total.value !== result.totalCount ||
        held.value !== result.heldCents ||
        result.entries.some((item) => rows.value.some((row) => row.id === item.id)))
    ) {
      await load();
      return;
    }
    held.value = result.heldCents;
    rows.value = next ? [...rows.value, ...result.entries] : result.entries;
    total.value = result.totalCount;
    page.value = result.page;
  } catch {
    if (sequence === pageSequence) loadError.value = true;
  } finally {
    if (sequence === pageSequence) loadBusy.value = false;
  }
}

async function loadAccounts() {
  accountBusy.value = true;
  accountError.value = false;
  try {
    accounts.value = await listLedgerAccounts(props.ledgerId);
  } catch {
    accountError.value = true;
  } finally {
    accountBusy.value = false;
  }
}

watch(
  () => [props.ledgerId, props.contractId],
  () => {
    held.value = undefined;
    void load();
  },
  { immediate: true },
);

function open(which: RentalDeposit['kind'], row?: RentalDeposit) {
  if (!props.writable || (row && !row.canModify)) return;
  editing.value = row;
  kind.value = which;
  const suggested =
    row?.amountCents ??
    (held.value == null
      ? 0
      : which === 'REFUND'
        ? held.value
        : Math.max(0, props.agreedCents - held.value));
  amount.value = suggested > 0 ? formatCentsForInput(suggested) : '';
  bookedOn.value = row?.bookedOn || today;
  accountId.value = row?.accountId ?? null;
  note.value = row?.note ?? '';
  error.value = undefined;
  emit('open', row ? '编辑' + name(which) : which === 'PAY' ? '登记交押金' : '登记退押金');
  void loadAccounts();
}

async function save() {
  if (!props.writable || saving.value) return;
  const cents = parseCents(amount.value);
  error.value = undefined;
  if (cents == null) {
    error.value = { field: 'amount', text: '请输入大于零的金额，最多两位小数。' };
    return;
  }
  if (bookedOn.value > today) {
    error.value = { field: 'date', text: '日期不能晚于今天。' };
    return;
  }
  const draft: RentalDepositDraft = {
    kind: kind.value,
    amountCents: cents,
    bookedOn: bookedOn.value,
    accountId: accountId.value,
    note: note.value,
  };
  saving.value = true;
  emit('busy', true);
  try {
    if (editing.value) {
      const { kind: _, ...changes } = draft;
      await editRentalDeposit(props.ledgerId, props.contractId, editing.value, changes);
    } else {
      await recordRentalDeposit(props.ledgerId, props.contractId, draft);
    }
    void load();
    emit('saved');
  } catch (failure) {
    const code = failure instanceof ApiRequestError ? failure.code : '';
    error.value =
      code === 'XIAOBU_RENTAL_DEPOSIT_EXCEEDS_HELD'
        ? { field: 'amount', text: '退回金额超过当日已交押金，请核对金额和日期。' }
        : code === 'XIAOBU_RENTAL_DEPOSIT_STALE'
          ? { field: '', text: '押金记录已变更，请返回重新打开。' }
          : code === 'XIAOBU_ACCOUNT_UNAVAILABLE'
            ? { field: 'account', text: '这个账户不可用，请重新选择。' }
            : { field: '', text: '未确认保存，请返回押金记录核对。' };
  } finally {
    saving.value = false;
    emit('busy', false);
  }
}
</script>

<template>
  <view v-if="!form" class="deposit-section">
    <view class="deposit-heading">
      <text>押金</text><text class="deposit-muted">约定 ¥{{ formatCents(agreedCents) }}</text>
    </view>
    <view class="deposit-card">
      <view class="deposit-row deposit-held">
        <text>待退押金</text><text>{{ held == null ? '—' : '¥' + formatCents(held) }}</text>
      </view>
      <view v-if="writable" class="deposit-actions">
        <button :disabled="loadBusy || loadError" @click="open('PAY')">登记交付</button>
        <button :disabled="loadBusy || loadError || !held" @click="open('REFUND')">登记退回</button>
      </view>
      <button
        v-for="row in rows"
        :key="row.id"
        class="deposit-row deposit-history"
        :aria-disabled="!writable || !row.canModify ? 'true' : undefined"
        @click="open(row.kind, row)"
      >
        <view class="deposit-item">
          <text>{{ name(row.kind) }}</text>
          <text class="deposit-muted">{{
            [row.bookedOn, row.accountName, row.note].filter(Boolean).join(' · ')
          }}</text>
        </view>
        <text class="deposit-amount">¥{{ formatCents(row.amountCents) }}</text>
        <text v-if="writable && row.canModify" class="deposit-chevron">›</text>
      </button>
      <text v-if="loadBusy" class="deposit-message">加载中…</text>
      <button v-else-if="loadError" class="deposit-more" @click="load(rows.length ? page + 1 : 0)">
        加载失败，点击重试
      </button>
      <button v-else-if="rows.length < total" class="deposit-more" @click="load(page + 1)">
        加载更多
      </button>
    </view>
  </view>
  <view v-else>
    <view class="deposit-card">
      <view class="deposit-field"
        ><text>房屋</text><text>{{ homeName }}</text></view
      >
      <view class="deposit-field">
        <text>金额（元）<text class="deposit-star">*</text></text>
        <input
          v-model="amount"
          aria-label="押金金额"
          type="digit"
          maxlength="11"
          placeholder="0.00"
          @input="error = undefined"
        />
      </view>
      <text v-if="error?.field === 'amount'" class="deposit-error" role="alert">{{
        error.text
      }}</text>
      <LedgerPicker
        mode="date"
        label="押金日期"
        :value="bookedOn"
        :end="today"
        :disabled="saving"
        @change="
          bookedOn = $event.detail.value;
          error = undefined;
        "
      >
        <view class="deposit-field"
          ><text>日期<text class="deposit-star">*</text></text
          ><text>{{ bookedOn }}</text
          ><text class="deposit-chevron">›</text></view
        >
      </LedgerPicker>
      <text v-if="error?.field === 'date'" class="deposit-error" role="alert">{{
        error.text
      }}</text>
      <LedgerPicker
        label="押金账户"
        :disabled="saving || accountBusy || accountError"
        :range="selectedAccounts"
        range-key="name"
        :value="accountIndex"
        @change="
          accountId = selectedAccounts[Number($event.detail.value)]?.id ?? null;
          error = undefined;
        "
      >
        <view class="deposit-field"
          ><text>账户</text
          ><text :class="{ 'deposit-muted': !accountId }">{{
            accountBusy ? '加载账户…' : selectedAccounts[accountIndex]?.name
          }}</text
          ><text class="deposit-chevron">›</text></view
        >
      </LedgerPicker>
      <button v-if="accountError" class="deposit-more" @click="loadAccounts">
        账户加载失败，点击重试
      </button>
      <text v-if="error?.field === 'account'" class="deposit-error" role="alert">{{
        error.text
      }}</text>
      <view class="deposit-field"
        ><text>备注</text
        ><input v-model="note" aria-label="押金备注" maxlength="100" placeholder="选填"
      /></view>
    </view>
    <text v-if="error?.field === ''" class="deposit-error" role="alert">{{ error.text }}</text>
    <button class="deposit-save" :disabled="saving" @click="save">
      {{ saving ? '保存中…' : '保存' }}
    </button>
  </view>
</template>

<style scoped>
.deposit-section {
  margin-top: 16px;
}
.deposit-heading,
.deposit-row,
.deposit-field,
.deposit-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.deposit-heading,
.deposit-row {
  justify-content: space-between;
}
.deposit-heading {
  margin: 0 2px 8px;
  font-weight: 600;
  font-size: 16px;
}
.deposit-muted {
  color: #61736b;
  font-size: 13px;
  font-weight: 400;
}
.deposit-card {
  background: white;
  border-radius: 12px;
  overflow: hidden;
}
.deposit-card button {
  margin: 0;
  border: 0;
  background: transparent;
  color: inherit;
  font: inherit;
  line-height: 1.4;
}
.deposit-card button::after,
.deposit-save::after {
  border: 0;
}
.deposit-row,
.deposit-field,
.deposit-card .deposit-history {
  box-sizing: border-box;
  width: 100%;
  min-height: 48px;
  padding: 10px 13px;
}
.deposit-field,
.deposit-history {
  border-bottom: 1px solid #edf1ef;
}
.deposit-card .deposit-history {
  border-radius: 0;
  border-top: 1px solid #edf1ef;
  text-align: left;
}
.deposit-field:last-child {
  border-bottom: 0;
}
.deposit-card :deep(.ledger-picker-trigger),
.deposit-card picker {
  border-bottom: 1px solid #edf1ef;
  box-sizing: border-box;
}
.deposit-field > :first-child {
  flex: 0 0 84px;
}
.deposit-field > :nth-child(2),
.deposit-item {
  flex: 1;
  min-width: 0;
  overflow-wrap: anywhere;
}
.deposit-item .deposit-muted {
  display: block;
  margin-top: 3px;
}
.deposit-amount {
  white-space: nowrap;
}
.deposit-chevron {
  color: #61736b;
  font-size: 17px;
}
.deposit-star,
.deposit-error {
  color: #ad392d;
}
.deposit-star {
  margin-left: 2px;
}
.deposit-message,
.deposit-error {
  display: block;
  padding: 8px 13px;
  font-size: 13px;
}
.deposit-message {
  text-align: center;
  color: #61736b;
}
.deposit-actions {
  padding: 0 12px 12px;
}
.deposit-actions button {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
  border: 1px solid #dce8e1;
  border-radius: 20px;
  min-height: 43px;
  color: #176a57;
}
.deposit-actions button[disabled],
.deposit-save[disabled] {
  opacity: 0.5;
}
.deposit-card .deposit-more {
  color: #176a57;
  min-height: 42px;
  width: 100%;
}
.deposit-save {
  display: flex;
  justify-content: center;
  align-items: center;
  border: 0;
  border-radius: 24px;
  min-height: 44px;
  margin-top: 14px;
  width: 100%;
  font: inherit;
  color: white;
  background: #176a57;
}
</style>
