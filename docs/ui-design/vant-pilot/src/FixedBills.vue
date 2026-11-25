<script setup>
import RecordActions from './RecordActions.vue';
import OptionalField from './OptionalField.vue';
import SlotField from './SlotField.vue';
import DateField from './DateField.vue';
import { computed, inject, ref } from 'vue';
import {
  Form as VanForm,
  Button as VanButton,
  Cell as VanCell,
  Field as VanField,
  Icon as VanIcon,
  Search as VanSearch,
  showConfirmDialog,
  showToast,
} from 'vant';
import SelectRow from './SelectRow.vue';
import { billReminder } from './reminder-sources.mjs';
import { reminderOptions } from './reminders.mjs';
import ChoiceField from './ChoiceField.vue';
import CategoryPicker from './CategoryPicker.vue';
import { amountText, validDay } from './ledger.mjs';
import {
  cycleLabels,
  billPeriods,
  settleBill,
  shiftMonth,
  nextPlanDate,
  revisePlan,
} from './fixed-bills.mjs';
const props = defineProps({
  book: Object,
  today: String,
  actor: String,
  write: Function,
  billId: String,
  openBill: String,
  initialMonth: String,
});
const leadOptions = reminderOptions(billReminder);
const emit = defineEmits(['back', 'row']);
const writable = inject('bookWritable'),
  directory = inject('choiceDirectory');
// 页面保留当前月份和选中的周期，不通过流水日期推断所属期。
const month = ref(props.initialMonth || props.today.slice(0, 7)),
  page = ref('list'),
  selected = ref(''),
  tab = ref('pending');
const draft = ref({}),
  money = ref(''),
  account = ref(''),
  date = ref(''),
  error = ref(''),
  query = ref(''),
  linked = ref('');
