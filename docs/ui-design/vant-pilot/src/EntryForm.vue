<script setup>
import OptionalField from './OptionalField.vue';
import { applyListDefaults } from './gift-options.mjs';
import SlotField from './SlotField.vue';
import { computed, ref, watch } from 'vue';
import {
  Form as VanForm,
  Button as VanButton,
  Calendar as VanCalendar,
  Field as VanField,
  Icon as VanIcon,
} from 'vant';
import { refunded, asDate, localDay, validDay } from './ledger.mjs';
import ChoiceField from './ChoiceField.vue';
import RentPeriodField from './RentPeriodField.vue';
import { paymentPeriod, rentChoices } from './fixed-bills.mjs';
import CategoryPicker from './CategoryPicker.vue';
import GiftPersonPicker from './GiftPersonPicker.vue';
import GiftReasonPicker from './GiftReasonPicker.vue';

const props = defineProps({
  row: { type: Object, default: null },
  today: { type: String, required: true },
  scope: { type: String, default: 'personal' },
  contacts: { type: Array, default: () => [] },
  // 复记时以已有字段初始化新增表单。
  repeating: Boolean,
  // 新流水可使用的启用账户。
  accounts: { type: Array, required: true },
  // 活动礼单根据收送方向与所属账本选择。
  lists: Array,
  book: Object,
  privateGift: Boolean,
});
const emit = defineEmits(['save', 'cancel']);
// 已退款的原单须保留支出方向，并覆盖所有到账金额与日期。
const linkedRefunds = computed(() => (props.repeating ? [] : (props.row?.refunds ?? [])));
const kinds = [
  { id: 'expense', label: '支出' },
  { id: 'income', label: '收入' },
];

const emerald = '#176a57';
const sheetStyle = { left: '0', right: '0', marginInline: 'auto', maxWidth: '440px' };
const kind = ref('expense'),
  amount = ref(''),
  category = ref(''),
  date = ref(''),
  account = ref(''),
  scope = ref('personal'),
  note = ref('');
const giftPerson = ref({ name: '', id: '', distinction: '', mode: 'none' });
const reason = ref('');
const personPicker = ref(null),
  categoryPicker = ref(null),
  form = ref(null);
const showCalendar = ref(false);
let lastSubmit = 0;
const giftCategory = computed(() => ['随礼', '收礼'].includes(category.value));
const giftVisible = computed(() => giftCategory.value && !props.privateGift);
// 住房支出可以选一期租金。复记从空关联开始，编辑保留原账期。
const rentPeriod = ref(null),
  rentError = ref('');
const existingPeriod = computed(() => paymentPeriod(props.book, props.row?.id));
const housing = computed(
  () => kind.value === 'expense' && category.value === '住房' && scope.value === 'personal',
);
const canChooseRent = computed(
  () => housing.value && (!existingPeriod.value || existingPeriod.value.lease),
);
const rentOptions = computed(() =>
  canChooseRent.value ? rentChoices(props.book, props.row?.id) : [],
);
watch(housing, (visible) => {
  if (!visible) {
    rentPeriod.value = null;
    rentError.value = '';
  }
});
// 新记账带入合同金额，编辑保留原流水的实际支出。
function chooseRent(item) {
  rentPeriod.value = item ? { billId: item.billId, month: item.month } : null;
  rentError.value = '';
  if (item && !props.row?.id && item.amount != null) amount.value = (item.amount / 100).toFixed(2);
}
// 保存前再次检查占用情况，过期的草稿须改选或清除。
function rentAvailable() {
  const target = rentPeriod.value;
  return (
    !target || rentOptions.value.some((o) => o.billId === target.billId && o.month === target.month)
  );
}
// 切换账本或方向时，失效的礼单自动回到日常礼单。
const listId = ref('');
const eligibleLists = computed(() =>
  props.lists.filter(
    (list) =>
      !list.archived && list.kind === kind.value && (list.scope || 'personal') === scope.value,
  ),
);
watch(eligibleLists, (items) => {
  if (!items.some((item) => item.id === listId.value)) listId.value = '';
});
const personLabel = computed(() => (kind.value === 'expense' ? '随礼对象' : '送礼人'));
const selectedDate = computed(() => asDate(date.value || props.today));
const minDate = computed(
  () =>
    new Date(
      Math.min(asDate(props.today).getFullYear() - 2, selectedDate.value.getFullYear()),
      0,
      1,
    ),
);
const maxDate = computed(() => asDate(date.value > props.today ? date.value : props.today));
const dateText = computed(() => {
  if (date.value === props.today) {
    const d = selectedDate.value;
    return '今天 · ' + (d.getMonth() + 1) + '月' + d.getDate() + '日';
  }
  return date.value;
});

