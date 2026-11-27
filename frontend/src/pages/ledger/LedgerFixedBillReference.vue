<script setup lang="ts">
import { onMounted, ref } from 'vue';
import {
  findFixedBillPaymentReference,
  unlinkFixedBillPayment,
  type FixedBillPaymentReference,
} from '../../api/fixed-bills';
import { ApiRequestError } from '../../api/request';
// #ifdef H5
import { showConfirmDialog } from 'vant';
import 'vant/es/dialog/style';
// #endif

const props = defineProps<{
  ledgerId: string;
  transactionId: string;
  writable: boolean;
  disabled: boolean;
}>();
const emit = defineEmits<{ unlinked: []; busy: [value: boolean] }>();
const reference = ref<FixedBillPaymentReference>();
const loading = ref(false);
const saving = ref(false);
const loadError = ref(false);
const error = ref('');
const title = '解除本期关联？';
const message = '该期会回到待付，原支出流水保留。';

async function load() {
  loading.value = true;
  loadError.value = false;
  try {
    reference.value = await findFixedBillPaymentReference(props.ledgerId, props.transactionId);
  } catch {
    loadError.value = true;
  } finally {
    loading.value = false;
  }
}

async function confirmUnlink() {
  // #ifdef H5
  try {
    await showConfirmDialog({
      title,
      message,
      confirmButtonText: '解除关联',
      confirmButtonColor: '#176a57',
    });
    return true;
  } catch {
    return false;
  }
  // #endif
  // #ifndef H5
  return new Promise<boolean>((resolve) =>
    uni.showModal({
      title,
      content: message,
      confirmText: '解除关联',
      confirmColor: '#176a57',
      success: (result) => resolve(result.confirm),
      fail: () => resolve(false),
    }),
  );
  // #endif
}

async function unlink() {
  if (!reference.value || saving.value || props.disabled || !props.writable) return;
  const { paymentId } = reference.value;
  saving.value = true;
  emit('busy', true);
  error.value = '';
  try {
    if (!(await confirmUnlink())) return;
    await unlinkFixedBillPayment(props.ledgerId, props.transactionId, paymentId);
    reference.value = undefined;
    uni.showToast({ title: '已解除关联', icon: 'none' });
    emit('unlinked');
  } catch (failure) {
    if (failure instanceof ApiRequestError && failure.status === 409) {
      await load();
      error.value = '关联已变更，请核对后再操作。';
    } else if (failure instanceof ApiRequestError && failure.status === 403) {
      error.value = '你当前没有记账权限，未解除关联。';
    } else {
      error.value = '未确认是否解除，请重新打开流水核对。';
    }
  } finally {
    saving.value = false;
    emit('busy', false);
  }
}

onMounted(load);
</script>

<template>
  <view v-if="reference || loadError || error" class="bill-reference">
    <view v-if="reference" class="reference-row">
      <text>固定账单</text>
      <text class="reference-value"
        >{{ reference.billName }} · {{ reference.periodStart.slice(0, 7) }}</text
      >
    </view>
    <button
      v-if="reference && writable"
      class="reference-row reference-action"
      :disabled="disabled || saving || loading"
      @click="unlink"
    >
      <text>解除本期关联</text><text>›</text>
    </button>
    <button
      v-if="loadError"
      class="reference-row reference-action"
      :disabled="loading"
      @click="load"
    >
      账期关联加载失败，重试
    </button>
    <text v-if="error" class="reference-row reference-error" role="status">{{ error }}</text>
  </view>
</template>

<style scoped>
.reference-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  box-sizing: border-box;
  width: 100%;
  min-height: 44px;
  padding: 8px 12px;
  border-bottom: 1px solid #edf1ee;
  font-size: 14px;
}
.reference-value {
  text-align: right;
  color: #607267;
  overflow-wrap: anywhere;
}
.reference-action {
  margin: 0;
  padding: 8px 12px;
  border: 0;
  border-bottom: 1px solid #edf1ee;
  border-radius: 0;
  text-align: left;
  background: transparent;
  color: #176a57;
  line-height: 1.4;
}
.reference-action::after {
  border: 0;
}
.reference-error {
  color: #a83636;
}
</style>
