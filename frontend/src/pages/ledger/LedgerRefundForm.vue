<script setup lang="ts">
import LedgerPicker from '../../components/LedgerPicker.vue';
import { ref } from 'vue';
import type { LedgerAccount } from '../../api/ledger-accounts';
import { recordLedgerRefund, type LedgerTransactionDetail } from '../../api/ledger-transactions';
import { ApiRequestError } from '../../api/request';
import LedgerAccountsSheet from './LedgerAccountsSheet.vue';
import { formatCents, ledgerToday, parseCents } from './transaction';

const props = defineProps<{
  ledgerId: string;
  detail: LedgerTransactionDetail;
  remainingCents: number;
}>();
const emit = defineEmits<{ cancel: []; saved: [] }>();
const amount = ref('');
const receivedOn = ref(ledgerToday());
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
  if (receivedOn.value < props.detail.transaction.bookedOn || receivedOn.value > ledgerToday()) {
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
      <view class="form-row">
        <label for="refund-amount">退款金额<text class="star">*</text></label>
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
      <view class="form-row">
        <text>到账日期<text class="star">*</text></text>
        <LedgerPicker
          label="选择到账日期"
          mode="date"
          :value="receivedOn"
          :start="detail.transaction.bookedOn"
          :end="ledgerToday()"
          @change="onDateChange"
        >
          <text class="picker-value">{{ receivedOn }}</text>
        </LedgerPicker>
      </view>
      <view class="form-row">
        <text>到账账户</text>
        <button
          class="text-button account-field"
          aria-label="选择到账账户"
          :disabled="saving"
          @click="selectingAccount = true"
        >
          <text class="picker-value">{{ selectedAccount?.name || '选择账户' }}</text>
        </button>
      </view>
    </view>
    <LedgerAccountsSheet
      v-if="selectingAccount"
      :ledger-id="ledgerId"
      :selected-id="selectedAccount?.id"
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
  flex: 1;
  min-width: 0;
  text-align: left;
}
.account-field {
  display: block;
  width: 100%;
  margin: 0;
  padding: 0;
  text-align: left;
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