watch(
  () => props.row,
  (row) => {
    kind.value = row?.kind || 'expense';
    amount.value = row ? (row.amount / 100).toFixed(2) : '';
    category.value = row?.category || '';
    date.value = row?.date || props.today;
    account.value = row?.account || '';
    scope.value = row?.scope || (props.scope === 'family' ? 'family' : 'personal');
    note.value = row?.note || '';
    const g = row?.gift;
    listId.value = g?.listId || '';
    giftPerson.value = {
      name: g?.person || '',
      id: g?.contactId || '',
      distinction: g?.distinction || '',
      mode: g?.contactId ? 'existing' : g?.person ? 'legacy' : 'none',
    };
    reason.value = g?.event || '';
    const linked = existingPeriod.value;
    rentPeriod.value = linked?.lease ? { billId: linked.bill.id, month: linked.month } : null;
    rentError.value = '';
    form.value?.resetValidation();
  },
  { immediate: true },
);

function changeKind(value) {
  if (value === kind.value) return;
  const oldGift = giftCategory.value;
  kind.value = value;
  category.value = oldGift ? (value === 'expense' ? '随礼' : '收礼') : '';
}
function setDate(day) {
  date.value = localDay(day);
  showCalendar.value = false;
}
function amountInCents(value) {
  let v = String(value).trim();
  if (v.startsWith('.')) v = '0' + v;
  const parts = v.match(/^(\d{1,8})(?:\.(\d{0,2}))?$/);
  if (!parts) return 0;
  return Number(parts[1]) * 100 + Number((parts[2] || '').padEnd(2, '0'));
}
function save() {
  const cents = amountInCents(amount.value);
  const event = reason.value;
  const prepared = giftVisible.value ? personPicker.value.prepare() : null;
  if (giftVisible.value && !prepared) return;
  const person = prepared?.contact;
  const gift =
    giftVisible.value && (person.name.trim() || event || listId.value)
      ? {
          shared: scope.value === 'family',
          listId: listId.value,
          person: person.name.trim(),
          event,
          ...(person.id ? { contactId: person.id, distinction: person.distinction } : {}),
        }
      : giftCategory.value && !giftVisible.value && !props.privateGift
        ? props.row?.gift
        : undefined;
  if (Date.now() - lastSubmit < 450) return;
  lastSubmit = Date.now();
  emit(
    'save',
    {
      kind: kind.value,
      amount: cents,
      date: date.value,
      category: category.value,
      scope: scope.value,
      note: note.value.trim(),
      account: account.value,
      ...(gift ? { gift } : {}),
    },
    prepared,
    {
      apply:
        canChooseRent.value || (props.row?.category === '住房' && !!existingPeriod.value?.lease),
      target: canChooseRent.value ? rentPeriod.value : null,
      failed: (message) => {
        rentError.value = message;
      },
    },
  );
}
// 仅在用户切换礼单时应用默认值，打开旧流水时保留实记内容。
function chooseListDefaults(list, previous) {
  const defaults = applyListDefaults(list, previous, date.value, reason.value, props.today);
  date.value = defaults.date;
  reason.value = defaults.reason;
}
</script>

