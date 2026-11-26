<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue';
import {
  getLedgerMonthlyBudget,
  setLedgerMonthlyBudget,
  type LedgerMonthlyBudget,
} from '../../api/ledger-budgets';
import LedgerIcon from '../../components/LedgerIcon.vue';
import {
  FIRST_LEDGER_MONTH,
  formatCentsForInput,
  formatSignedCurrency,
  ledgerToday,
  parseCents,
  resolveLedgerMonth,
  shiftLedgerMonth,
} from './transaction';

const props = defineProps<{ ledgerId: string; initialMonth: string; writable: boolean }>();
const emit = defineEmits<{ close: [] }>();
const topStyle: Record<string, string> = {};
// #ifdef MP-WEIXIN
topStyle.paddingTop = uni.getMenuButtonBoundingClientRect().bottom + 'px';
// #endif

const currentMonth = ledgerToday().slice(0, 7);
const month = ref(resolveLedgerMonth(props.initialMonth, currentMonth));
const budget = ref<LedgerMonthlyBudget | null>(null);
const loading = ref(false);
const error = ref('');
const editing = ref(false);
const saving = ref(false);
const draft = ref('');
const fieldError = ref('');
let sequence = 0;

const monthLabel = computed(() => {
  const [year, value] = month.value.split('-');
  return `${year}年${Number(value)}月`;
});
const remaining = computed(() => budget.value?.remainingCents ?? null);
const exceeded = computed(() => remaining.value !== null && remaining.value < 0);
const progress = computed(() => {
  const row = budget.value;
  if (!row?.amountCents) return 0;
  return Math.min(100, Math.max(0, (row.usedCents / row.amountCents) * 100));
});

async function loadBudget() {
  const request = ++sequence;
  editing.value = false;
  saving.value = false;
  loading.value = true;
  budget.value = null;
  error.value = '';
  try {
    const row = await getLedgerMonthlyBudget(props.ledgerId, month.value);
    if (request === sequence) budget.value = row;
  } catch {
    if (request === sequence) error.value = '预算加载失败';
  } finally {
    if (request === sequence) loading.value = false;
  }
}

function shiftMonth(offset: number) {
  if (editing.value || saving.value) return;
  const next = shiftLedgerMonth(month.value, offset);
  if (next >= FIRST_LEDGER_MONTH && next <= currentMonth) month.value = next;
}

function editBudget() {
  if (!props.writable || !budget.value) return;
  draft.value = budget.value.amountCents ? formatCentsForInput(budget.value.amountCents) : '';
  fieldError.value = '';
  editing.value = true;
}

async function saveBudget() {
  if (!editing.value || saving.value || !props.writable) return;
  const amountCents = parseCents(draft.value);
  if (amountCents === null) {
    fieldError.value = '请输入大于 0 的金额，最多两位小数';
    return;
  }
  const request = ++sequence;
  saving.value = true;
  fieldError.value = '';
  try {
    const row = await setLedgerMonthlyBudget(props.ledgerId, month.value, amountCents);
    if (request !== sequence) return;
    budget.value = row;
    editing.value = false;
  } catch {
    if (request === sequence) fieldError.value = '预算未保存，请重试';
  } finally {
    if (request === sequence) saving.value = false;
  }
}
watch([() => props.ledgerId, month], () => void loadBudget(), { immediate: true });
watch(
  () => props.writable,
  (writable) => {
    if (!writable) void loadBudget();
  },
);
onUnmounted(() => {
  ++sequence;
});
</script>

<template>
  <view class="budget-workspace">
    <view class="budget-head" :style="topStyle">
      <button class="budget-nav" aria-label="返回" :disabled="saving" @click="emit('close')">
        <LedgerIcon class="budget-icon" src="/static/assistant/back.svg" />
      </button>
      <text>月度预算</text>
      <view />
    </view>
    <view class="budget-body">
      <view class="budget-month">
        <button
          class="budget-nav"
          aria-label="上个月"
          :disabled="editing || month <= FIRST_LEDGER_MONTH"
          @click="shiftMonth(-1)"
        >
          ‹
        </button>
        <text>{{ monthLabel }}</text>
        <button
          class="budget-nav"
          aria-label="下个月"
          :disabled="editing || month >= currentMonth"
          @click="shiftMonth(1)"
        >
          ›
        </button>
      </view>
      <text v-if="loading" class="budget-state">加载中…</text>
      <view v-else-if="error" class="budget-state" role="alert">
        <text>{{ error }}</text><button class="budget-link" @click="loadBudget">重试</button>
      </view>
      <template v-else-if="budget">
        <view class="budget-card">
          <view class="budget-card-label"><text>总预算</text></view>
          <view class="budget-total">
            <text :class="{ unset: budget.amountCents === null }">{{
              budget.amountCents === null ? '未设置' : formatSignedCurrency(budget.amountCents)
            }}</text>
            <button
              v-if="writable && !editing && budget.amountCents !== null"
              class="budget-link"
              @click="editBudget"
            >
              修改预算
            </button>
          </view>
          <view v-if="budget.amountCents !== null" class="budget-track" aria-hidden="true">
            <view class="budget-fill" :class="{ exceeded }" :style="{ width: progress + '%' }" />
          </view>
          <view class="budget-metrics">
            <view
              ><text>已用</text><text>{{ formatSignedCurrency(budget.usedCents) }}</text></view
            >
            <view :class="{ exceeded }">
              <text>{{ exceeded ? '已超出' : '剩余' }}</text>
              <text>{{
                remaining === null ? '—' : formatSignedCurrency(Math.abs(remaining))
              }}</text>
            </view>
          </view>
        </view>
        <view v-if="editing" class="budget-edit">
          <text class="budget-edit-title">设置总预算</text>
          <view class="budget-field">
            <text>¥</text>
            <input
              v-model="draft"
              type="digit"
              aria-label="预算金额"
              placeholder="0.00"
              maxlength="11"
              :disabled="saving"
              @input="fieldError = ''"
            />
          </view>
          <text v-if="fieldError" class="budget-error" role="alert">{{ fieldError }}</text>
          <view class="budget-actions">
            <button class="budget-secondary" :disabled="saving" @click="editing = false">
              取消
            </button>
            <button class="budget-primary" :disabled="saving" @click="saveBudget">
              {{ saving ? '保存中…' : '保存预算' }}
            </button>
          </view>
        </view>
        <button
          v-else-if="writable && budget.amountCents === null"
          class="budget-primary budget-create"
          @click="editBudget"
        >
          设置预算
        </button>
        <text class="budget-note">已用按本月净支出计算，退款抵扣，不含转账和押金。</text>
      </template>
    </view>
  </view>
