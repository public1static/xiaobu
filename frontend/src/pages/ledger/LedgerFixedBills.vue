<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import {
  createFixedBill,
  getFixedBillPeriod,
  listFixedBillPeriods,
  listFixedBillPlans,
  payFixedBill,
  reviseFixedBillPlan,
  stopFixedBillPlan,
  type FixedBillPeriod,
  type FixedBillPlan,
} from '../../api/fixed-bills';
import { ApiRequestError } from '../../api/request';
import { listLedgerAccounts, type LedgerAccount } from '../../api/ledger-accounts';
import { listLedgerCategories, type LedgerCategory } from '../../api/ledger-categories';
import LedgerPicker from '../../components/LedgerPicker.vue';
import LedgerCategoryGrid from './LedgerCategoryGrid.vue';
import LedgerFixedBillLink from './LedgerFixedBillLink.vue';
import {
  FIRST_LEDGER_MONTH,
  clampLedgerDueOn,
  formatCents,
  formatCentsForInput,
  ledgerToday,
  parseCents,
  shiftLedgerMonth,
} from './transaction';

// #ifdef H5
import { showConfirmDialog } from 'vant';
import 'vant/es/dialog/style';
// #endif

const props = defineProps<{
  ledgerId: string;
  writable: boolean;
  covered: boolean;
  revision: number;
  initialPeriod?: { billId: string; month: string };
  rental?: {
    billId: string;
    firstDueOn: string;
    endsOn: string;
    cycleMonths: number;
    dueDayOfMonth: number;
  };
}>();
const emit = defineEmits<{ close: []; saved: []; changed: []; detail: [id: string] }>();
const today = ledgerToday();
const cycleLabels: Record<number, string> = { 1: '每月', 3: '季付', 6: '半年付', 12: '年付' };
const workspaceStyle: Record<string, string> = {};
// #ifdef MP-WEIXIN
workspaceStyle.paddingTop = uni.getMenuButtonBoundingClientRect().bottom + 'px';
// #endif
const month = ref(firstVisibleMonth());
function firstVisibleMonth() {
  const current = today.slice(0, 7);
  if (!props.rental) return props.initialPeriod?.month ?? current;
  const first = props.rental.firstDueOn.slice(0, 7);
  let period = first;
  let next = shiftLedgerMonth(period, props.rental.cycleMonths);
  while (next < current && isRentalPeriod(next)) {
    period = next;
    next = shiftLedgerMonth(period, props.rental.cycleMonths);
  }
  return current > period && isRentalPeriod(next) ? next : period;
}
function isRentalPeriod(candidate: string) {
  if (!props.rental) return candidate >= FIRST_LEDGER_MONTH && candidate <= '2100-12';
  const first = props.rental.firstDueOn;
  if (candidate < first.slice(0, 7) || candidate > props.rental.endsOn.slice(0, 7)) return false;
  return clampLedgerDueOn(candidate, props.rental.dueDayOfMonth) <= props.rental.endsOn;
}
const previousPeriod = computed(() =>
  shiftLedgerMonth(month.value, -(props.rental?.cycleMonths ?? 1)),
);
const nextPeriod = computed(() => shiftLedgerMonth(month.value, props.rental?.cycleMonths ?? 1));
const paid = ref(false);
const view = ref<'list' | 'create' | 'period' | 'link' | 'plans' | 'edit' | 'target'>(
  props.initialPeriod ? 'target' : 'list',
);
const selected = ref<FixedBillPeriod>();
const plans = ref<FixedBillPlan[]>([]);
const plansTotal = ref(0);
const plansPage = ref(0);
const plansLoading = ref(false);
const plansError = ref('');
const selectedPlan = ref<FixedBillPlan>();
const planForm = computed(() => view.value === 'create' || view.value === 'edit');
let planLoadNumber = 0;
const rows = ref<FixedBillPeriod[]>([]);
const page = ref(0);
const total = ref(0);
const loading = ref(false);
const saving = ref(false);
const stopping = ref(false);
const linking = ref(false);
const error = ref('');
const catalogError = ref(false);
const catalogLoading = ref(false);
const categories = ref<LedgerCategory[]>([]);
const accounts = ref<LedgerAccount[]>([]);
const name = ref('');
const amount = ref('');
const date = ref(today);
const accountId = ref<string | null>(null);
const categoryCode = ref('');
let loadNumber = 0;

