<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { listPendingRentalPeriods } from '../../api/rentals';
import type { FixedBillPeriod } from '../../api/fixed-bills';
import type { TransactionExtensionDetail } from '../../api/transaction-extensions';
import { FIRST_LEDGER_MONTH, formatCents, ledgerToday, shiftLedgerMonth } from './transaction';
// #ifdef H5
import { ActionSheet as VanActionSheet } from 'vant';
import 'vant/es/action-sheet/style';
// #endif

defineOptions({ inheritAttrs: false });

const props = defineProps<{
  ledgerId: string;
  detail: TransactionExtensionDetail;
  bookedOn?: string;
  disabled?: boolean;
}>();
const emit = defineEmits<{
  change: [detail: TransactionExtensionDetail | null];
  'amount-default': [cents: number];
}>();
const open = ref(false);
const month = ref(ledgerToday().slice(0, 7));
const rows = ref<FixedBillPeriod[]>([]);
const page = ref(0);
const total = ref(0);
const loading = ref(false);
const error = ref(false);
let sequence = 0;
const selected = computed(
  () => typeof props.detail.billId === 'string' && typeof props.detail.month === 'string',
);
const value = computed(() =>
  selected.value ? `${props.detail.billName || '租房账单'} · ${props.detail.month}` : '选择账期',
);

function close() {
  open.value = false;
  sequence++;
}

function show() {
  if (props.disabled) return;
  month.value = String(selected.value ? props.detail.month : props.bookedOn || ledgerToday()).slice(
    0,
    7,
  );
  open.value = true;
  void load();
}

async function load(next = 0) {
  const request = ++sequence;
  error.value = false;
  loading.value = true;
  if (!next) rows.value = [];
  try {
    const result = await listPendingRentalPeriods(props.ledgerId, month.value, next);
    if (request !== sequence) return;
    if (
      next &&
      (result.totalCount !== total.value ||
        result.entries.some((item) => rows.value.some((row) => row.billId === item.billId)))
    ) {
      await load();
      return;
    }
    rows.value = next ? [...rows.value, ...result.entries] : result.entries;
    page.value = result.page;
    total.value = result.totalCount;
  } catch {
    if (request === sequence) error.value = true;
  } finally {
    if (request === sequence) loading.value = false;
  }
}

function shiftMonth(offset: number) {
  const next = shiftLedgerMonth(month.value, offset);
  if (next < FIRST_LEDGER_MONTH || next > '2100-12') return;
  month.value = next;
  void load();
}

function choose(row: FixedBillPeriod) {
  emit('change', { billId: row.billId, month: month.value, billName: row.name });
  emit('amount-default', row.amountCents);
  close();
}

function clear() {
  if (!props.disabled) emit('change', null);
}

watch(() => props.ledgerId, close);
</script>

<template>
  <view class="rent-field" :class="{ 'has-selection': selected }">
    <button
      class="rent-trigger"
      aria-label="关联租房账单"
      aria-haspopup="dialog"
      :disabled="disabled"
      @click="show"
    >
      <text>关联租房账单</text>
      <text class="rent-value" :class="{ placeholder: !selected }">{{ value }}</text>
      <text class="rent-chevron" aria-hidden="true">›</text>
    </button>
    <button
      v-if="selected"
      class="rent-clear"
      aria-label="清除房租账期"
      :disabled="disabled"
      @click.stop="clear"
    >
      ⊗
    </button>
  </view>

  <!-- #ifdef H5 -->
  <VanActionSheet
    v-model:show="open"
    teleport="body"
    title="关联租房账单"
    class="rent-period-sheet"
    cancel-text="取消"
  >
    <!-- #endif -->
    <!-- #ifdef MP-WEIXIN -->
    <view v-if="open" class="rent-period-overlay" @click.self="close">
      <view class="rent-period-sheet">
        <view class="rent-period-title"
          ><text>关联租房账单</text><button @click="close">取消</button></view
        >
        <!-- #endif -->
        <view v-if="open" class="rent-period-body">
          <view class="rent-month">
            <button
              aria-label="上一个账期月份"
              :disabled="month <= FIRST_LEDGER_MONTH"
              @click="shiftMonth(-1)"
            >
              ‹
            </button>
            <text>{{ month.replace('-', ' 年 ') }} 月</text>
            <button
              aria-label="下一个账期月份"
              :disabled="month >= '2100-12'"
              @click="shiftMonth(1)"
            >
              ›
            </button>
          </view>
          <button
            v-for="row in rows"
            :key="row.billId"
            class="rent-period-row"
            @click="choose(row)"
          >
            <view class="rent-period-label"
              ><text>{{ row.name }}</text
              ><text class="rent-period-caption">{{ row.dueOn }} 到期</text></view
            >
            <text class="rent-period-money">¥{{ formatCents(row.amountCents) }}</text>
            <text aria-hidden="true">›</text>
          </button>
          <text v-if="loading" class="rent-period-empty">加载中…</text>
          <button
            v-else-if="error"
            class="rent-period-more"
            @click="load(rows.length ? page + 1 : 0)"
          >
            加载失败，点击重试
          </button>
          <text v-else-if="!rows.length" class="rent-period-empty"
            >本月没有待付房租账单，可切换月份。尚未设置时可到「租房 → 合同」设置。</text
          >
          <button v-else-if="rows.length < total" class="rent-period-more" @click="load(page + 1)">
            加载更多
          </button>
        </view>
        <!-- #ifdef MP-WEIXIN -->
      </view>
    </view>
    <!-- #endif -->
    <!-- #ifdef H5 -->
  </VanActionSheet>
  <!-- #endif -->
