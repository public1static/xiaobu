<script setup>
import RecordActions from './RecordActions.vue';
import OptionalField from './OptionalField.vue';
import SlotField from './SlotField.vue';
import DateField from './DateField.vue';
import { inject, computed, ref } from 'vue';
import {
  Form as VanForm,
  showToast,
  Button as VanButton,
  Cell as VanCell,
  Field as VanField,
  showConfirmDialog,
  Search as VanSearch,
  Icon as VanIcon,
  Switch as VanSwitch,
} from 'vant';
import {
  changeAccount,
  accountNameError,
  accountBalances,
  openingForCurrent,
  saveTransfer,
} from './accounts.mjs';
import { typeGroups, symbols, palette, displayType, groupAccounts } from './account-types.mjs';
import ChoiceField from './ChoiceField.vue';
import {
  cashRows,
  validDay,
  localDay,
  amountSigned,
  amountText,
  select,
  totals,
} from './ledger.mjs';
const writable = inject('bookWritable');

const props = defineProps({
  book: Object,
  accounts: Array,
  scope: String,
  write: Function,
  picking: Boolean,
  chosen: String,
  exclude: String,
  required: Boolean,
  label: String,
});
const emit = defineEmits(['close', 'row', 'choose']);
// 关闭流水详情后仍停留在原账户。
const page = ref('list'),
  selected = ref('');
// 编辑草稿在点保存后生效。
const name = ref(''),
  active = ref(true),
  error = ref('');
// 独立切换明细范围，余额始终按全部账本计算。
const range = ref(props.scope);
const rows = computed(() =>
  select(
    cashRows(props.book.rows).filter((row) => row.account === selected.value),
    { scope: range.value },
  ),
);
const summary = computed(() => totals(rows.value));
// 余额跨账本核算，列表明细沿用首页的账本范围。
const balances = computed(() => accountBalances(props.book));
const currentAmount = ref(''),
  type = ref('其他'),
  transfer = ref({}),
  transferAmount = ref('');
// 管理列表和选择器沿用同一账户目录。
const search = ref('');
const groupedAccounts = computed(() =>
  groupAccounts(props.accounts)
    .map(([group, items]) => [
      group,
      items.filter(
        (a) =>
          !props.picking ||
          ((a.active || a.name === props.chosen) &&
            a.name !== props.exclude &&
            [a.name, displayType(a), group].some((text) =>
              text.toLocaleLowerCase().includes(search.value.trim().toLocaleLowerCase()),
            )),
      ),
    ])
    .filter(([, items]) => items.length),
);
// 同一行在管理页打开详情，在选择页将账户带回原表单。
function openAccount(account) {
  if (props.picking) {
    emit('choose', account.name);
    return;
  }
  selected.value = account.name;
  page.value = 'detail';
}
const credit = (t) => typeGroups['信用消费'].includes(t);
const investment = (t) => typeGroups['投资理财'].includes(t);
const amountTitle = (t) => (credit(t) ? '当前待还' : investment(t) ? '当前市值' : '当前余额');
const overpaid = ref(false);
const amountHint = computed(() =>
  credit(type.value)
    ? '填账单上尚未还清的总金额'
    : investment(type.value)
      ? '填持仓页面显示的总市值'
      : '填现在能查到的账户余额',
);
// 新建产品账户时带入常用名称，允许自行区分同类型账户。
function chooseType(value) {
  const suggest =
    !selected.value &&
    (!name.value || name.value === type.value || props.accounts.some((a) => a.name === name.value));
  type.value = value;
  if (suggest)
    name.value = value === '其他' || props.accounts.some((a) => a.name === value) ? '' : value;
  page.value = 'edit';
}