const categoryOptions = computed(() =>
  categories.value
    .filter(
      (row) =>
        row.direction === 'EXPENSE' &&
        row.active &&
        !row.capabilities?.some((item) => item.required),
    )
    .map((row) => [row.code, row.name] as const),
);
const accountOptions = computed(() => [{ id: null, name: '不记账户' }, ...accounts.value]);
const accountIndex = computed(() =>
  Math.max(
    0,
    accountOptions.value.findIndex((row) => row.id === accountId.value),
  ),
);
const selectedCategory = computed(
  () => categories.value.find((row) => row.code === selected.value?.categoryCode)?.name,
);

const categoryNames = computed(() =>
  Object.fromEntries(categories.value.map((category) => [category.code, category.name])),
);

function openLink() {
  error.value = '';
  view.value = 'link';
}

function onLinked(result: FixedBillPeriod) {
  selected.value = result;
  view.value = 'list';
  if (paid.value) void load();
  else paid.value = true;
  emit('saved');
  if (props.initialPeriod) {
    view.value = 'period';
  }
  uni.showToast({ title: '已关联流水', icon: 'none' });
}

async function load(next = 0) {
  const sequence = ++loadNumber;
  loading.value = true;
  error.value = '';
  if (!next) {
    rows.value = [];
    total.value = 0;
  }
  try {
    const result = await listFixedBillPeriods(
      props.ledgerId,
      month.value,
      paid.value,
      next,
      props.rental?.billId,
    );
    if (sequence !== loadNumber) return;
    if (
      next &&
      (total.value !== result.totalCount ||
        result.entries.some((entry) => rows.value.some((row) => row.billId === entry.billId)))
    ) {
      await load();
      return;
    }
    rows.value = next ? [...rows.value, ...result.entries] : result.entries;
    total.value = result.totalCount;
    page.value = result.page;
  } catch {
    if (sequence === loadNumber) error.value = '账单加载失败，请重试。';
  } finally {
    if (sequence === loadNumber) loading.value = false;
  }
}

async function loadCatalog() {
  catalogLoading.value = true;
  catalogError.value = false;
  try {
    const [categoryList, accountList] = await Promise.all([
      listLedgerCategories(props.ledgerId),
      listLedgerAccounts(props.ledgerId),
    ]);
    categories.value = categoryList;
    accounts.value = accountList;
  } catch {
    catalogError.value = true;
  } finally {
    catalogLoading.value = false;
  }
}

watch(
  [month, paid],
  () => {
    if (!props.initialPeriod) void load();
  },
  { immediate: true },
);
async function loadInitialPeriod() {
  const target = props.initialPeriod;
  if (!target) return;
  view.value = 'target';
  loading.value = true;
  error.value = '';
  try {
    openPeriod(await getFixedBillPeriod(props.ledgerId, target.billId, target.month));
  } catch {
    error.value = '账期不可用或加载失败，请返回待办刷新后重试。';
  } finally {
    loading.value = false;
  }
}
if (props.initialPeriod) void loadInitialPeriod();
watch(
  () => props.revision,
  () => {
    if (props.initialPeriod) {
      void loadInitialPeriod();
      return;
    }
    view.value = 'list';
    selected.value = undefined;
    if (!paid.value) void load();
    else paid.value = false;
  },
);

void loadCatalog();

async function loadPlans(pageIndex = 0) {
  const sequence = ++planLoadNumber;
  plansLoading.value = true;
  plansError.value = '';
  if (!pageIndex) {
    plans.value = [];
    plansTotal.value = 0;
    plansPage.value = 0;
  }
  try {
    const result = await listFixedBillPlans(props.ledgerId, pageIndex);
    if (sequence !== planLoadNumber) return;
    if (
      pageIndex &&
      (result.totalCount !== plansTotal.value ||
        result.entries.some((item) => plans.value.some((plan) => plan.billId === item.billId)))
    ) {
      await loadPlans();
      return;
    }
    plans.value = pageIndex ? [...plans.value, ...result.entries] : result.entries;
    plansTotal.value = result.totalCount;
    plansPage.value = result.page;
  } catch {
    if (sequence === planLoadNumber) plansError.value = '账单计划加载失败，请重试。';
  } finally {
    if (sequence === planLoadNumber) plansLoading.value = false;
  }
}

if (props.writable && !props.rental) void loadPlans();

function openPlans() {
  view.value = 'plans';
  void loadPlans();
}

