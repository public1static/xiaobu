<script setup lang="ts">
import { ref } from 'vue';
import type { LedgerAccount } from '../../api/ledger-accounts';
import { recordLedgerRefund, type LedgerTransactionDetail } from '../../api/ledger-transactions';
import { ApiRequestError } from '../../api/request';
import LedgerAccountsSheet from './LedgerAccountsSheet.vue';
import { formatCents, refundToday, parseCents } from './transaction';

const props = defineProps<{
  ledgerId: string;
  detail: LedgerTransactionDetail;
  remainingCents: number;
}>();
const emit = defineEmits<{ cancel: []; saved: [] }>();
const amount = ref('');
const receivedOn = ref(refundToday());
const selectedAccount = ref<LedgerAccount | null>(null);
const selectingAccount = ref(false);
const saving = ref(false);
const error = ref('');
const validationError = ref('');

function onDateChange(event: { detail: { value: string } }) {
  receivedOn.value = event.detail.value;
  validationError.value = '';
}

function selectAccount(account: LedgerAccount | null) {
  selectedAccount.value = account;
  selectingAccount.value = false;
}

async function save() {
  if (saving.value) return;
  const amountCents = parseCents(amount.value);
  if (!amountCents || amountCents > props.remainingCents) {
    validationError.value = '请输入不超过可退金额的退款金额。';
    return;
  }
  if (receivedOn.value < props.detail.transaction.bookedOn || receivedOn.value > refundToday()) {
    validationError.value = '到账日期须在原付款日期至今天之间。';
    return;
  }
  saving.value = true;
  validationError.value = '';
  error.value = '';
  try {
    await recordLedgerRefund(props.ledgerId, props.detail.transaction.id, {
      version: props.detail.version,
      amountCents,
      receivedOn: receivedOn.value,
      accountId: selectedAccount.value?.id ?? null,
    });
    emit('saved');
  } catch (failure) {
    error.value =
      failure instanceof ApiRequestError && [400, 403, 409].includes(failure.status)
        ? '流水或可退金额可能已变更，请关闭并重新查看。'
        : '未确认退款是否保存，请先重新查看流水。';
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <view class="refund-form">
    <view class="refund-header"
      ><text>可退金额</text><text>¥{{ formatCents(remainingCents) }}</text></view
    >
    <view class="meta">
      <view class="meta-row">
        <label for="refund-amount">退款金额</label>
        <view class="refund-input">
          <text>¥</text>
          <input
            id="refund-amount"
            v-model="amount"
            type="digit"
            placeholder="0.00"
            @input="validationError = ''"
          />
        </view>
      </view>
      <view class="meta-row">
        <text>到账日期</text>
        <picker
          mode="date"
          :value="receivedOn"
          :start="detail.transaction.bookedOn"
          :end="refundToday()"
          @change="onDateChange"
        >
          <text>{{ receivedOn }} ›</text>
        </picker>
      </view>
      <view class="meta-row">
        <text>到账账户</text>
        <button
          class="text-button account-field"
          aria-label="选择到账账户"
          :disabled="saving"
          @click="selectingAccount = true"
        >
          {{ selectedAccount?.name || '未选择' }} ›
        </button>
      </view>
    </view>
    <LedgerAccountsSheet
      v-if="selectingAccount"
      :ledger-id="ledgerId"
      :allow-create="false"
      selectable
      @close="selectingAccount = false"
      @select="selectAccount"
      :allow-transfer="false"
    />
    <text v-if="validationError || error" class="error" role="alert">{{
      validationError || error
    }}</text>
    <view class="refund-actions">
      <button class="primary secondary" :disabled="saving" @click="emit('cancel')">取消</button>
      <button class="primary" :disabled="saving" @click="save">
        {{ saving ? '保存中…' : '保存退款' }}
      </button>
    </view>
  </view>
</template>

<style scoped src="./ledger.css"></style>
<style scoped>
.refund-form {
  display: grid;
  gap: 12px;
}
.refund-header,
.refund-input {
  display: flex;
  align-items: center;
  gap: 4px;
}
.refund-header {
  justify-content: space-between;
  font-weight: 650;
}
.refund-header text:last-child {
  color: var(--muted);
  font-size: 13px;
  font-weight: 400;
}
.refund-input input {
  width: 100px;
  text-align: right;
}
.refund-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}
.refund-actions button {
  width: 100%;
  margin-top: 0;
}
</style>
