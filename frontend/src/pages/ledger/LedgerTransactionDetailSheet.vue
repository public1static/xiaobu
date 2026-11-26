<script setup lang="ts">
import LedgerExtensionDetail from './LedgerExtensionDetail.vue';
import LedgerFixedBillReference from './LedgerFixedBillReference.vue';
import { computed, onMounted, ref } from 'vue';
import {
  deleteLedgerTransaction,
  getLedgerTransaction,
  type LedgerTransactionDetail,
} from '../../api/ledger-transactions';
import { ApiRequestError } from '../../api/request';
import LedgerRefundForm from './LedgerRefundForm.vue';
import { categoryLabel, formatCents, ledgerToday } from './transaction';

const props = defineProps<{
  ledgerId?: string;
  // 游客详情来自内存中的只读示例。
  example?: LedgerTransactionDetail;
  transactionId: string;
  browseOnly?: boolean;
  allowBillUnlink?: boolean;
  categoryNames?: Record<string, string>;
}>();
const emit = defineEmits<{
  (event: 'accessLost'): void;
  (event: 'close'): void;
  (event: 'edit', detail: LedgerTransactionDetail): void;
  (event: 'deleted'): void;
  (event: 'refunded'): void;
  (event: 'billUnlinked'): void;
}>();
const detail = ref<LedgerTransactionDetail | null>(null);
const loading = ref(false);
const error = ref('');
const confirming = ref(false);
const deleting = ref(false);
const refunding = ref(false);
const unlinking = ref(false);
const refundHistory = computed(() => detail.value?.refunds ?? []);
const refundedCents = computed(() =>
  refundHistory.value.reduce((sum, refund) => sum + refund.amountCents, 0),
);
const remainingCents = computed(
  () => (detail.value?.transaction.amountCents ?? 0) - refundedCents.value,
);
const canRefund = computed(
  () =>
    !props.browseOnly &&
    detail.value?.canModify &&
    detail.value.transaction.direction === 'EXPENSE' &&
    remainingCents.value > 0 &&
    detail.value.transaction.bookedOn <= ledgerToday(),
);

async function onRefundSaved() {
  refunding.value = false;
  emit('refunded');
  await load();
}

async function load() {
  if (props.example || !props.ledgerId) {
    detail.value = props.example ?? null;
    return;
  }
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
  if (!props.ledgerId || !detail.value || deleting.value) return;
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
      <view class="sheet-head sheet-child">
        <text>{{ refunding ? '登记退款' : '流水详情' }}</text>
        <button
          v-if="!refunding"
          class="close"
          aria-label="关闭详情"
          :disabled="deleting || unlinking"
          @click="emit('close')"
        >
          ×
        </button>
      </view>
      <text v-if="loading" class="preview-label sheet-child" role="status">正在加载详情</text>
      <LedgerRefundForm
        class="sheet-child"
        v-else-if="refunding && detail && ledgerId"
        :ledger-id="ledgerId"
        :detail="detail"
        :remaining-cents="remainingCents"
        @cancel="refunding = false"
        @saved="onRefundSaved"
      />
      <template v-else-if="detail">
        <view class="detail-summary sheet-child"
          ><view class="detail-summary-head">
            <text
              >{{
                categoryNames?.[detail.transaction.categoryCode] ||
                categoryLabel(detail.transaction.categoryCode, detail.transaction.direction)
              }}{{ refundHistory.length ? ' · 实付' : '' }}</text
            >
            <button
              v-if="canRefund && !confirming"
              :disabled="unlinking"
              class="refund-entry"
              @click="refunding = true"
            >
              记退款
            </button></view
          >
          <text class="detail-amount"
            >{{
              refundHistory.length ? '' : detail.transaction.direction === 'INCOME' ? '+' : '−'
            }}¥{{ formatCents(remainingCents) }}</text
          >
          <text v-if="refundHistory.length" class="refund-breakdown"
            >原付 ¥{{ formatCents(detail.transaction.amountCents) }}　已退 ¥{{
              formatCents(refundedCents)
            }}</text
          ></view
        >
        <view class="meta sheet-child">
          <LedgerFixedBillReference
            v-if="!example && ledgerId && detail.transaction.direction === 'EXPENSE'"
            :ledger-id="ledgerId"
            :transaction-id="transactionId"
            :writable="Boolean(allowBillUnlink) && !browseOnly"
            :disabled="confirming || deleting"
            @unlinked="emit('billUnlinked')"
            @busy="unlinking = $event"
          />
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
          class="sheet-child"
          v-for="extension in detail.extensions ?? []"
          :key="extension.type"
          :extension="extension"
          :direction="detail.transaction.direction"
        />
        <view v-if="refundHistory.length" class="meta refund-history sheet-child">
          <text class="detail-caption">退款记录</text>
          <view v-for="receipt in refundHistory" :key="receipt.id" class="meta-row">
            <view class="refund-history-info"
              ><text>{{ receipt.receivedOn }}</text
              ><text>{{ receipt.accountName || '未选择账户' }}</text></view
            >
            <text>+¥{{ formatCents(receipt.amountCents) }}</text>
          </view>
        </view>
        <view
          v-if="!browseOnly && detail.canModify && !confirming"
          class="detail-actions sheet-child"
        >
          <button class="primary secondary danger" :disabled="unlinking" @click="confirming = true">
            删除这笔
          </button>
          <button class="primary" :disabled="unlinking" @click="emit('edit', detail)">编辑</button>
        </view>
        <view v-if="confirming" class="detail-confirm sheet-child">
          <text class="preview-label">{{
            refundHistory.length
              ? '退款也会从流水移除，恢复时一并恢复。'
              : '删除后可在「最近删除」恢复。'
          }}</text>
          <button class="primary danger-solid" :disabled="deleting" @click="completeDeletion">
            确认删除
          </button>
          <button class="primary secondary" :disabled="deleting" @click="confirming = false">
            取消
          </button>
        </view>
      </template>
      <text v-if="error" class="error sheet-child" role="alert">{{ error }}</text>
      <button v-if="error && !detail" class="text-button sheet-child" @click="load">重试</button>
    </view>
  </view>
</template>

<style scoped src="./ledger.css"></style>
<style scoped>
.detail-summary {
  display: grid;
  gap: 6px;
  background: #124334;
  color: white;
  border-radius: 16px;
  padding: 15px 17px;
}
.detail-summary-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #d6e7dd;
  font-size: 14px;
}
.refund-entry {
  margin: 0;
  padding: 0 10px;
  min-height: 32px;
  line-height: 32px;
  background: #ffffff18;
  color: white;
  border: 1px solid #ffffff42;
  border-radius: 9px;
  font-size: 13px;
}
.refund-breakdown {
  font-size: 13px;
  color: #d6e7dd;
}
.refund-history-info {
  display: grid;
  gap: 4px;
}
.detail-actions .danger {
  color: #a23832;
  border-color: var(--line);
}
.detail-confirm .danger-solid {
  background: #a23832;
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
