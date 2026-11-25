<script setup>
import OptionalField from './OptionalField.vue';
import DateField from './DateField.vue';
import { computed, inject, ref, watch } from 'vue';
import {
  Button as VanButton,
  Cell as VanCell,
  Field as VanField,
  Form as VanForm,
  Icon as VanIcon,
  showConfirmDialog,
  showToast,
} from 'vant';
import { amountText, validDay } from './ledger.mjs';
import { rentCents, storeDeposit, storeLease } from './rentals.mjs';
import GiftPersonPicker from './GiftPersonPicker.vue';
import ChoiceField from './ChoiceField.vue';
import { leaseReminder } from './reminder-sources.mjs';
import { reminderOptions } from './reminders.mjs';
import SelectRow from './SelectRow.vue';
import { cycleLabels, cycleMonths, dueDate, shiftMonth } from './fixed-bills.mjs';
import FixedBills from './FixedBills.vue';
const props = defineProps({
  book: Object,
  contacts: Array,
  today: String,
  actor: String,
  initialLease: String,
  write: Function,
});
const leadOptions = reminderOptions(leaseReminder);
const emit = defineEmits(['close', 'row']);
const writable = inject('bookWritable');
// 房屋和合同是独立资料；从房屋进入时自动带入房屋。
const page = ref('homes'),
  origin = ref('homes'),
  homeId = ref(''),
  leaseId = ref('');
const draft = ref({}),
  rent = ref(''),
  pledge = ref(''),
  mode = ref('none'),
  planId = ref(''),
  due = ref(''),
  error = ref('');
// 签约名称独立保存；联系人编号用于跨业务复用同一人。
const party = ref({}),
  partyPicker = ref(null);
const homes = computed(() => props.book.rentalHomes || []),
  leases = computed(() => props.book.leases || []);
const home = computed(() => homes.value.find((h) => h.id === homeId.value));
const lease = computed(() => leases.value.find((l) => l.id === leaseId.value));
const homeLeases = computed(() =>
  leases.value
    .filter((l) => l.homeId === homeId.value)
    .sort((a, b) => b.start.localeCompare(a.start)),
);
const deposits = computed(() =>
  (props.book.depositEntries || [])
    .filter((d) => d.leaseId === leaseId.value)
    .sort((a, b) => b.date.localeCompare(a.date)),
);
const held = computed(() =>
  deposits.value.reduce((n, d) => n + (d.kind === 'pay' ? d.amount : -d.amount), 0),
);
const available = computed(() =>
  (props.book.fixedBills || []).filter(
    (b) =>
      !b.stopMonth &&
      b.cycle === (draft.value.cycle ?? 'month') &&
      !leases.value.some((l) => l.billId === b.id),
  ),
);
const plan = computed(() => props.book.fixedBills?.find((b) => b.id === lease.value?.billId));
const monthsStart = computed(() => {
  const start = [lease.value.start, plan.value.start].sort().at(-1);
  const end = lease.value.end;
  const first = (props.today < start ? start : props.today > end ? end : props.today).slice(0, 7);
  // 非付款月优先展示下一期；租期尾部回到最近一期。
  for (const direction of [1, -1]) {
    for (let offset = 0; offset <= cycleMonths[plan.value.cycle]; offset++) {
      const month = shiftMonth(first, direction * offset),
        due = dueDate(plan.value, month);
      if (due && due >= start && due <= end) return month;
    }
  }
  return first;
});
const money = (c) => '¥' + amountText(c);
const homeName = (id) => homes.value.find((h) => h.id === id)?.name || '';
const status = (l) =>
  props.today < l.start ? '待入住' : props.today > l.end ? '已到期' : '租期中';