const current = computed(() => props.accounts.find((a) => a.name === selected.value));
const history = computed(() => {
  const entries = rows.value.map((row) => ({
    id: row.id,
    date: row.date,
    title: row.category + (row.originalId ? '退款' : ''),
    value: amountSigned(row),
    row,
  }));
  for (const t of props.book.transfers ?? []) {
    if (range.value !== 'all' && range.value !== t.scope) continue;
    if (t.from !== selected.value && t.to !== selected.value) continue;
    const sent = t.from === selected.value;
    entries.push({
      id: t.id,
      date: t.date,
      title: sent ? '转至 ' + t.to : '转自 ' + t.from,
      value: (sent ? '−' : '＋') + '¥' + amountText(t.amount),
      transfer: t,
    });
  }
  for (const d of props.book.depositEntries || []) {
    if (d.account !== selected.value || range.value === 'family') continue;
    const lease = props.book.leases?.find((l) => l.id === d.leaseId);
    const house = props.book.rentalHomes?.find((h) => h.id === lease?.homeId);
    entries.push({
      id: d.id,
      date: d.date,
      title: [d.kind === 'pay' ? '交付押金' : '退回押金', house?.name].filter(Boolean).join(' · '),
      value: (d.kind === 'pay' ? '−' : '＋') + '¥' + amountText(d.amount),
      deposit: true,
    });
  }
  return entries.sort((a, b) => b.date.localeCompare(a.date));
});
// 显示尚未录入期初余额的旧账户。
function balanceLabel(a) {
  const value = balances.value.get(a),
    t = props.accounts.find((item) => item.name === a)?.type;
  if (value == null) return '待填' + amountTitle(t);
  if (credit(t)) return (value > 0 ? '溢缴款 ' : '待还 ') + '¥' + amountText(Math.abs(value));
  return '¥' + amountText(value);
}
// 转账草稿只在提交时写入；修改保留原来的记录编号。
function openTransfer(t, incoming = false) {
  transfer.value = t
    ? { ...t }
    : {
        id: crypto.randomUUID(),
        from: incoming ? '' : selected.value,
        to: incoming ? selected.value : '',
        date: localDay(new Date()),
        scope: props.scope === 'family' ? 'family' : 'personal',
        note: '',
      };
  transferAmount.value = t ? (t.amount / 100).toFixed(2) : '';
  error.value = '';
  page.value = 'transfer';
}
// 金额在边界转换成整数分，拒绝多余的小数位。
function cents(text) {
  return /^-?\d{1,10}(?:\.\d{1,2})?$/.test(text) ? Math.round(Number(text) * 100) : NaN;
}
// 成功写入后返回发起操作的账户。
function availableSide(side, value) {
  const previous = props.book.transfers?.find((t) => t.id === transfer.value.id);
  return (
    props.accounts.some((a) => a.name === value && (a.active || previous?.[side] === value)) &&
    (side === 'from' || value !== transfer.value.from)
  );
}
// 表单校验完成后一次写入转出与转入账户。
function postTransfer() {
  const result = saveTransfer(props.book, {
    ...transfer.value,
    amount: cents(transferAmount.value),
  });
  if (result.error) {
    showToast(result.error);
    return;
  }
  if (props.write(result.book)) page.value = 'detail';
}
// 撤销整笔转账，一次写入恢复两个账户的余额。
async function cancelTransfer() {
  try {
    await showConfirmDialog({ title: '撤销转账？', message: '两个账户会同步撤回这笔金额。' });
  } catch {
    return;
  }
  if (
    props.write({
      ...props.book,
      transfers: props.book.transfers.filter((t) => t.id !== transfer.value.id),
    })
  )
    page.value = 'detail';
}
// 从详情编辑已有账户，从列表新增账户。
function edit(original = '') {
  selected.value = original;
  name.value = original;
  active.value = props.accounts.find((a) => a.name === original)?.active ?? true;
  const a = props.accounts.find((a) => a.name === original);
  type.value = a ? displayType(a) : '其他';
  const value = balances.value.get(original);
  overpaid.value = credit(type.value) && value > 0;
  currentAmount.value =
    value == null ? '' : ((credit(type.value) ? Math.abs(value) : value) / 100).toFixed(2);
  error.value = '';
  page.value = original ? 'edit' : 'catalog';
}
// 退出表单时丢弃草稿。
function back() {
  if (page.value === 'list') emit('close');
  else if (page.value === 'catalog')
    page.value = selected.value || name.value || currentAmount.value ? 'edit' : 'list';
  else
    page.value =
      page.value === 'transfer' || (page.value === 'edit' && selected.value) ? 'detail' : 'list';
}
// 改名后用新名称继续展示相同账户的流水。
function save() {
  const value = currentAmount.value.trim() ? cents(currentAmount.value.trim()) : null;
  const signed = value != null && credit(type.value) && !overpaid.value ? -value : value;
  const result = changeAccount(props.book, selected.value, name.value, active.value, {
    type: type.value,
    opening: openingForCurrent(props.book, selected.value, signed),
  });
  if (result.error) {
    if (result.field === 'balance') error.value = result.error;
    else showToast(result.error);
    return;
  }
  if (props.write(result.book)) {
    if (props.picking) {
      emit('choose', name.value.trim());
      return;
    }
    selected.value = name.value.trim();
    page.value = 'detail';
  }
}
</script>
<template>
  <header class="business-header">
    <button aria-label="返回" @click="back"><VanIcon name="arrow-left" /></button>
    <h1>
      {{
        page === 'list'
          ? picking
            ? '选择' + label
            : '账户管理'
          : page === 'catalog'
            ? '选择账户类型'
            : page === 'detail'
              ? selected
              : page === 'transfer'
                ? '账户转账'
                : selected
                  ? '编辑账户'
                  : '新增账户'
      }}
    </h1>
    <button
      v-if="writable && ['list', 'detail'].includes(page)"
      class="action"
      @click="edit(page === 'detail' ? selected : '')"
    >
      {{ page === 'list' ? '新增' : '编辑' }}
    </button>
  </header>
  <template v-if="page === 'list'">
    <VanSearch v-if="picking" v-model="search" placeholder="搜索账户或类型" clearable />
    <section v-for="[group, items] in groupedAccounts" :key="group" class="account-group">
      <h2>{{ group }}</h2>
      <div class="account-rows">
        <VanCell
          v-for="a in items"
          :key="a.name"
          :title="a.name"
          :label="
            [a.name === displayType(a) ? '' : displayType(a), a.active ? '' : '已停用']
              .filter(Boolean)
              .join(' · ')
          "
          :value="balanceLabel(a.name)"
          :is-link="!picking"
          clickable
          :class="{ picked: picking && chosen === a.name }"
          @click="openAccount(a)"
        >
          <template v-if="picking && chosen === a.name" #right-icon
            ><VanIcon name="success" aria-label="已选" color="var(--green)"
          /></template>
          <template #icon
            ><span
              class="account-type-icon"
              :style="{ '--tone': palette[displayType(a)] || '#617573' }"
              ><VanIcon :name="symbols[displayType(a)] || 'balance-o'" /></span
          ></template>
        </VanCell>
      </div>
    </section>
    <p v-if="picking && !groupedAccounts.length" class="account-empty">
      {{ search ? '没有匹配的账户' : '暂无可选账户' }}
    </p>
    <button
      v-if="picking && chosen && !required"
      type="button"
      class="clear-account"
      @click="emit('choose', '')"
    >
      清除账户选择
    </button>
  </template>
  <div v-else-if="page === 'catalog'" class="type-catalog">
    <section v-for="(options, group) in typeGroups" :key="group">
      <h2>{{ group }}</h2>
      <div class="type-grid">
        <button v-for="option in options" :key="option" type="button" @click="chooseType(option)">
          <span class="account-type-icon" :style="{ '--tone': palette[option] || '#617573' }"
            ><VanIcon :name="symbols[option] || 'balance-o'" /></span
          >{{ option }}
        </button>
      </div>
    </section>
  </div>
  <template v-else-if="page === 'detail'">
    <div class="summary">
      <small>{{ amountTitle(current?.type) }}</small>
      <h3>{{ balanceLabel(selected) }}</h3>
      <small>全部日期</small>
      <dl>
        <div>
          <dt>收入</dt>
          <dd>¥{{ amountText(summary.income) }}</dd>
        </div>
        <div>
          <dt>支出</dt>
          <dd>¥{{ amountText(summary.expense) }}</dd>
        </div>
      </dl>
    </div>
    <VanButton
      v-if="writable && current?.active"
      block
      round
      type="primary"
      @click="openTransfer(null, credit(current.type) || investment(current.type))"
      >{{
        credit(current.type) ? '记还款' : investment(current.type) ? '记买入' : '账户间转账'
      }}</VanButton
    >
    <VanButton
      v-if="writable && current?.active && investment(current.type)"
      block
      round
      @click="openTransfer()"
      >记赎回</VanButton
    >
    <h2>账户流水 · {{ history.length }} 笔</h2>
    <VanCell
      v-for="item in history"
      :key="item.id"
      :title="item.title"
      :label="item.date"
      :value="item.value"
      :is-link="!item.deposit"
      @click="
        item.deposit
          ? undefined
          : item.transfer
            ? writable && openTransfer(item.transfer)
            : emit('row', item.row?.originalId || item.id)
      "
    />
    <VanCell v-if="!history.length" title="暂无流水" />
  </template>
  <VanForm
    scroll-to-error
    validate-trigger="onSubmit"
    class="managed-form"
    v-else-if="page === 'transfer'"
    @submit="postTransfer"
  >
    <SlotField
      v-for="side in ['from', 'to']"
      :key="side"
      :name="side"
      :model-value="transfer[side]"
      :rules="[
        {
          validator: (v) => availableSide(side, v),
          message: side === 'from' ? '请选择可用的转出账户' : '请选择不同于转出账户的可用账户',
        },
      ]"
      ><ChoiceField
        v-model="transfer[side]"
        resource="account"
        :label="side === 'from' ? '转出账户' : '转入账户'"
        :exclude="transfer[side === 'from' ? 'to' : 'from']"
        required
    /></SlotField>
    <VanField
      name="amount"
      :rules="[{ validator: (v) => cents(v) > 0, message: '请输入大于零的金额，最多两位小数' }]"
      v-model="transferAmount"
      required
      label="金额（元）"
      inputmode="decimal"
      placeholder="0.00"
    />
    <DateField
      name="date"
      :rules="[{ validator: validDay, message: '请选择转账日期' }]"
      v-model="transfer.date"
      required
      label="日期"
    />
    <OptionalField v-model="transfer.note" maxlength="100" label="备注" />
    <RecordActions
      :options="[
        {
          name: '撤销这笔转账',
          visible: book.transfers?.some((t) => t.id === transfer.id) === true,
          danger: true,
          run: cancelTransfer,
        },
      ]"
    >
      <VanButton type="primary" native-type="submit">保存转账</VanButton>
    </RecordActions>
  </VanForm>
  <VanForm scroll-to-error validate-trigger="onSubmit" class="managed-form" v-else @submit="save">
    <button type="button" class="form-picker-row" @click="page = 'catalog'">
      <span>账户类型<span class="field-mark" aria-label="必填">*</span></span
      ><span>{{ type }}<VanIcon name="arrow" /></span>
    </button>
    <VanCell v-if="credit(type)" title="有溢缴款"
      ><template #right-icon
        ><VanSwitch v-model="overpaid" size="20px" aria-label="有溢缴款" /></template
    ></VanCell>
    <OptionalField
      name="balance"
      :error-message="error"
      @update:model-value="error = ''"
      :rules="[
        {
          validator: (v) =>
            !v.trim() ||
            (Number.isSafeInteger(cents(v.trim())) &&
              (!(credit(type) || investment(type)) || cents(v.trim()) >= 0)),
          message: '请输入有效金额，最多两位小数；待还金额和市值须为零或正数',
        },
      ]"
      v-model="currentAmount"
      :label="credit(type) && overpaid ? '溢缴款' : amountTitle(type)"
      inputmode="decimal"
      :placeholder="credit(type) && overpaid ? '填多还的金额' : amountHint"
    />
    <VanField
      name="name"
      :rules="[{ validator: (v) => accountNameError(accounts, selected, v) || true }]"
      v-model="name"
      required
      label="账户名称"
      maxlength="40"
      :placeholder="
        accounts.some((a) => a.name === type) ? '给这个账户起个不同的名称' : '输入账户名称'
      "
      @update:model-value="error = ''"
    />
    <VanCell v-if="selected" title="启用账户" label="停用后仍可查看历史流水"
      ><template #right-icon
        ><VanSwitch v-model="active" size="22px" aria-label="启用账户" /></template
    ></VanCell>
    <VanButton round block type="primary" native-type="submit">保存</VanButton>
  </VanForm>