function editPlan(plan: FixedBillPlan) {
  selectedPlan.value = plan;
  name.value = plan.name;
  amount.value = formatCentsForInput(plan.amountCents);
  date.value = plan.earliestFirstDueOn;
  categoryCode.value = plan.categoryCode;
  accountId.value = plan.accountId ?? null;
  error.value = '';
  view.value = 'edit';
}

function shiftMonth(direction: -1 | 1) {
  const next = direction === -1 ? previousPeriod.value : nextPeriod.value;
  if (isRentalPeriod(next)) month.value = next;
}

function create() {
  name.value = '';
  amount.value = '';
  date.value = today;
  accountId.value = null;
  categoryCode.value = '';
  view.value = 'create';
  error.value = '';
}

function openPeriod(row: FixedBillPeriod) {
  selected.value = row;
  amount.value = formatCentsForInput(row.amountCents);
  date.value = today;
  accountId.value = row.accountId ?? null;
  error.value = '';
  view.value = 'period';
}

function back() {
  if (saving.value || stopping.value || linking.value) return;
  if (view.value === 'edit') openPlans();
  else if (view.value === 'link') view.value = 'period';
  else if (view.value === 'list' || props.initialPeriod) emit('close');
  else {
    error.value = '';
    view.value = 'list';
    void load();
  }
}

async function confirmStop(stopOn: string) {
  const title = '停止后续账期？';
  const message = '从 ' + stopOn + ' 起停止生成账期，已有账期和流水保留。';
  // #ifdef H5
  try {
    await showConfirmDialog({
      title,
      message,
      confirmButtonText: '停止后续账期',
      confirmButtonColor: '#a54341',
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
      confirmText: '停止账期',
      confirmColor: '#a54341',
      success: (result) => resolve(result.confirm),
      fail: () => resolve(false),
    }),
  );
  // #endif
}

async function stopPlan() {
  const plan = selectedPlan.value;
  if (!plan || saving.value || stopping.value || !props.writable) return;
  stopping.value = true;
  error.value = '';
  try {
    if (!(await confirmStop(plan.earliestFirstDueOn))) return;
    await stopFixedBillPlan(props.ledgerId, plan.billId, plan.earliestFirstDueOn);
    selectedPlan.value = undefined;
    view.value = 'list';
    void loadPlans();
    await load();
    emit('changed');
    uni.showToast({ title: '已停止后续账期', icon: 'none' });
  } catch (failure) {
    error.value =
      failure instanceof ApiRequestError && failure.status === 409
        ? '计划或预付账期已变更，请返回账单计划重新打开。'
        : '未确认是否停止，请返回账单计划核对。';
  } finally {
    stopping.value = false;
  }
}