<template>
  <VanForm
    scroll-to-error
    validate-trigger="onSubmit"
    ref="form"
    class="pilot-entry-form"
    novalidate
    @submit="save"
    @keydown.esc="emit('cancel')"
  >
    <div class="pilot-sheet-head pilot-entry-head">
      <h2>{{ repeating ? '再记一笔' : row ? '编辑账目' : '记一笔' }}</h2>
      <VanButton
        icon="cross"
        type="default"
        plain
        round
        aria-label="关闭记账"
        @click="emit('cancel')"
      />
    </div>
    <div class="direction-field">
      <span>收支类型<span class="field-mark" aria-hidden="true">*</span></span>
      <div class="field-segments pilot-entry-segments" aria-label="收支类型（必填）" role="group">
        <button
          v-for="item in kinds"
          :key="item.id"
          type="button"
          :disabled="!!linkedRefunds.length && item.id !== 'expense'"
          :aria-pressed="kind === item.id"
          @click="changeKind(item.id)"
        >
          {{ item.label }}
        </button>
      </div>
    </div>

    <section class="pilot-entry-amount" aria-label="金额">
      <label for="pilot-entry-amount"
        >{{ kind === 'expense' ? '支出金额' : '收入金额'
        }}<span class="field-mark" aria-hidden="true">*</span></label
      >
      <div class="pilot-entry-amount__line">
        <span aria-hidden="true">¥</span>
        <VanField
          id="pilot-entry-amount"
          v-model="amount"
          type="number"
          placeholder="0.00"
          :maxlength="11"
          :border="false"
          aria-required="true"
          name="amount"
          :rules="[
            { validator: (v) => amountInCents(v) > 0, message: '请输入大于零的金额，最多两位小数' },
            {
              validator: (v) => !linkedRefunds.length || amountInCents(v) >= refunded(row),
              message: '原付金额不能低于已退款金额',
            },
          ]"
          autocomplete="off"
        />
      </div>
    </section>

    <div class="pilot-entry-category-block">
      <SlotField
        name="category"
        :model-value="category"
        :rules="[
          {
            validator: (v) => !!v && categoryPicker?.isAvailable(v) !== false,
            message: '请选择分类',
          },
        ]"
      >
        <CategoryPicker
          ref="categoryPicker"
          v-model="category"
          :kind="kind"
          :scope="scope"
          required
        />
      </SlotField>
    </div>

    <div class="pilot-entry-meta" aria-label="账目资料">
      <SlotField
        name="date"
        :model-value="date"
        :rules="[
          { validator: validDay, message: '请选择日期' },
          {
            validator: (v) => linkedRefunds.every((r) => r.date >= v),
            message: '消费日期不能晚于退款日期',
          },
        ]"
        ><button
          class="pilot-entry-meta__row"
          type="button"
          aria-haspopup="dialog"
          @click="showCalendar = true"
        >
          <span
            >日期<span class="field-mark" aria-hidden="true">*</span
            ><span class="pilot-a11y-only">（必填）</span></span
          ><span class="picker-display"
            >{{ dateText }} <VanIcon name="arrow" aria-hidden="true"
          /></span></button
      ></SlotField>
      <ChoiceField v-model="account" resource="account" label="账户" />
      <OptionalField
        v-if="!privateGift"
        id="pilot-entry-note"
        v-model="note"
        label="备注"
        maxlength="100"
        input-align="left"
        :border="false"
      />
    </div>

    <section
      v-if="canChooseRent || giftVisible"
      class="pilot-entry-business"
      :aria-label="canChooseRent ? '租房账单' : '往来明细'"
    >
      <SlotField
        v-if="canChooseRent"
        class="rent-field"
        name="rentPeriod"
        :model-value="rentPeriod?.month || ''"
        :error-message="rentError"
        :rules="[{ validator: rentAvailable, message: '该账期已变更，请重新选择' }]"
      >
        <RentPeriodField
          :has-plan="!!book.leases?.some((l) => l.billId)"
          :options="rentOptions"
          :selected="rentPeriod"
          :date="date"
          @choose="chooseRent"
        />
      </SlotField>
      <template v-else>
        <ChoiceField
          v-model="listId"
          @list-defaults="chooseListDefaults"
          resource="list"
          label="所属礼单"
          :kind="kind"
          :scope="scope"
          :day="date"
        />
        <GiftPersonPicker
          ref="personPicker"
          v-model="giftPerson"
          :label="personLabel"
          :contacts="contacts"
        />
        <SlotField
          name="reason"
          :model-value="reason"
          :rules="[{ validator: (v) => v.length <= 60, message: '事由最多60字' }]"
          ><GiftReasonPicker class="entry-reasons" v-model="reason"
        /></SlotField>
      </template>
    </section>

    <div class="pilot-entry-footer">
      <VanButton class="pilot-entry-submit" type="primary" native-type="submit" block>{{
        row && !repeating ? '保存修改' : '保存账目'
      }}</VanButton>
    </div>

    <VanCalendar
      v-model:show="showCalendar"
      teleport="body"
      :style="sheetStyle"
      type="single"
      title="选择日期"
      confirm-text="确认日期"
      :default-date="selectedDate"
      :min-date="minDate"
      :max-date="maxDate"
      :show-mark="false"
      first-day-of-week="1"
      switch-mode="year-month"
      :color="emerald"
      @confirm="setDate"
    />
  </VanForm>