</template>
<style scoped>
h2 {
  font-size: 14px;
  color: #577064;
  margin: 20px 12px 8px;
}
.summary {
  padding: 18px;
  background: var(--green-deep);
  color: white;
  border-radius: 16px;
}
.summary small,
dt {
  color: #d5e8df;
}
.summary dl {
  display: flex;
  gap: 24px;
  flex-wrap: wrap;
  margin: 12px 0 0;
}
.summary dd {
  margin: 4px 0;
  font-size: 20px;
  font-weight: 650;
}
form > .van-button {
  margin-top: 16px;
}
.pick {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  font-size: 14px;
  background: white;
  border-bottom: 1px solid #edf1ed;
}
.pick select {
  max-width: 65%;
  font: inherit;
  border: 0;
  background: white;
}
.summary h3 {
  font-size: 26px;
  margin: 6px 0 14px;
}
.summary + .van-button {
  margin-top: 12px;
}
form p {
  color: #a2303d;
}
.account-rows .account-type-icon {
  margin-right: 12px;
  flex: none;
}
.account-group,
.type-catalog section {
  margin: 18px 0 22px;
}
.account-group h2,
.type-catalog h2 {
  margin: 0 0 9px 3px;
  color: #3a5146;
  font-weight: 650;
}
.type-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 2px;
  background: #edf1ec;
  border: 1px solid #e5ebe5;
  border-radius: 16px;
  overflow: hidden;
}
.type-grid button {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px 3px;
  min-height: 74px;
  border: 0;
  background: #fff;
  font: inherit;
  font-size: 14px;
  color: #243c30;
}
.type-grid button:active {
  background: #edf5ef;
}
.account-type-icon {
  display: grid;
  place-items: center;
  border-radius: 10px;
  width: 32px;
  height: 32px;
  background: color-mix(in srgb, var(--tone) 11%, white);
  color: var(--tone);
  font-size: 21px;
}
.account-empty,
.clear-account {
  color: var(--muted);
  text-align: center;
  font-size: 14px;
}
.clear-account {
  display: block;
  margin: 8px auto;
  padding: 12px;
  background: transparent;
  border: 0;
}
.account-rows .picked {
  background: var(--mint);
}
.picked > .van-icon:last-child {
  margin-left: 6px;
}
</style>