const periods = computed(() =>
  billPeriods(props.book, month.value).filter((p) => !props.billId || p.bill.id === props.billId),
);
const lease = computed(() => props.book.leases?.find((l) => l.billId === selected.value));
const period = computed(() => periods.value.find((p) => p.bill.id === selected.value));
const visible = computed(() =>
  periods.value.filter((p) => (tab.value === 'paid' ? p.row : !p.row)),
);
// 候选可跨月，优先显示本期和同分类的支出，以便处理提前付款或补缴。
const candidates = computed(() => {
  const used = new Set((props.book.fixedBills || []).flatMap((b) => Object.values(b.paid)));
  const rank = (r) =>
    (r.date.startsWith(month.value) ? 2 : 0) + (r.category === period.value?.bill.category ? 1 : 0);
  const term = query.value.trim().toLowerCase();
  return props.book.rows
    .filter(
      (r) =>
        r.kind === 'expense' &&
        r.scope === 'personal' &&
        !used.has(r.id) &&
        [r.date, r.note, r.account, r.category, amountText(r.amount), String(r.amount / 100)]
          .join(' ')
          .toLowerCase()
          .includes(term),
    )
    .sort((a, b) => rank(b) - rank(a) || b.date.localeCompare(a.date));
});
// 金额以分保存，浮动账单可在确认付款时填写。
function cents(value) {
  return /^\d{1,8}(?:\.\d{1,2})?$/.test(value) ? Math.round(Number(value) * 100) : NaN;
}
function create() {
  draft.value = {
    remindDays: billReminder.defaultDays,
    name: '',
    cycle: 'month',
    start: month.value === props.today.slice(0, 7) ? props.today : month.value + '-01',
    category: '',
  };
  money.value = '';
  account.value = '';
  error.value = '';
  page.value = 'create';
}
// 独立固定账单集中维护，合同房租仍从合同编辑。
const plans = computed(() =>
  (props.book.fixedBills || []).filter(
    (b) => !b.stopMonth && !props.book.leases?.some((l) => l.billId === b.id),
  ),
);
const effective = ref('');
function editPlan(bill) {
  selected.value = bill.id;
  effective.value = nextPlanDate(bill, props.today);
  draft.value = {
    name: bill.name,
    cycle: bill.cycle,
    start: effective.value,
    category: bill.category,
    remindDays: bill.remindDays ?? billReminder.defaultDays,
  };
  money.value = bill.amount === null ? '' : String(bill.amount / 100);
  account.value = bill.account;
  page.value = 'edit';
}
// 截止月份只影响尚未开始的账期，历史付款和欠款保留。
async function stopPlan() {
  try {
    await showConfirmDialog({
      title: '停止后续账期？',
      message: '从 ' + effective.value + ' 起停止生成账期，已有账期和流水保留。',
      confirmButtonText: '停止后续账期',
    });
  } catch {
    return;
  }
  const next = revisePlan(props.book, selected.value, props.today);
  if (next && props.write(next)) {
    page.value = 'list';
    showToast('已停止后续账期');
  }
}
function savePlan() {
  const bill = {
    ...draft.value,
    id: crypto.randomUUID(),
    name: draft.value.name.trim(),
    amount: money.value === '' ? null : cents(money.value),
    account: account.value,
    paid: {},
  };
  if (page.value === 'edit') {
    const next = revisePlan(props.book, selected.value, props.today, bill);
    if (next && props.write(next)) {
      month.value = bill.start.slice(0, 7);
      tab.value = 'pending';
      page.value = 'list';
      showToast('后续账期已更新');
    }
    return;
  }
  if (props.write({ ...props.book, fixedBills: [...(props.book.fixedBills || []), bill] })) {
    month.value = bill.start.slice(0, 7);
    tab.value = 'pending';
    page.value = 'list';
    showToast('已创建固定账单');
  }
}
// 待付记录只在明确确认后入账。
function openPeriod(p) {
  if (p.row) {
    emit('row', p.row.id);
    return;
  }
  selected.value = p.bill.id;
  money.value = p.bill.amount === null ? '' : String(p.bill.amount / 100);
  account.value = directory.accounts.value.some((a) => a.active && a.name === p.bill.account)
    ? p.bill.account
    : '';
  date.value = props.today;
  error.value = '';
  page.value = 'pay';
}
function link() {
  query.value = '';
  linked.value = '';
  error.value = '';
  page.value = 'link';
}
function confirm() {
  if (!writable.value) return;
  const byLink = page.value === 'link';
  if (byLink && !linked.value) {
    error.value = '请选择已有流水。';
    return;
  }
  const result = settleBill(
    props.book,
    selected.value,
    month.value,
    byLink
      ? { rowId: linked.value }
      : {
          payment: {
            amount: cents(money.value),
            date: date.value,
            account: account.value,
            author: props.actor,
          },
        },
  );
  if (result.error) {
    if (byLink) error.value = result.error;
    else showToast(result.error);
    return;
  }
  if (props.write(result.book)) {
    tab.value = 'paid';
    if (props.openBill) emit('back');
    else page.value = 'list';
    showToast(byLink ? '已关联流水' : '已记为已付');
  }
}
function back() {
  if (page.value === 'list' || (props.openBill && page.value === 'pay')) emit('back');
  else page.value = page.value === 'link' ? 'pay' : page.value === 'edit' ? 'plans' : 'list';
  error.value = '';
}
// 在账期页调整整份账单的提醒设置，沿用付款映射。
function changeLead(days) {
  const bills = props.book.fixedBills.map((b) =>
    b.id === selected.value ? { ...b, remindDays: days } : b,
  );
  props.write({ ...props.book, fixedBills: bills });
}
if (props.openBill) {
  const item = periods.value.find((p) => p.bill.id === props.openBill);
  if (item) openPeriod(item);
}
</script>
<template>
  <header class="business-header">
    <button aria-label="返回" @click="back"><VanIcon name="arrow-left" /></button>
    <h1>
      {{
        {
          list: billId ? '房租账期' : '固定账单',
          plans: '账单计划',
          edit: '修改后续账期',
          create: '新增固定账单',
          pay: '本期账单',
          link: '关联已有流水',
        }[page]
      }}
    </h1>
    <button v-if="page === 'list' && writable && !billId" @click="create">新增</button>
  </header>
  <template v-if="page === 'list'">
    <VanCell
      v-if="writable && !billId && plans.length"
      title="管理账单计划"
      is-link
      @click="page = 'plans'"
    />
    <div class="bill-month">
      <button aria-label="上个月" @click="month = shiftMonth(month, -1)">‹</button
      ><strong>{{ month.replace('-', ' 年 ') }} 月</strong
      ><button aria-label="下个月" @click="month = shiftMonth(month, 1)">›</button>
    </div>
    <div class="field-segments">
      <button
        v-for="(label, k) in { pending: '待付', paid: '已付' }"
        :key="k"
        :aria-pressed="tab === k"
        @click="tab = k"
      >
        {{ label }} · {{ periods.filter((p) => (k === 'paid' ? p.row : !p.row)).length }}
      </button>
    </div>
    <div v-if="visible.length" class="bill-list">
      <VanCell
        v-for="p in visible"
        :key="p.bill.id"
        :title="p.bill.name"
        :label="p.row ? p.row.date + ' 已付' : p.due + ' 到期'"
        :value="
          p.row
            ? '¥' + amountText(p.row.amount)
            : p.bill.amount === null
              ? '金额待定'
              : '¥' + amountText(p.bill.amount)
        "
        is-link
        @click="openPeriod(p)"
      />
    </div>
    <div v-else class="bill-empty">
      <VanIcon name="calendar-o" />
      <p>{{ tab === 'paid' ? '本月暂无已付账单' : '本月没有待付账单' }}</p>
    </div>
  </template>
  <template v-else-if="page === 'plans'"
    ><VanCell
      v-for="b in plans"
      :key="b.id"
      :title="b.name"
      :label="cycleLabels[b.cycle]"
      is-link
      @click="editPlan(b)"
  /></template>
  <VanForm
    scroll-to-error
    validate-trigger="onSubmit"
    v-else-if="page === 'create' || page === 'edit'"
    class="managed-form"
    @submit="savePlan"
  >
    <VanField
      required
      name="name"
      :rules="[
        { required: true, formatter: (v) => String(v ?? '').trim(), message: '请输入账单名称' },
      ]"
      v-model="draft.name"
      maxlength="40"
      label="账单名称"
      placeholder="如：房租"
    />
    <div class="direction-field">
      <span>周期<span class="field-mark">*</span></span>
      <div class="field-segments cycle-segments">
        <button
          v-for="(label, k) in cycleLabels"
          type="button"
          :key="k"
          :aria-pressed="draft.cycle === k"
          @click="draft.cycle = k"
        >
          {{ label }}
        </button>
      </div>
    </div>
    <DateField
      required
      name="start"
      :rules="[
        { validator: validDay, message: '请选择首次到期日' },
        {
          validator: (v) => page !== 'edit' || v >= effective,
          message: '请选择 ' + effective + ' 或之后的日期',
        },
      ]"
      v-model="draft.start"
      :label="page === 'edit' ? '调整后首期' : '首次到期日'"
    /><OptionalField
      name="amount"
      :rules="[
        { validator: (v) => !v || cents(v) > 0, message: '请输入大于零的金额，最多两位小数' },
      ]"
      v-model="money"
      label="约定金额"
      inputmode="decimal"
      placeholder="金额浮动可留空"
    />
    <SlotField
      name="category"
      :model-value="draft.category"
      :rules="[{ required: true, message: '请选择分类' }]"
      ><CategoryPicker v-model="draft.category" kind="expense" required
    /></SlotField>
    <ChoiceField v-model="account" resource="account" label="常用账户" /><SelectRow
      v-model="draft.remindDays"
      label="付款提醒"
      :options="leadOptions"
    />
    <RecordActions
      :options="[{ name: '停止后续账期', visible: page === 'edit', danger: true, run: stopPlan }]"
      ><VanButton round block type="primary" native-type="submit">保存</VanButton></RecordActions
    >
  </VanForm>
  <template v-else-if="period">
    <div class="bill-summary">
      <small>{{ month }} · {{ cycleLabels[period.bill.cycle] }}</small>
      <h2>{{ period.bill.name }}</h2>
      <span
        >{{ period.due }} 到期 · {{ period.bill.category
        }}<template v-if="lease">
          · {{ book.rentalHomes?.find((h) => h.id === lease.homeId)?.name }}</template
        ></span
      >
    </div>
    <VanForm
      scroll-to-error
      validate-trigger="onSubmit"
      v-if="page === 'pay' && writable"
      class="managed-form"
      @submit="confirm"
    >
      <VanField
        required
        name="actualAmount"
        :rules="[{ validator: (v) => cents(v) > 0, message: '请输入大于零的金额，最多两位小数' }]"
        label="实际金额"
        v-model="money"
        inputmode="decimal"
        placeholder="0.00"
      /><DateField
        required
        label="付款日期"
        name="date"
        :rules="[{ validator: validDay, message: '请选择付款日期' }]"
        v-model="date"
      />
      <SelectRow
        label="付款提醒"
        :model-value="period.bill.remindDays ?? billReminder.defaultDays"
        :options="leadOptions"
        @update:model-value="changeLead"
      />
      <ChoiceField label="付款账户" resource="account" v-model="account" />
      <VanButton type="primary" round block native-type="submit">记为已付</VanButton
      ><VanButton round block native-type="button" @click="link">关联已有流水</VanButton>
    </VanForm>
    <VanCell
      v-else-if="page === 'pay'"
      title="约定金额"
      :value="period.bill.amount === null ? '金额待定' : '¥' + amountText(period.bill.amount)"
    />
    <template v-else
      ><SlotField name="linkedRow" :model-value="linked" :error-message="error"
        ><VanSearch v-model="query" placeholder="搜索金额、分类、日期或备注" />
        <div class="bill-list">
          <VanCell
            v-for="r in candidates"
            :key="r.id"
            clickable
            :class="{ chosen: linked === r.id }"
            :title="r.note || r.category"
            :label="[r.date, r.category, r.account].filter(Boolean).join(' · ')"
            :value="'¥' + amountText(r.amount)"
            @click="
              linked = r.id;
              error = '';
            "
            ><template v-if="linked === r.id" #right-icon><VanIcon name="success" /></template
          ></VanCell>
        </div>
        <p v-if="!candidates.length" class="bill-empty">没有可关联的支出流水</p></SlotField
      ><VanButton
        class="confirm-link"
        type="primary"
        round
        block
        :disabled="!linked || !candidates.some((r) => r.id === linked)"
        @click="confirm"
        >确认关联</VanButton
      >
    </template>
  </template>
</template>
<style scoped>
.bill-month {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 10px 0 16px;
}
.bill-month button {
  background: var(--soft);
  border: 0;
  border-radius: 10px;
  width: 40px;
  height: 36px;
  color: var(--green);
  font-size: 22px;
}
.bill-list {
  margin-top: 14px;
  border-radius: 14px;
  overflow: hidden;
}
.bill-empty {
  text-align: center;
  color: var(--muted);
  padding: 38px 10px;
}
.bill-empty i {
  font-size: 32px;
  margin-bottom: 10px;
}
.bill-summary {
  background: var(--green-deep);
  color: white;
  border-radius: 16px;
  padding: 16px;
  margin-bottom: 14px;
}
.bill-summary h2 {
  margin: 5px 0 8px;
  font-size: 22px;
  overflow-wrap: anywhere;
}
.bill-summary small,
.bill-summary span {
  color: #d5e8df;
  font-size: 13px;
}
.managed-form > .van-button {
  margin-top: 12px;
}
.chosen {
  background: var(--mint);
  color: var(--green);
}
.confirm-link {
  position: sticky;
  bottom: 8px;
  margin-top: 14px;
}
.managed-form p,
[role='alert'] {
  color: var(--danger);
  font-size: 14px;
}
</style>