async function save() {
  if (
    saving.value ||
    stopping.value ||
    !props.writable ||
    catalogError.value ||
    catalogLoading.value
  )
    return;
  const amountCents = parseCents(amount.value);
  if (amountCents == null || amountCents <= 0) {
    error.value = '请输入大于零的金额，最多两位小数。';
    return;
  }
  if (
    planForm.value &&
    (!name.value.trim() || !categoryOptions.value.some(([code]) => code === categoryCode.value))
  ) {
    error.value = '请填写账单名称并选择支出分类。';
    return;
  }
  saving.value = true;
  error.value = '';
  try {
    if (planForm.value) {
      const form = {
        name: name.value.trim(),
        firstDueOn: date.value,
        amountCents,
        categoryCode: categoryCode.value,
        accountId: accountId.value,
      };
      if (view.value === 'edit' && selectedPlan.value) {
        await reviseFixedBillPlan(props.ledgerId, selectedPlan.value.billId, form);
        uni.showToast({ title: '后续账期已更新', icon: 'none' });
      } else {
        await createFixedBill(props.ledgerId, form);
      }
      emit('changed');
      void loadPlans();
      view.value = 'list';
      const nextMonth = date.value.slice(0, 7);
      if (nextMonth === month.value && !paid.value) await load();
      else {
        month.value = nextMonth;
        paid.value = false;
      }
    } else if (selected.value) {
      selected.value = await payFixedBill(props.ledgerId, selected.value.billId, month.value, {
        paidOn: date.value,
        amountCents,
        accountId: accountId.value,
      });
      emit('saved');
    }
  } catch (failure) {
    error.value =
      view.value === 'edit' && failure instanceof ApiRequestError && failure.status === 409
        ? '计划或预付账期已变更，请返回账单计划重新打开。'
        : planForm.value
          ? '账单未确认保存，请返回列表核对。'
          : '付款未确认，请返回列表核对后再试。';
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <view v-show="!covered" class="bill-workspace" :style="workspaceStyle">
    <view class="bill-header">
      <button aria-label="返回" class="bill-header-action bill-back" @click="back">‹</button>
      <text>{{
        view === 'create'
          ? '新增固定账单'
          : view === 'period' || view === 'target'
            ? '本期账单'
            : view === 'link'
              ? '关联已有流水'
              : view === 'plans'
                ? '账单计划'
                : view === 'edit'
                  ? '修改后续账期'
                  : rental
                    ? '房租账期'
                    : '固定账单'
      }}</text>
      <button
        v-if="view === 'list' && writable && !rental"
        class="bill-header-action bill-add"
        @click="create"
      >
        新增
      </button>
      <view v-else class="bill-header-space" />
    </view>
    <view v-if="view === 'target'" class="bill-content">
      <text v-if="loading" class="bill-retry">加载中…</text>
      <button v-else class="bill-retry" role="alert" @click="loadInitialPeriod">{{ error }}</button>
    </view>
    <view v-else-if="view === 'list'" class="bill-content">
      <button
        v-if="writable && !rental && (plansTotal || plansError)"
        class="bill-row bill-manage"
        @click="openPlans"
      >
        <text>管理账单计划</text><text>›</text>
      </button>
      <view class="bill-month">
        <button
          :aria-label="rental ? '上期' : '上个月'"
          :disabled="!!rental && !isRentalPeriod(previousPeriod)"
          :aria-disabled="rental && !isRentalPeriod(previousPeriod) ? 'true' : undefined"
          @click="shiftMonth(-1)"
        >
          ‹
        </button>
        <text>{{ month.replace('-', ' 年 ') }} 月</text>
        <button
          :aria-label="rental ? '下期' : '下个月'"
          :disabled="!!rental && !isRentalPeriod(nextPeriod)"
          :aria-disabled="rental && !isRentalPeriod(nextPeriod) ? 'true' : undefined"
          @click="shiftMonth(1)"
        >
          ›
        </button>
      </view>
      <view class="bill-tabs">
        <button :class="{ active: !paid }" :aria-pressed="!paid" @click="paid = false">
          待付{{ !paid && total ? ' · ' + total : '' }}
        </button>
        <button :class="{ active: paid }" :aria-pressed="paid" @click="paid = true">
          已付{{ paid && total ? ' · ' + total : '' }}
        </button>
      </view>
      <view v-if="rows.length" class="bill-list">
        <button v-for="row in rows" :key="row.billId" class="bill-row" @click="openPeriod(row)">
          <view
            ><text>{{ row.name }}</text
            ><text class="bill-caption">{{
              row.paidOn ? row.paidOn + ' 已付' : row.dueOn + ' 到期'
            }}</text></view
          >
          <text>¥{{ formatCents(row.paidAmountCents ?? row.amountCents) }} ›</text>
        </button>
      </view>
      <text v-if="loading" class="bill-empty">加载中…</text>
      <button v-else-if="error" class="bill-retry" @click="load(rows.length ? page + 1 : 0)">
        {{ error }}
      </button>
      <text v-else-if="!rows.length" class="bill-empty">{{
        paid ? '本月暂无已付账单' : '本月没有待付账单'
      }}</text>
      <button v-else-if="rows.length < total" class="bill-retry" @click="load(page + 1)">
        加载更多
      </button>
    </view>
    <view v-else-if="view === 'plans'" class="bill-content">
      <view v-if="plans.length" class="bill-list">
        <button v-for="plan in plans" :key="plan.billId" class="bill-row" @click="editPlan(plan)">
          <view
            ><text>{{ plan.name }}</text
            ><text class="bill-caption">每月</text></view
          ><text>›</text>
        </button>
      </view>
      <text v-if="plansLoading" class="bill-empty">加载中…</text>
      <button
        v-else-if="plansError"
        class="bill-retry"
        @click="loadPlans(plans.length ? plansPage + 1 : 0)"
      >
        {{ plansError }}
      </button>
      <text v-else-if="!plans.length" class="bill-empty">暂无账单计划</text>
      <button
        v-else-if="plans.length < plansTotal"
        class="bill-retry"
        @click="loadPlans(plansPage + 1)"
      >
        加载更多
      </button>
    </view>
    <view v-else class="bill-content">
      <view v-if="(view === 'period' || view === 'link') && selected" class="bill-summary">
        <text>{{ month }} · {{ cycleLabels[selected.cycleMonths] || '每月' }}</text
        ><text>{{ selected.name }}</text>
        <text>{{ selected.dueOn }} 到期{{ selectedCategory ? ' · ' + selectedCategory : '' }}</text>
      </view>
      <LedgerFixedBillLink
        v-if="view === 'link' && selected"
        :ledger-id="ledgerId"
        :bill-id="selected.billId"
        :month="month"
        :category-names="categoryNames"
        @linked="onLinked"
        @busy="linking = $event"
      />
      <view v-else-if="selected?.transactionId && view === 'period'" class="bill-form">
        <view class="bill-field"
          ><text>实际金额</text><text>¥{{ formatCents(selected.paidAmountCents ?? 0) }}</text></view
        >
        <view class="bill-field"
          ><text>付款日期</text><text>{{ selected.paidOn }}</text></view
        >
        <button class="bill-row" @click="emit('detail', selected.transactionId!)">
          <text>查看付款流水</text><text>›</text>
        </button>
      </view>
      <view v-else-if="writable" class="bill-form">
        <view v-if="planForm" class="bill-field"
          ><text>账单名称 *</text
          ><input v-model="name" aria-label="账单名称" maxlength="40" placeholder="如：宽带费"
        /></view>
        <view v-if="planForm" class="bill-field"><text>周期</text><text>每月</text></view>
        <view v-if="planForm" class="bill-field">
          <text>{{ view === 'edit' ? '调整后首期 *' : '首次到期日 *' }}</text>
          <LedgerPicker
            mode="date"
            :label="view === 'edit' ? '调整后首期' : '首次到期日'"
            :start="view === 'edit' ? selectedPlan?.earliestFirstDueOn : undefined"
            :value="date"
            @change="date = $event.detail.value"
            ><text class="bill-picker-value">{{ date }} ›</text></LedgerPicker
          >
        </view>
        <view class="bill-field">
          <text>{{ planForm ? '约定金额 *' : '实际金额 *' }}</text>
          <input
            v-model="amount"
            :aria-label="planForm ? '约定金额' : '实际金额'"
            type="digit"
            maxlength="11"
            placeholder="0.00"
          />
        </view>
        <view v-if="view === 'period'" class="bill-field">
          <text>付款日期 *</text>
          <LedgerPicker
            mode="date"
            label="付款日期"
            :value="date"
            :end="today"
            @change="date = $event.detail.value"
            ><text class="bill-picker-value">{{ date }} ›</text></LedgerPicker
          >
        </view>
        <view v-if="planForm" class="bill-categories">
          <text>支出分类 *</text>
          <LedgerCategoryGrid
            :items="categoryOptions"
            :selected="categoryCode"
            @select="categoryCode = $event"
          />
        </view>
        <view class="bill-field">
          <text>{{ planForm ? '常用账户' : '付款账户' }}</text>
          <LedgerPicker
            label="选择账户"
            :value="accountIndex"
            :range="accountOptions"
            range-key="name"
            @change="accountId = accountOptions[Number($event.detail.value)].id"
            ><text class="bill-picker-value"
              >{{ accountOptions[accountIndex].name }} ›</text
            ></LedgerPicker
          >
        </view>
      </view>
      <view v-else-if="selected" class="bill-field"
        ><text>约定金额</text><text>¥{{ formatCents(selected.amountCents) }}</text></view
      >
      <text v-if="error" class="bill-empty">{{ error }}</text>
      <button v-if="catalogError" class="bill-retry" @click="loadCatalog">
        账户和分类加载失败，重试
      </button>
      <view
        v-if="writable && (planForm || (view === 'period' && !selected?.transactionId))"
        :class="{ 'bill-edit-actions': view === 'edit' }"
      >
        <button
          v-if="view === 'edit'"
          class="bill-submit bill-stop"
          :disabled="saving || stopping"
          @click="stopPlan"
        >
          停止后续账期
        </button>
        <button
          class="bill-submit"
          :disabled="saving || stopping || catalogError || catalogLoading"
          @click="save"
        >
          {{ saving ? '正在保存…' : planForm ? '保存' : '记为已付' }}
        </button>
      </view>
      <button
        v-if="view === 'period' && writable && selected && !selected.transactionId"
        class="bill-submit bill-link"
        :disabled="saving"
        @click="openLink"
      >
        关联已有流水
      </button>
    </view>
  </view>
</template>

<style scoped>
.bill-workspace {
  position: fixed;
  inset: 0;
  z-index: 15;
  overflow: auto;
  background: #f3f5f1;
  padding: env(safe-area-inset-top) 0 env(safe-area-inset-bottom);
  color: #142b25;
}
.bill-header,
.bill-content {
  max-width: 540px;
  margin: 0 auto;
  padding: 0 16px;
}
.bill-header {
  min-height: 56px;
  display: grid;
  grid-template-columns: 52px 1fr 52px;
  align-items: center;
  text-align: center;
  font-size: 17px;
  font-weight: 700;
}
.bill-header-action,
.bill-header-space {
  width: 52px;
}
.bill-header-action {
  background: transparent;
  border: 0;
  color: #176a57;
  font-size: 14px;
  min-height: 44px;
  line-height: 44px;
  padding: 0;
  margin: 0;
}
.bill-back {
  text-align: left;
  font-size: 28px;
}
.bill-add {
  text-align: right;
}
.bill-workspace button::after {
  border: 0;
}
.bill-picker-value {
  display: block;
  text-align: right;
  color: #176a57;
}
.bill-month,
.bill-row,
.bill-field {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.bill-manage {
  margin: 10px 0 14px;
  border-radius: 14px;
}
.bill-month {
  margin: 10px 0 16px;
  font-weight: 650;
}
.bill-month button {
  width: 40px;
  height: 36px;
  line-height: 36px;
  padding: 0;
  margin: 0;
  background: #e7f1eb;
  border: 0;
  border-radius: 10px;
  color: #176a57;
  font-size: 22px;
}
.bill-month button[aria-disabled='true'] {
  opacity: 0.42;
}
.bill-tabs {
  display: flex;
  padding: 3px;
  gap: 4px;
  border-radius: 12px;
  background: #e7eee9;
}
.bill-tabs button {
  flex: 1;
  margin: 0;
  border: 0;
  background: transparent;
  font-size: 14px;
  color: #52675d;
  line-height: 34px;
}
.bill-tabs button.active {
  background: white;
  color: #176a57;
  font-weight: 700;
  border-radius: 9px;
}
.bill-list,
.bill-form {
  background: white;
  border-radius: 14px;
  overflow: hidden;
  margin-top: 14px;
}
.bill-row,
.bill-field {
  box-sizing: border-box;
  width: 100%;
  min-height: 51px;
  margin: 0;
  padding: 12px 14px;
  border: 0;
  border-bottom: 1px solid #edf1ee;
  background: white;
  border-radius: 0;
  text-align: left;
  line-height: 1.45;
  font-size: 14px;
}
.bill-row > text:last-child {
  color: #176a57;
  flex: none;
}
.bill-row > view {
  min-width: 0;
  overflow-wrap: anywhere;
}
.bill-caption {
  display: block;
  font-size: 12px;
  color: #607267;
  margin-top: 3px;
}
.bill-field > text:first-child {
  flex: none;
}
.bill-field input {
  text-align: right;
  font-size: 14px;
  min-width: 0;
}
.bill-categories {
  padding: 12px 14px;
  font-size: 14px;
}
.bill-empty {
  display: block;
  color: #607267;
  text-align: center;
  padding: 36px 8px;
  font-size: 14px;
}
.bill-retry {
  margin: 12px auto;
  background: transparent;
  border: 0;
  color: #176a57;
  font-size: 14px;
}
.bill-summary {
  display: flex;
  flex-direction: column;
  gap: 6px;
  background: #155843;
  color: #d5e8df;
  border-radius: 16px;
  padding: 16px;
  font-size: 13px;
  margin-top: 12px;
}
.bill-summary text:nth-child(2) {
  font-size: 22px;
  font-weight: 700;
  color: white;
  overflow-wrap: anywhere;
}
.bill-submit {
  margin-top: 16px;
  background: #176a57;
  color: white;
  min-height: 44px;
  line-height: 44px;
  border: 0;
  border-radius: 999px;
  font-size: 15px;
}
.bill-edit-actions {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 10px;
}
.bill-edit-actions .bill-submit {
  box-sizing: border-box;
  width: 100%;
}
.bill-stop {
  background: white;
  color: #a54341;
  border: 1px solid #ead9d7;
  padding: 0 13px;
  font-size: 14px;
}
.bill-link {
  margin-top: 10px;
  background: white;
  color: #176a57;
  border: 1px solid #dce8e1;
}
.bill-submit[disabled] {
  opacity: 0.55;
}
</style>
