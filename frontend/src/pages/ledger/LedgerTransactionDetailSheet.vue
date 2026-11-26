<script setup lang="ts">
import LedgerExtensionDetail from './LedgerExtensionDetail.vue';
import { computed, onMounted, ref } from 'vue';
import {
  deleteLedgerTransaction,
  getLedgerTransaction,
  type LedgerTransactionDetail,
} from '../../api/ledger-transactions';
import { ApiRequestError } from '../../api/request';
import LedgerRefundForm from './LedgerRefundForm.vue';
import { categoryLabel, formatCents, refundToday } from './transaction';

const props = defineProps<{
  ledgerId: string;
  transactionId: string;
  browseOnly?: boolean;
  categoryNames?: Record<string, string>;
}>();
const emit = defineEmits<{
  (event: 'accessLost'): void;
  (event: 'close'): void;
  (event: 'edit', detail: LedgerTransactionDetail): void;
  (event: 'deleted'): void;
  (event: 'refunded'): void;
}>();
const detail = ref<LedgerTransactionDetail | null>(null);
const loading = ref(false);
const error = ref('');
const confirming = ref(false);
const deleting = ref(false);
const refunding = ref(false);
const refundHistory = computed(() => detail.value?.refunds ?? []);
const refundedCents = computed(() =>
  refundHistory.value.reduce((sum, refund) => sum + refund.amountCents, 0),
);
const remainingCents = computed(
  () => (detail.value?.transaction.amountCents ?? 0) - refundedCents.value,
);
const canRefund = computed(
  () =>
    detail.value?.canModify &&
    detail.value.transaction.direction === 'EXPENSE' &&
    remainingCents.value > 0 &&
    detail.value.transaction.bookedOn <= refundToday(),
);

async function onRefundSaved() {
  refunding.value = false;
  emit('refunded');
  await load();
}

async function load() {
  loading.value = true;
  error.value = '';
  try {
    detail.value = await getLedgerTransaction(props.ledgerId, props.transactionId);
  } catch (failure) {
    if (failure instanceof ApiRequestError && [401, 403].includes(failure.status))
      emit('accessLost');
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
    <view
      class="sheet login-sheet"
      role="dialog"
      :aria-label="refunding ? '登记退款' : '流水详情'"
      aria-modal="true"
    >
      <view class="sheet-head">
        <text>{{ refunding ? '登记退款' : '流水详情' }}</text>
        <button
          v-if="!refunding"
          class="close"
          aria-label="关闭详情"
          :disabled="deleting"
          @click="emit('close')"
        >
          ×
        </button>
      </view>
      <text v-if="loading" class="preview-label" role="status">正在加载详情</text>
      <LedgerRefundForm
        v-else-if="refunding && detail"
        :ledger-id="ledgerId"
        :detail="detail"
        :remaining-cents="remainingCents"
        @cancel="refunding = false"
        @saved="onRefundSaved"
      />
      <template v-else-if="detail">
        <view class="detail-caption"
          ><text>{{
            categoryNames?.[detail.transaction.categoryCode] ||
            categoryLabel(detail.transaction.categoryCode, detail.transaction.direction)
          }}</text></view
        >
        <text class="detail-amount" :class="{ income: detail.transaction.direction === 'INCOME' }">
          {{ detail.transaction.direction === 'INCOME' ? '+' : '−' }}¥{{
            formatCents(remainingCents)
          }}
        </text>
        <view v-if="refundHistory.length" class="refund-summary">
          <view
            ><text>原付</text><text>¥{{ formatCents(detail.transaction.amountCents) }}</text></view
          >
          <view
            ><text>已退</text><text>¥{{ formatCents(refundedCents) }}</text></view
          >
          <view
            ><text>实付</text><text>¥{{ formatCents(remainingCents) }}</text></view
          >
        </view>
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
        <LedgerExtensionDetail
          v-for="extension in detail.extensions ?? []"
          :key="extension.type"
          :extension="extension"
          :direction="detail.transaction.direction"
        />
        <view v-if="refundHistory.length" class="meta refund-history">
          <text class="detail-caption">退款记录</text>
          <view v-for="receipt in refundHistory" :key="receipt.id" class="meta-row">
            <view class="refund-history-info"
              ><text>{{ receipt.receivedOn }}</text
              ><text>{{ receipt.accountName || '未选择账户' }}</text></view
            >
            <text>+¥{{ formatCents(receipt.amountCents) }}</text>
          </view>
        </view>
        <button
          v-if="canRefund && !confirming"
          class="primary secondary refund-open"
          @click="refunding = true"
        >
          登记退款
        </button>
        <view v-if="!browseOnly && detail.canModify && !confirming" class="detail-actions">
          <button class="primary secondary" @click="confirming = true">删除这笔</button>
          <button class="primary" @click="emit('edit', detail)">编辑</button>
        </view>
        <view v-if="confirming" class="detail-confirm">
          <text class="preview-label">{{
            refundHistory.length
              ? '退款也会从流水移除，恢复时一并恢复。'
              : '删除后可在「最近删除」恢复。'
          }}</text>
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
.refund-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  padding: 12px;
  border-radius: 12px;
  background: #f5f7fa;
}
.refund-summary view,
.refund-history-info {
  display: grid;
  gap: 4px;
  overflow-wrap: anywhere;
}
.refund-summary view {
  text-align: center;
  padding: 0 2px;
}
.refund-summary text:first-child,
.refund-history-info text:last-child {
  color: var(--muted);
  font-size: 12px;
}
.refund-summary text:last-child {
  font-weight: 650;
}
.refund-open {
  margin: 0;
  width: 100%;
}
.refund-history > .detail-caption {
  display: block;
  padding: 10px 12px 0;
}
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