</template>

<style scoped>
.rent-field {
  position: relative;
  border-radius: 14px;
  background: #fff;
  color: #21352e;
}
.rent-trigger,
.rent-clear,
.rent-period-body button,
.rent-period-title button {
  margin: 0;
  padding: 0;
  border: 0;
  background: transparent;
  color: inherit;
  line-height: 1.4;
  font-size: 14px;
}
.rent-field button::after,
.rent-period-body button::after,
.rent-period-title button::after {
  border: 0;
}
.rent-trigger {
  width: 100%;
  display: grid;
  grid-template-columns: 84px minmax(0, 1fr) 16px;
  gap: 10px;
  align-items: center;
  min-height: 46px;
  padding: 8px 13px;
  text-align: left;
}
.rent-value {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.rent-field.has-selection .rent-value {
  padding-right: 32px;
}
.rent-clear {
  position: absolute;
  right: 27px;
  top: 4px;
  min-width: 36px;
  min-height: 38px;
  line-height: 38px;
  color: #62766d;
}
.placeholder,
.rent-chevron,
.rent-period-caption,
.rent-period-empty {
  color: #62766d;
}
.rent-chevron {
  text-align: right;
  font-size: 18px;
}
.rent-period-overlay {
  position: fixed;
  z-index: 99;
  inset: 0;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  background: rgba(10, 24, 18, 0.48);
}
.rent-period-sheet {
  width: 100%;
  max-width: 440px;
  margin: 0 auto;
  right: 0;
  border-radius: 16px 16px 0 0;
  background: white;
}
.rent-period-title,
.rent-month,
.rent-period-body .rent-period-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.rent-period-title {
  padding: 8px 15px;
  min-height: 46px;
  font-weight: 600;
}
.rent-period-title button,
.rent-period-body .rent-period-more {
  color: #176a57;
}
.rent-period-body {
  overflow-y: auto;
  max-height: 55vh;
  padding: 8px 16px 15px;
  color: #21352e;
}
.rent-month {
  min-height: 42px;
  margin-bottom: 6px;
}
.rent-month button {
  min-width: 38px;
  min-height: 38px;
  border-radius: 10px;
  background: #eaf1ed;
  color: #176a57;
  font-size: 22px;
}
.rent-period-body .rent-period-row {
  width: 100%;
  padding: 10px 4px;
  border-bottom: 1px solid #e8eeeb;
  text-align: left;
}
.rent-period-label {
  flex: 1;
  min-width: 0;
  overflow-wrap: anywhere;
}
.rent-period-caption,
.rent-period-empty {
  display: block;
  font-size: 13px;
}
.rent-period-caption {
  margin-top: 3px;
}
.rent-period-empty {
  padding: 18px 4px;
  text-align: center;
}
.rent-period-money {
  white-space: nowrap;
}
.rent-period-more {
  min-height: 44px;
}
</style>