const title = computed(
  () =>
    ({
      homes: '租房',
      leases: '租房',
      home: home.value?.name,
      lease: '租房合同',
      houseForm: draft.value.id ? '编辑房屋' : '新增房屋',
      leaseForm: draft.value.id ? '编辑合同' : '新增合同',
      deposit: draft.value.kind === 'pay' ? '登记交押金' : '登记退押金',
    })[page.value],
);
// 初始化草稿；取消或校验失败都不会改动账本。
function houseForm(edit = false) {
  draft.value = edit ? { ...home.value } : { name: '', address: '', note: '' };
  error.value = '';
  page.value = 'houseForm';
}
function saveHome() {
  const h = {
    ...draft.value,
    name: draft.value.name.trim(),
    id: draft.value.id || crypto.randomUUID(),
  };
  if (
    props.write({ ...props.book, rentalHomes: [...homes.value.filter((v) => v.id !== h.id), h] })
  ) {
    homeId.value = h.id;
    page.value = 'home';
  }
}
function openLease(l) {
  origin.value = page.value;
  leaseId.value = l.id;
  homeId.value = l.homeId;
  page.value = 'lease';
}
function leaseForm(edit = false) {
  if (!homes.value.length) {
    showToast('先新增一套房屋，再登记合同');
    page.value = 'homes';
    houseForm();
    return;
  }
  if (!edit) origin.value = page.value;
  const l = edit
    ? lease.value
    : {
        homeId: page.value === 'home' ? homeId.value : homes.value[0].id,
        party: '',
        start: props.today,
        end: '',
        note: '',
      };
  party.value = {
    name: l.party,
    id: l.partyId || '',
    distinction: l.partyDistinction || '',
    mode: l.partyId ? 'existing' : l.party ? 'typed' : 'none',
  };
  draft.value = {
    ...l,
    remindDays: l.remindDays ?? leaseReminder.defaultDays,
    cycle: l.cycle ?? 'month',
  };
  rent.value = l.rent ? String(l.rent / 100) : '';
  pledge.value = l.deposit ? String(l.deposit / 100) : '';
  mode.value = l.billId ? 'bound' : 'none';
  planId.value = l.billId || '';
  due.value = l.start;
  error.value = '';
  page.value = 'leaseForm';
}
// 首次付款日尚未单独修改时，跟随起租日期。
watch(
  () => draft.value.start,
  (next, prev) => {
    if (next && due.value === prev) due.value = next;
  },
);
// 合同与押金共用金额精度，字段错误由 Vant 随表单提交定位。
const requiredRule = (message) => ({ required: true, message });
const positiveMoney = {
  validator: (value) => rentCents(value) > 0,
  message: '请输入大于零的金额，最多两位小数',
};
const contractIssue = ref(null);
watch(
  [draft, mode, planId, party, rent, pledge, due],
  () => {
    contractIssue.value = null;
  },
  { deep: true },
);
async function saveContract() {
  const l = {
    ...draft.value,
    id: draft.value.id || crypto.randomUUID(),
    party: party.value.name.trim(),
    rent: rentCents(rent.value),
    deposit: pledge.value === '' ? 0 : rentCents(pledge.value),
  };
  const result = storeLease(props.book, l, mode.value, planId.value, due.value);
  if (result.error) {
    contractIssue.value = result;
    return;
  }
  if (
    mode.value === 'create' &&
    available.value.some((b) => b.category === '住房' || b.name.includes('房租'))
  ) {
    try {
      await showConfirmDialog({
        title: '已有可关联的房租账单',
        message: '如果是同一笔房租，返回选择「关联已有固定账单」即可保留原有付款记录。',
        confirmButtonText: '另建一份',
        cancelButtonText: '返回选择',
      });
    } catch {
      mode.value = 'link';
      return;
    }
  }
  const prepared = partyPicker.value.prepare();
  if (!prepared) return;
  const contracts = result.book.leases.map((item) =>
    item.id === l.id
      ? { ...item, partyId: prepared.contact.id, partyDistinction: prepared.contact.distinction }
      : item,
  );
  if (props.write({ ...result.book, leases: contracts }, prepared)) {
    leaseId.value = l.id;
    homeId.value = l.homeId;
    page.value = 'lease';
  }
}
// 押金只在实际交付或退回时影响账户现金，合同约定本身不产生流水。
function depositForm(kind, entry) {
  draft.value = entry
    ? { ...entry }
    : { leaseId: leaseId.value, kind, date: props.today, account: '', note: '' };
  const amount =
    entry?.amount ??
    (kind === 'refund' ? held.value : Math.max(0, lease.value.deposit - held.value));
  pledge.value = amount ? String(amount / 100) : '';
  error.value = '';
  page.value = 'deposit';
}
function saveDeposit() {
  const entry = {
    ...draft.value,
    id: draft.value.id || crypto.randomUUID(),
    amount: rentCents(pledge.value),
  };
  const result = storeDeposit(props.book, entry);
  if (result.error) {
    error.value = result.error;
    return;
  }
  if (props.write(result.book)) page.value = 'lease';
}
function back() {
  const dest = {
    home: 'homes',
    lease: origin.value,
    houseForm: draft.value.id ? 'home' : 'homes',
    leaseForm: draft.value.id ? 'lease' : origin.value,
    deposit: 'lease',
    periods: 'lease',
  };
  if (dest[page.value]) page.value = dest[page.value];
  else emit('close');
  error.value = '';
}
// 处理标记绑定到期日，修改到期日后按新的租期重新提醒。
function handleExpiry() {
  const current = lease.value;
  const next = leases.value.map((l) => (l.id === current.id ? { ...l, handledEnd: l.end } : l));
  if (props.write({ ...props.book, leases: next })) {
    showToast('已处理本次到期提醒');
    back();
  }
}
if (props.initialLease) {
  const initial = leases.value.find((l) => l.id === props.initialLease);
  if (initial) {
    openLease(initial);
    origin.value = '';
  }
}
</script>
<template>
  <FixedBills
    v-if="page === 'periods'"
    :book="book"
    :write="write"
    :today="today"
    :actor="actor"
    :bill-id="lease.billId"
    :initial-month="monthsStart"
    @back="back"
    @row="emit('row', $event)"
  />
  <template v-else
    ><header class="business-header">
      <button aria-label="返回" @click="back"><VanIcon name="arrow-left" /></button>
      <h1>{{ title }}</h1>
      <button
        v-if="writable && ['homes', 'leases', 'home', 'lease'].includes(page)"
        @click="
          page === 'homes'
            ? houseForm()
            : page === 'leases'
              ? leaseForm()
              : page === 'home'
                ? houseForm(true)
                : leaseForm(true)
        "
      >
        {{ ['home', 'lease'].includes(page) ? '编辑' : '新增' }}
      </button>
    </header>
    <template v-if="['homes', 'leases'].includes(page)">
      <div class="field-segments">
        <button
          v-for="(label, k) in { homes: '房屋', leases: '合同' }"
          :key="k"
          :aria-pressed="page === k"
          @click="page = k"
        >
          {{ label }}
        </button>
      </div>
      <section v-if="page === 'homes'" class="rent-list">
        <VanCell
          v-for="h in homes"
          :key="h.id"
          icon="wap-home-o"
          :title="h.name"
          :label="h.address"
          :value="leases.filter((l) => l.homeId === h.id).length + ' 份合同'"
          is-link
          @click="
            homeId = h.id;
            page = 'home';
          "
        />
        <p v-if="!homes.length">暂无租住房屋</p>
      </section>
      <section v-else class="rent-list">
        <VanCell
          v-for="l in leases"
          :key="l.id"
          :title="homeName(l.homeId)"
          :label="l.party + ' · ' + l.start + ' 至 ' + l.end"
          :value="status(l)"
          is-link
          @click="openLease(l)"
        />
        <p v-if="!leases.length">暂无租房合同</p>
      </section>
    </template>
    <template v-if="page === 'home' && home"
      ><VanCell v-if="home.address" title="地址" :value="home.address" /><VanCell
        v-if="home.note"
        title="备注"
        :value="home.note"
      />
      <div class="rent-heading">
        <h2>租房合同</h2>
        <button v-if="writable" @click="leaseForm()">新增合同</button>
      </div>
      <VanCell
        v-for="l in homeLeases"
        :key="l.id"
        :title="l.party"
        :label="l.start + ' 至 ' + l.end"
        :value="status(l)"
        is-link
        @click="openLease(l)"
      />
      <p v-if="!homeLeases.length" class="rent-empty">这套房屋还没有合同</p></template
    >
    <VanForm
      scroll-to-error
      validate-trigger="onSubmit"
      v-if="page === 'houseForm'"
      class="managed-form"
      @submit="saveHome"
    >
      <VanField
        v-model="draft.name"
        label="房屋名称"
        name="homeName"
        :rules="[
          { validator: (v) => !!v.trim(), message: '请输入房屋名称' },
          {
            validator: (v) => !homes.some((h) => h.id !== draft.id && h.name === v.trim()),
            message: '已有同名房屋',
          },
        ]"
        required
        maxlength="40"
        placeholder="如：朝阳路公寓"
      /><OptionalField v-model="draft.address" label="地址" maxlength="120" /><OptionalField
        v-model="draft.note"
        label="备注"
        maxlength="100"
      />
      <VanButton block round type="primary" native-type="submit">保存</VanButton></VanForm
    >
    <VanForm
      scroll-to-error
      validate-trigger="onSubmit"
      v-if="page === 'leaseForm'"
      class="managed-form"
      @submit="saveContract"
    >
      <VanField
        class="compound-field"
        name="homeId"
        :model-value="draft.homeId"
        :rules="[requiredRule('请选择房屋')]"
        ><template #input
          ><SelectRow
            label="房屋"
            v-model="draft.homeId"
            required
            :readonly="!!draft.id"
            :options="homes.map((h) => ({ value: h.id, label: h.name }))" /></template
      ></VanField>
      <GiftPersonPicker
        ref="partyPicker"
        v-model="party"
        label="出租方"
        :contacts="contacts"
        rental
        required
      /><DateField
        required
        label="起租日"
        name="start"
        :rules="[{ validator: validDay, message: '请选择起租日' }]"
        v-model="draft.start"
      /><DateField
        required
        label="到期日"
        name="end"
        :error-message="contractIssue?.field === 'end' ? contractIssue.error : ''"
        :rules="[
          { validator: validDay, message: '请选择到期日' },
          { validator: (v) => v >= draft.start, message: '到期日不能早于起租日' },
        ]"
        v-model="draft.end"
      />
      <VanField
        required
        label="月租（元）"
        name="rent"
        :rules="[requiredRule('请输入月租'), positiveMoney]"
        v-model="rent"
        type="number"
      /><OptionalField
        label="押金（元）"
        name="pledge"
        :rules="[
          { validator: (v) => !v || rentCents(v) >= 0, message: '请输入非负金额，最多两位小数' },
        ]"
        v-model="pledge"
        type="number"
      />
      <div class="direction-field" v-if="mode !== 'bound'">
        <span>付款周期<span class="field-mark">*</span></span>
        <div class="field-segments cycle-segments" role="group" aria-label="付款周期">
          <button
            v-for="(label, k) in cycleLabels"
            type="button"
            :key="k"
            :aria-pressed="draft.cycle === k"
            @click="
              draft.cycle = k;
              planId = '';
              mode = mode === 'link' ? 'none' : mode;
            "
          >
            {{ label }}
          </button>
        </div>
      </div>
      <VanField v-else label="付款周期" :model-value="cycleLabels[draft.cycle]" readonly />
      <VanField
        v-if="rentCents(rent) > 0"
        label="每期应付"
        :model-value="money(rentCents(rent) * cycleMonths[draft.cycle])"
        readonly
      />
      <VanField
        v-if="mode === 'bound'"
        label="房租账单"
        :error-message="contractIssue?.field === 'planId' ? contractIssue.error : ''"
        :model-value="plan?.name"
        readonly
      />
      <template v-else
        ><SelectRow
          v-model="mode"
          label="房租账单"
          :options="[
            { value: 'none', label: '暂不设置' },
            { value: 'link', label: '关联已有固定账单', disabled: !available.length },
            { value: 'create', label: '生成固定账单' },
          ]"
        />
        <DateField
          v-if="mode === 'create'"
          required
          label="首次付款日"
          name="due"
          :rules="[
            { validator: validDay, message: '请选择首次付款日' },
            {
              validator: (v) => v >= draft.start && v <= draft.end,
              message: '首次付款日需在合同租期内',
            },
          ]"
          v-model="due"
        />
        <VanField
          v-if="mode === 'link'"
          class="compound-field"
          name="planId"
          :model-value="planId"
          :rules="[requiredRule('请选择要关联的账单')]"
          :error-message="contractIssue?.field === 'planId' ? contractIssue.error : ''"
          ><template #input
            ><SelectRow
              label="选择账单"
              required
              v-model="planId"
              :options="
                available.map((b) => ({
                  value: b.id,
                  label: b.name + ' · ' + cycleLabels[b.cycle] + ' · 首期 ' + b.start,
                }))
              " /></template
        ></VanField>
        <small v-if="mode === 'link'"
          >沿用所选账单的付款日和已付记录，待付金额按月租和付款周期计算。</small
        ></template
      >
      <SelectRow label="到期提醒" v-model="draft.remindDays" :options="leadOptions" /><OptionalField
        label="备注"
        v-model="draft.note"
        maxlength="100"
      /><VanButton block round type="primary" native-type="submit">保存合同</VanButton></VanForm
    >
    <template v-if="page === 'lease' && lease">
      <div class="rent-summary">
        <small>{{ status(lease) }} · {{ homeName(lease.homeId) }}</small>
        <h2>{{ money(lease.rent) }} <small>/ 月</small></h2>
        <span>{{ lease.start }} 至 {{ lease.end }}</span>
      </div>
      <VanCell
        v-if="initialLease && writable && lease.handledEnd !== lease.end"
        title="标记到期提醒为已处理"
        is-link
        @click="handleExpiry" />
      <VanCell
        title="付款约定"
        :value="
          cycleLabels[lease.cycle ?? 'month'] +
          ' · 每期 ' +
          money(lease.rent * cycleMonths[lease.cycle ?? 'month'])
        " /><VanCell
        title="出租方"
        :value="[lease.party, lease.partyDistinction].filter(Boolean).join(' · ')" /><VanCell
        v-if="lease.note"
        title="备注"
        :value="lease.note" />
      <VanCell
        v-if="plan"
        title="房租账期"
        :value="plan.name"
        is-link
        @click="page = 'periods'" /><VanCell
        v-else-if="writable"
        title="设置房租账单"
        is-link
        @click="leaseForm(true)" />
      <div class="rent-heading">
        <h2>押金</h2>
        <span>约定 {{ money(lease.deposit) }}</span>
      </div>
      <VanCell title="待退押金" :value="money(held)" />
      <div v-if="writable" class="rent-actions">
        <VanButton round @click="depositForm('pay')">登记交付</VanButton
        ><VanButton round :disabled="held <= 0" @click="depositForm('refund')">登记退回</VanButton>
      </div>
      <VanCell
        v-for="d in deposits"
        :key="d.id"
        :title="d.kind === 'pay' ? '交付押金' : '退回押金'"
        :label="[d.date, d.account, d.note].filter(Boolean).join(' · ')"
        :value="money(d.amount)"
        :is-link="writable"
        @click="writable && depositForm(d.kind, d)"
    /></template>
    <VanForm scroll-to-error v-if="page === 'deposit'" class="managed-form" @submit="saveDeposit"
      ><VanField :model-value="homeName(lease.homeId)" label="房屋" readonly />
      <VanField
        required
        label="金额（元）"
        name="amount"
        :rules="[positiveMoney]"
        :error-message="error"
        v-model="pledge"
        @update:model-value="error = ''"
        type="number"
      /><DateField
        required
        label="日期"
        name="date"
        :rules="[{ validator: validDay, message: '请选择日期' }]"
        @update:model-value="error = ''"
        v-model="draft.date"
      /><ChoiceField v-model="draft.account" resource="account" label="账户" /><OptionalField
        v-model="draft.note"
        label="备注"
        maxlength="100"
      />
      <VanButton type="primary" round block native-type="submit">保存</VanButton></VanForm
    >
  </template>
