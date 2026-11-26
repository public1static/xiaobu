<script setup lang="ts">
import { onMounted, ref } from 'vue';
import {
  deleteLedgerTransaction,
  getLedgerTransaction,
  type LedgerTransactionDetail,
} from '../../api/ledger-transactions';
import { ApiRequestError } from '../../api/request';
import { categoryLabel, formatCents } from './transaction';

const props = defineProps<{
  ledgerId: string;
  transactionId: string;
  categoryNames?: Record<string, string>;
}>();
const emit = defineEmits<{
  (event: 'close'): void;
  (event: 'edit', detail: LedgerTransactionDetail): void;
  (event: 'deleted'): void;
}>();
const detail = ref<LedgerTransactionDetail | null>(null);
const loading = ref(false);
const error = ref('');
const confirming = ref(false);
const deleting = ref(false);

async function load() {
  loading.value = true;
  error.value = '';
  try {
    detail.value = await getLedgerTransaction(props.ledgerId, props.transactionId);
  } catch (failure) {
    error.value =
      failure instanceof ApiRequestError && [403, 404].includes(failure.status)
        ? '这笔流水已不可查看。'
        : '流水详情加载失败。';
  } finally {
    loading.value = false;
  }
}

async function completeDeletion() {
  if (!detail.value || deleting.value) return;
  deleting.value = true;
  error.value = '';
  try {
    await deleteLedgerTransaction(props.ledgerId, props.transactionId, detail.value.version);
    emit('deleted');
  } catch (failure) {
    if (failure instanceof ApiRequestError && failure.status === 409) {
      error.value = '流水已变更，请重新查看后再删除。';
      detail.value = null;
    } else if (failure instanceof ApiRequestError && failure.status === 403) {
      error.value = '你已没有删除这笔流水的权限。';
      detail.value = { ...detail.value, canModify: false };
    } else {
      error.value = '删除未确认，请重新查看这笔流水。';
    }
  } finally {
    deleting.value = false;
    confirming.value = false;
  }
}

onMounted(load);
</script>

<template>
  <view class="overlay">
    <view class="sheet login-sheet" role="dialog" aria-label="流水详情" aria-modal="true">
      <view class="sheet-head">
        <text>流水详情</text>
        <button class="close" aria-label="关闭详情" :disabled="deleting" @click="emit('close')">
          ×
        </button>
      </view>
      <text v-if="loading" class="preview-label" role="status">正在加载详情</text>
      <template v-else-if="detail">
        <view class="detail-caption"
          ><text>{{
            categoryNames?.[detail.transaction.categoryCode] ||
            categoryLabel(detail.transaction.categoryCode, detail.transaction.direction)
          }}</text></view
        >
        <text class="detail-amount" :class="{ income: detail.transaction.direction === 'INCOME' }">
          {{ detail.transaction.direction === 'INCOME' ? '+' : '−' }}¥{{
            formatCents(detail.transaction.amountCents)
          }}
        </text>
        <view class="meta">
          <view class="meta-row"
            ><text>记账日期</text><text>{{ detail.transaction.bookedOn }}</text></view
          >
          <view class="meta-row"
            ><text>记账人</text><text>{{ detail.authorName }}</text></view
          >
          <view class="meta-row"
            ><text>账户</text><text>{{ detail.accountName || '未选择' }}</text></view
          >
          <view v-if="detail.transaction.note" class="meta-row detail-note"
            ><text>备注</text><text>{{ detail.transaction.note || '无' }}</text></view
          >
        </view>
        <view v-if="detail.canModify && !confirming" class="detail-actions">
          <button class="primary secondary" @click="confirming = true">删除这笔</button>
          <button class="primary" @click="emit('edit', detail)">编辑</button>
        </view>
        <view v-if="confirming" class="detail-confirm">
          <text class="preview-label">删除后可在「最近删除」恢复。</text>
          <button class="primary" :disabled="deleting" @click="completeDeletion">确认删除</button>
          <button class="primary secondary" :disabled="deleting" @click="confirming = false">
            取消
          </button>
        </view>
      </template>
      <text v-if="error" class="error" role="alert">{{ error }}</text>
      <button v-if="error && !detail" class="text-button" @click="load">重试</button>
    </view>
  </view>
</template>

<style scoped src="./ledger.css"></style>
<style scoped>
.detail-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}
.detail-confirm {
  display: grid;
  gap: 8px;
}
.detail-caption {
  color: var(--muted);
  font-size: 14px;
}
.detail-amount {
  font-size: 32px;
  font-weight: 750;
}
.detail-amount.income {
  color: var(--green);
}
.detail-note text:last-child {
  min-width: 0;
  overflow-wrap: anywhere;
  text-align: right;
}
</style>