</template>

<style scoped>
.pilot-entry-form {
  --entry-accent: var(--green);
  --entry-ink: var(--ink);
  --entry-muted: var(--muted);
  --entry-rule: var(--line);
  --entry-danger: var(--danger);
  color: var(--entry-ink);
  display: grid;
  gap: 16px;
  min-width: 0;
}
.pilot-entry-head {
  margin: 0;
}
.pilot-entry-head :deep(.van-button) {
  min-width: 44px;
  min-height: 44px;
}
.pilot-entry-amount {
  min-width: 0;
  padding: 17px 13px 12px;
  background: #fff;
  border: 1px solid var(--entry-rule);
  border-radius: 16px;
}
.pilot-entry-amount label {
  font-size: 14px;
  font-weight: 400;
  color: var(--entry-ink);
  display: block;
  margin-bottom: 4px;
}
.pilot-entry-amount__line {
  display: flex;
  align-items: baseline;
  gap: 6px;
  line-height: 1.2;
}
.pilot-entry-amount__line > span {
  font-size: 1.35rem;
  font-weight: 560;
  color: var(--entry-accent);
}
.pilot-entry-amount :deep(.van-field) {
  flex: 1;
  min-width: 0;
  padding: 0;
  background: transparent;
}
.pilot-entry-amount :deep(.van-field__control) {
  font-size: clamp(2.1rem, 9vw, 2.8rem);
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  line-height: 1.2;
  letter-spacing: -0.05em;
  color: var(--entry-ink);
  min-height: 52px;
}
.pilot-entry-amount :deep(.van-field__control::placeholder) {
  color: #a3afac;
}
.pilot-entry-category-block {
  min-width: 0;
}
.pilot-entry-meta,
.pilot-entry-business {
  background: white;
  border: 1px solid var(--entry-rule);
  border-radius: 14px;
  min-width: 0;
}
.pilot-entry-meta__row {
  width: 100%;
  min-height: 46px;
  display: grid;
  grid-template-columns: var(--field-label) minmax(0, 1fr);
  align-items: center;
  gap: var(--field-gap);
  text-align: left;
  padding: 9px 13px;
  border: 0;
  border-bottom: 1px solid var(--entry-rule);
  color: var(--entry-ink);
  background: transparent;
  font: inherit;
  font-size: 14px;
}
.pilot-entry-empty {
  color: var(--entry-muted);
}
.pilot-entry-meta :deep(.van-field:not(.slot-field)),
.pilot-entry-business :deep(.van-field:not(.slot-field)) {
  padding: 11px 13px;
  background: transparent;
  border-radius: 14px;
  color: inherit;
}
.pilot-entry-meta :deep(.van-field__label),
.pilot-entry-business :deep(.van-field__label) {
  color: inherit;
}
.pilot-entry-meta :deep(input::placeholder),
.pilot-entry-business :deep(input::placeholder) {
  color: var(--entry-muted);
}
.pilot-entry-meta :deep(.choice-trigger),
.pilot-entry-business :deep(.choice-trigger) {
  padding-inline: 13px;
}
.pilot-entry-footer {
  padding: 0 0 6px;
}
.pilot-entry-submit {
  --van-button-primary-background: var(--entry-accent);
  --van-button-primary-border-color: var(--entry-accent);
  min-height: 46px;
  border-radius: var(--button-radius);
  font-weight: 640;
}

.pilot-a11y-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
}
.rent-field :deep(.rent-trigger) {
  border-bottom: 0;
}
.rent-field :deep(.van-field__error-message) {
  padding: 0 13px 8px calc(13px + var(--field-label) + var(--field-gap));
}
.entry-reasons {
  margin-inline: 13px;
}
.pilot-entry-form button:focus-visible,
.pilot-entry-form summary:focus-visible {
  outline: 2px solid var(--entry-accent);
  outline-offset: 2px;
}
.pilot-entry-meta__row:first-child {
  border-radius: 13px 13px 0 0;
}
@media (max-width: 360px) {
  .pilot-entry-form {
    gap: 12px;
  }
  .pilot-entry-amount {
    padding: 13px;
  }
}
.pilot-entry-meta__row select {
  max-width: 65%;
  border: 0;
  background: white;
  color: inherit;
  font: inherit;
}
</style>