</template>
<style scoped>
.rent-summary {
  padding: 18px;
  background: var(--green-deep);
  color: white;
  border-radius: 16px;
  margin-bottom: 14px;
}
.rent-summary h2 {
  font-size: 26px;
  margin: 6px 0;
}
.rent-summary small,
.rent-summary span {
  font-size: 13px;
  color: #d4e6db;
  font-weight: 400;
}
.rent-heading,
.rent-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin: 15px 0 8px;
}
.rent-heading h2 {
  font-size: 16px;
}
.rent-heading span,
form small {
  font-size: 13px;
  color: var(--muted);
}
.rent-heading button {
  border: 0;
  background: transparent;
  color: var(--green);
  padding: 8px;
}
.rent-actions > * {
  flex: 1;
}
.rent-list {
  margin-top: 14px;
  border-radius: 14px;
  overflow: hidden;
}
.rent-list p,
.rent-empty {
  text-align: center;
  color: var(--muted);
  padding: 24px;
}
.managed-form .native-field-row {
  padding-inline: 0;
}
.managed-form > .van-button {
  margin-top: 12px;
}
[role='alert'] {
  color: var(--danger);
}
.managed-form :deep(.pilot-entry-contact) {
  padding-inline: 0;
  --entry-rule: var(--line);
  --entry-ink: var(--ink);
  --entry-muted: var(--muted);
  --entry-accent: var(--green);
  --entry-danger: var(--danger);
}
.managed-form .compound-field {
  padding: 0;
  overflow: visible;
}
.compound-field :deep(.van-cell__value) {
  overflow: visible;
}
.compound-field :deep(.van-field__control--custom) {
  display: block;
}
.compound-field :deep(.pilot-entry-contact),
.compound-field :deep(.form-picker-row) {
  border-bottom: 0;
}
.compound-field :deep(.van-field__error-message) {
  margin-left: calc(var(--field-label) + var(--field-gap));
  padding-bottom: 5px;
}
</style>