</template>

<style scoped>
.budget-workspace {
  position: fixed;
  z-index: 29;
  inset: 0;
  overflow-y: auto;
  background: #f4f6f5;
  color: #21352e;
  font-size: 14px;
}
.budget-head {
  position: sticky;
  top: 0;
  z-index: 1;
  display: grid;
  grid-template-columns: 44px 1fr 44px;
  align-items: center;
  min-height: 52px;
  padding: 0 10px;
  border-bottom: 1px solid #e8efeb;
  background: white;
  text-align: center;
  font-size: 17px;
  font-weight: 650;
}
.budget-workspace button {
  margin: 0;
  border: 0;
  font-family: inherit;
}
.budget-workspace button::after {
  border: 0;
}
.budget-nav {
  display: grid;
  width: 44px;
  height: 44px;
  place-items: center;
  padding: 0;
  background: transparent;
  color: #21352e;
  font-size: 24px;
  line-height: 1;
}
.budget-workspace button[disabled] {
  opacity: 0.4;
}
.budget-icon {
  width: 18px;
  height: 18px;
}
.budget-body {
  max-width: 580px;
  margin: 0 auto;
  padding: 8px 14px 28px;
}
.budget-month {
  display: grid;
  grid-template-columns: 44px 1fr 44px;
  align-items: center;
  margin: 0 auto 10px;
  width: min(100%, 242px);
  text-align: center;
  font-size: 15px;
  font-weight: 600;
}
.budget-card,
.budget-edit {
  padding: 18px;
  border-radius: 15px;
  background: white;
}
.budget-card-label,
.budget-note,
.budget-metrics view > text:first-child,
.budget-state {
  color: #50665d;
  font-size: 13px;
}
.budget-total {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 3px;
}
.budget-total > text {
  font-size: 30px;
  font-weight: 650;
  font-variant-numeric: tabular-nums;
}
.budget-total > .unset {
  font-size: 24px;
}
.budget-workspace .budget-link {
  background: transparent;
  color: #176a57;
  padding: 0 3px;
  font-size: 14px;
  line-height: 44px;
}
.budget-track {
  height: 7px;
  border-radius: 8px;
  background: #e9f1ee;
  overflow: hidden;
  margin: 15px 0 3px;
}
.budget-fill {
  height: 100%;
  background: #237967;
  border-radius: 8px;
}
.budget-fill.exceeded {
  background: #ac4a31;
}
.budget-metrics {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-top: 18px;
}
.budget-metrics view {
  display: grid;
  gap: 4px;
  min-width: 0;
  overflow-wrap: anywhere;
  font-weight: 650;
  font-size: 18px;
}
.budget-metrics view > text:first-child {
  font-weight: 400;
}
.budget-metrics .exceeded,
.budget-metrics .exceeded > text:first-child,
.budget-error {
  color: #9b3927;
}
.budget-workspace .budget-primary,
.budget-workspace .budget-secondary {
  padding: 0 12px;
  min-height: 44px;
  line-height: 44px;
  border-radius: 10px;
  font-size: 15px;
  font-weight: 600;
  background: #176a57;
  color: white;
}
.budget-workspace .budget-secondary {
  background: #eef4f1;
  color: #285044;
}
.budget-workspace .budget-create,
.budget-edit {
  margin-top: 12px;
}
.budget-edit-title {
  font-weight: 650;
}
.budget-field {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
  padding: 0 12px;
  min-height: 46px;
  border-radius: 9px;
  background: #f4f6f5;
}
.budget-field input {
  flex: 1;
  min-width: 0;
  font-size: 18px;
}
.budget-actions {
  display: grid;
  grid-template-columns: 1fr 2fr;
  gap: 10px;
  margin-top: 14px;
}
.budget-note,
.budget-error,
.budget-state {
  display: block;
  margin: 12px 4px 0;
  line-height: 1.5;
}
.budget-state {
  text-align: center;
}
</style>
