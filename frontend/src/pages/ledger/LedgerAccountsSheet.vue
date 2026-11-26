<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import LedgerPicker from '../../components/LedgerPicker.vue';
import {
  listLedgerAccounts,
  createLedgerAccount,
  updateLedgerAccount,
  listAccountHistory,
  recordAccountTransfer,
  updateAccountTransfer,
  cancelAccountTransfer,
  type LedgerAccount,
  type AccountHistoryEntry,
} from '../../api/ledger-accounts';
import { ApiRequestError } from '../../api/request';
import {
  categoryLabel,
  formatCents,
  formatSignedCurrency,
  localDay,
  parseCents,
  parseOpeningBalance,
} from './transaction';
import { typeGroups, symbols, palette, amountTitle } from './account-types';
import LedgerIcon from '../../components/LedgerIcon.vue';

const props = defineProps<{
  ledgerId: string;
  allowCreate: boolean;
  allowTransfer: boolean;
  selectable: boolean;
  selectedId?: string;
  categoryNames?: Record<string, string>;
  revision?: number;
}>();
const emit = defineEmits<{
  (event: 'close'): void;
  (event: 'row', id: string): void;
  (event: 'select', account: LedgerAccount | null): void;
}>();
// 管理页、记账选择器与转账两端共享同一账户目录。
const mode = ref<'list' | 'detail' | 'catalog' | 'account' | 'transfer' | 'pick'>('list');
const accounts = ref<LedgerAccount[]>([]);
const selected = ref('');
const current = computed(() => accounts.value.find((a) => a.id === selected.value));
const search = ref('');
const history = ref<AccountHistoryEntry[]>([]);
const busy = ref(false),
  error = ref(''),
  loadFailed = ref(false);
// 表单校验分别显示在可修正的字段下。
const fieldErrors = ref<Record<string, string>>({});
const side = ref<'sourceAccountId' | 'targetAccountId' | null>(null);
const picking = computed(() => props.selectable || mode.value === 'pick');
const chosen = computed(() => (side.value ? transfer.value[side.value] : props.selectedId));
const groups = computed(() =>
  Object.entries(typeGroups)
    .map(([group, types]) => ({
      group,
      items: accounts.value.filter(
        (a) =>
          types.includes(a.type) &&
          (!picking.value ||
            ((a.active || a.id === chosen.value) &&
              (!side.value ||
                a.id !==
                  transfer.value[
                    side.value === 'sourceAccountId' ? 'targetAccountId' : 'sourceAccountId'
                  ]) &&
              [a.name, a.type, group].some((v) =>
                v.toLocaleLowerCase().includes(search.value.trim().toLocaleLowerCase()),
              ))),
      ),
    }))
    .filter((g) => g.items.length),
);
// 账户与转账分别保存草稿，选择另一个账户时保留已输入金额。
const editing = ref(false),
  name = ref(''),
  type = ref('其他'),
  active = ref(true),
  balance = ref(''),
  overpaid = ref(false);
const credit = computed(() => amountTitle(type.value) === '当前待还');
const oldTransfer = ref<AccountHistoryEntry | null>(null);
const transfer = ref({ sourceAccountId: '', targetAccountId: '', bookedOn: localDay(), note: '' }),
  amount = ref('');
const income = computed(() =>
  history.value.reduce((sum, r) => sum + (r.kind === 'INCOME' ? r.amountCents : 0), 0),
);
const expense = computed(() =>
  history.value.reduce(
    (sum, r) =>
      sum + (r.kind === 'EXPENSE' ? r.amountCents : r.kind === 'REFUND' ? -r.amountCents : 0),
    0,
  ),
);
const title = computed(
  () =>
    ({
      list: props.selectable ? '选择账户' : '账户管理',
      pick: side.value === 'sourceAccountId' ? '选择转出账户' : '选择转入账户',
      catalog: '选择账户类型',
      account: editing.value ? '编辑账户' : '新增账户',
      detail: current.value?.name || '账户详情',
      transfer: '账户转账',
    })[mode.value],
);
// 用途决定负债、市值与未知余额的展示。
function balanceLabel(a: LedgerAccount) {
  if (!a.balanceKnown) return '待填' + amountTitle(a.type);
  if (amountTitle(a.type) === '当前待还')
    return (a.balanceCents > 0 ? '溢缴款 ' : '待还 ') + '¥' + formatCents(Math.abs(a.balanceCents));
  return formatSignedCurrency(a.balanceCents);
}
// 服务端返回数据库排序的流水，刷新时同步读取账户余额。
async function refresh() {
  busy.value = true;
  error.value = '';
  loadFailed.value = false;
  try {
    const [catalog, rows] = await Promise.all([
      listLedgerAccounts(props.ledgerId),
      selected.value ? listAccountHistory(props.ledgerId, selected.value) : Promise.resolve([]),
    ]);
    accounts.value = catalog;
    history.value = rows;
  } catch {
    error.value = '账户资料加载失败，请重试。';
    loadFailed.value = true;
  } finally {
    busy.value = false;
  }
}
onMounted(refresh);
watch(
  () => props.revision,
  () => {
    if (mode.value === 'detail') void refresh();
  },
);
// 选择器内选中后返回原表单，管理页则进入账户详情。
function choose(a: LedgerAccount) {
  error.value = '';
  if (side.value) {
    delete fieldErrors.value[side.value === 'sourceAccountId' ? 'source' : 'target'];
    transfer.value[side.value] = a.id;
    side.value = null;
    mode.value = 'transfer';
    return;
  }
  if (props.selectable) {
    emit('select', a);
    return;
  }
  selected.value = a.id;
  mode.value = 'detail';
  history.value = [];
  void refresh();
}
// 保存前返回会丢弃本层草稿。
function back() {
  error.value = '';
  switch (mode.value) {
    case 'list':
      emit('close');
      break;
    case 'detail':
      selected.value = '';
      mode.value = 'list';
      break;
    case 'catalog':
      mode.value = catalogFromForm.value ? 'account' : side.value ? 'pick' : 'list';
      break;
    case 'account':
      mode.value = editing.value ? 'detail' : side.value ? 'pick' : 'list';
      break;
    case 'pick':
      side.value = null;
      mode.value = 'transfer';
      break;
    case 'transfer':
      mode.value = 'detail';
      break;
  }
}
// 从表单更换类型后返回当前草稿。
const catalogFromForm = ref(false);
// 编辑预填当前余额，新建先选择类型。
function edit(a?: LedgerAccount) {
  fieldErrors.value = {};
  editing.value = !!a;
  type.value = a?.type || '其他';
  name.value = a?.name || '';
  active.value = a?.active ?? true;
  overpaid.value = !!a && credit.value && a.balanceCents > 0;
  balance.value = a?.balanceKnown
    ? ((credit.value ? Math.abs(a.balanceCents) : a.balanceCents) / 100).toFixed(2)
    : '';
  catalogFromForm.value = false;
  error.value = '';
  mode.value = a ? 'account' : 'catalog';
}
// 新建时建议使用产品名称，重复类型仍可自行命名。
function setType(next: string) {
  if (!editing.value && (!name.value || name.value === type.value))
    name.value = next !== '其他' && !accounts.value.some((a) => a.name === next) ? next : '';
  type.value = next;
  mode.value = 'account';
}
// 保存当前账户资料；选择器中新建成功会直接带回原表单。
async function saveAccount() {
  if (busy.value) return;
  const value = balance.value.trim() ? parseOpeningBalance(balance.value) : null;
  fieldErrors.value = {};
  if (!name.value.trim()) fieldErrors.value.name = '请输入账户名称。';
  if (
    balance.value.trim() &&
    (value === null || (amountTitle(type.value) !== '当前余额' && value < 0))
  ) {
    fieldErrors.value.balance = '请输入有效金额，待还金额和市值须为零或正数。';
  }
  if (Object.keys(fieldErrors.value).length) return;
  const signed = value !== null && credit.value && !overpaid.value ? -value : value;
  busy.value = true;
  error.value = '';
  try {
    if (editing.value) {
      await updateLedgerAccount(props.ledgerId, selected.value, {
        name: name.value.trim(),
        type: type.value,
        active: active.value,
        preserveBalance:
          signed === (current.value?.balanceKnown ? current.value.balanceCents : null),
        currentBalanceCents: signed,
      });
    } else {
      const a = await createLedgerAccount(
        props.ledgerId,
        name.value.trim(),
        signed ?? 0,
        type.value,
        signed !== null,
      );
      accounts.value.push(a);
      if (side.value || props.selectable) {
        choose(a);
        return;
      }
      selected.value = a.id;
    }
    mode.value = 'detail';
    await refresh();
  } catch (e) {
    if (e instanceof ApiRequestError && e.status === 409)
      fieldErrors.value.name = '当前账本已有同名账户。';
    else error.value = '未确认保存，请核对账户资料。';
  } finally {
    busy.value = false;
  }
}
// 转账、还款、买入和赎回共用双边转账接口。
function openTransfer(incoming: boolean, row?: AccountHistoryEntry) {
  fieldErrors.value = {};
  oldTransfer.value = row ?? null;
  transfer.value = row
    ? {
        sourceAccountId: row.sourceAccountId,
        targetAccountId: row.targetAccountId,
        bookedOn: row.bookedOn,
        note: row.note,
      }
    : {
        sourceAccountId: incoming ? '' : selected.value,
        targetAccountId: incoming ? selected.value : '',
        bookedOn: localDay(),
        note: '',
      };
  amount.value = row ? (row.amountCents / 100).toFixed(2) : '';
  error.value = '';
  mode.value = 'transfer';
}
// 打开转账账户选择器，排除已选的另一端。
function pick(which: 'sourceAccountId' | 'targetAccountId') {
  side.value = which;
  search.value = '';
  mode.value = 'pick';
  error.value = '';
}
// 展示账户原名或当前名称。
function accountName(id: string) {
  return accounts.value.find((a) => a.id === id)?.name || '';
}
// 原型中退款冲减原分类支出，转账单列。
function rowTitle(row: AccountHistoryEntry) {
  if (row.kind === 'TRANSFER')
    return row.sourceAccountId === selected.value
      ? '转至 ' + accountName(row.targetAccountId)
      : '转自 ' + accountName(row.sourceAccountId);
  return (
    (props.categoryNames?.[row.categoryCode] ||
      categoryLabel(row.categoryCode, row.kind === 'INCOME' ? 'INCOME' : 'EXPENSE')) +
    (row.kind === 'REFUND' ? '退款' : '')
  );
}
// 账本管理者和创建人可在详情编辑转账。
function canOpen(row: AccountHistoryEntry) {
  return row.kind !== 'TRANSFER' || (props.allowTransfer && (props.allowCreate || !!row.ownRecord));
}
// 打开原流水或已有转账。
function openRow(row: AccountHistoryEntry) {
  if (!canOpen(row)) return;
  if (row.kind === 'TRANSFER') openTransfer(false, row);
  else if (row.transactionId) emit('row', row.transactionId);
}
// 仅在金额和双边账户有效时提交。
async function saveTransfer() {
  if (busy.value) return;
  const amountCents = parseCents(amount.value);
  fieldErrors.value = {};
  if (!transfer.value.sourceAccountId) fieldErrors.value.source = '请选择可用的转出账户。';
  if (
    !transfer.value.targetAccountId ||
    transfer.value.targetAccountId === transfer.value.sourceAccountId
  )
    fieldErrors.value.target = '请选择不同于转出账户的可用账户。';
  if (!amountCents) fieldErrors.value.amount = '请输入大于零的金额，最多两位小数。';
  if (Object.keys(fieldErrors.value).length || !amountCents) return;
  busy.value = true;
  error.value = '';
  try {
    const draft = { ...transfer.value, amountCents };
    if (oldTransfer.value) await updateAccountTransfer(props.ledgerId, oldTransfer.value.id, draft);
    else await recordAccountTransfer(props.ledgerId, draft);
    mode.value = 'detail';
    await refresh();
  } catch {
    error.value = '未确认转账，请返回账户详情核对。';
  } finally {
    busy.value = false;
  }
}
// 二次确认后一次撤销两个账户的余额影响。
function confirmCancel() {
  const row = oldTransfer.value;
  if (!row || busy.value) return;
  uni.showModal({
    title: '撤销转账？',
    content: '两个账户会同步撤回这笔金额。',
    success: async (result) => {
      if (!result.confirm || busy.value) return;
      busy.value = true;
      try {
        await cancelAccountTransfer(props.ledgerId, row.id);
        mode.value = 'detail';
        await refresh();
      } catch {
        error.value = '未确认撤销，请返回账户详情核对。';
      } finally {
        busy.value = false;
      }
    },
  });
}
</script>
<template>
  <view class="overlay account-overlay">
    <view class="sheet accounts-sheet" role="dialog" :aria-label="title">
      <view class="account-head">
        <button class="text-button" aria-label="返回" :disabled="busy" @click="back">‹</button>
        <text>{{ title }}</text>
        <button
          v-if="allowCreate && ['detail', 'list', 'pick'].includes(mode)"
          class="text-button"
          :disabled="busy || loadFailed"
          @click="edit(mode === 'detail' ? current : undefined)"
        >
          {{ mode === 'detail' ? '编辑' : '新增' }}
        </button>
      </view>
      <template v-if="mode === 'list' || mode === 'pick'">
        <input
          v-if="picking"
          v-model="search"
          class="account-search"
          aria-label="搜索账户或类型"
          placeholder="搜索账户或类型"
        />
        <view v-for="g in groups" :key="g.group" class="group">
          <text class="group-title">{{ g.group }}</text>
          <view class="rows">
            <button
              v-for="a in g.items"
              :key="a.id"
              class="account-row"
              :class="{ picked: picking && a.id === chosen }"
              :disabled="busy"
              @click="choose(a)"
            >
              <view
                class="type-icon"
                :style="{ backgroundColor: (palette[a.type] || '#617573') + '19' }"
                ><LedgerIcon
                  class="account-icon"
                  :src="'/static/ledger/accounts/' + (symbols[a.type] || 'balance-o') + '.svg'"
              /></view>
              <view class="item-name">
                <text>{{ a.name }}</text>
                <text class="muted">
                  {{
                    [a.type === a.name ? '' : a.type, a.active ? '' : '已停用']
                      .filter(Boolean)
                      .join(' · ')
                  }}
                </text>
              </view>
              <text>{{ balanceLabel(a) }}</text>
              <text>{{ picking ? (a.id === chosen ? '✓' : '') : '›' }}</text>
            </button>
          </view>
        </view>
        <text v-if="!busy && !loadFailed && !groups.length" class="muted">
          {{ search ? '没有匹配的账户' : picking ? '暂无可选账户' : '还没有账户' }}
        </text>
        <button v-if="selectable && chosen" class="text-button" @click="emit('select', null)">
          清除账户选择
        </button>
      </template>
      <template v-else-if="mode === 'catalog'">
        <view v-for="(types, group) in typeGroups" :key="group">
          <text class="group-title">{{ group }}</text>
          <view class="type-grid">
            <button v-for="t in types" :key="t" @click="setType(t)">
              <view class="type-icon" :style="{ backgroundColor: (palette[t] || '#617573') + '19' }"
                ><LedgerIcon
                  class="account-icon"
                  :src="'/static/ledger/accounts/' + (symbols[t] || 'balance-o') + '.svg'"
              /></view>
              {{ t }}
            </button>
          </view>
        </view>
      </template>
      <template v-else-if="mode === 'detail' && current">
        <view class="summary">
          <text>{{ amountTitle(current.type) }}</text>
          <text class="hero">{{ balanceLabel(current) }}</text>
          <text>全部日期</text>
          <view v-if="!busy && !loadFailed" class="totals">
            <view>
              收入
              <text>¥{{ formatCents(income) }}</text>
            </view>
            <view>
              支出
              <text>¥{{ formatCents(expense) }}</text>
            </view>
          </view>
        </view>
        <button
          v-if="allowTransfer && current.active"
          class="primary"
          :disabled="busy"
          @click="openTransfer(amountTitle(current.type) !== '当前余额')"
        >
          {{
            amountTitle(current.type) === '当前待还'
              ? '记还款'
              : amountTitle(current.type) === '当前市值'
                ? '记买入'
                : '账户间转账'
          }}
        </button>
        <button
          v-if="allowTransfer && current.active && amountTitle(current.type) === '当前市值'"
          class="primary secondary"
          :disabled="busy"
          @click="openTransfer(false)"
        >
          记赎回
        </button>
        <template v-if="!busy && !loadFailed">
          <text class="group-title">账户流水 · {{ history.length }} 笔</text>
          <view class="rows">
            <button v-for="row in history" :key="row.id" class="account-row" @click="openRow(row)">
              <view class="item-name">
                {{ rowTitle(row) }}
                <text class="muted">{{ row.bookedOn }}</text>
              </view>
              <text>
                {{ row.kind === 'EXPENSE' || row.sourceAccountId === selected ? '−' : '＋' }}¥{{
                  formatCents(row.amountCents)
                }}
              </text>
              <text v-if="canOpen(row)">›</text>
            </button>
            <text v-if="!history.length" class="empty">暂无流水</text>
          </view>
        </template>
      </template>
      <template v-else-if="mode === 'account'">
        <view class="meta">
          <button
            class="form-row form-choice"
            @click="
              catalogFromForm = true;
              mode = 'catalog';
            "
          >
            <text>
              账户类型
              <text class="star">*</text>
            </text>
            <text class="picker-value">{{ type }}</text>
          </button>
          <view v-if="credit" class="form-row">
            <text>有溢缴款</text>
            <switch :checked="overpaid" @change="overpaid = $event.detail.value" />
          </view>
          <view class="form-row">
            <text>{{ credit && overpaid ? '溢缴款' : amountTitle(type) }}</text>
            <view class="clear-field">
              <input
                @input="delete fieldErrors.balance"
                v-model="balance"
                :aria-label="credit && overpaid ? '溢缴款' : amountTitle(type)"
                :placeholder="
                  credit
                    ? overpaid
                      ? '填多还的金额'
                      : '填账单上尚未还清的总金额'
                    : amountTitle(type) === '当前市值'
                      ? '填持仓页面显示的总市值'
                      : '填现在能查到的账户余额'
                "
              />
              <button
                v-if="balance"
                class="clear-control"
                aria-label="清除金额"
                @click="
                  balance = '';
                  delete fieldErrors.balance;
                "
              >
                ⊗
              </button>
            </view>
            <text v-if="fieldErrors.balance" class="field-error">{{ fieldErrors.balance }}</text>
          </view>
          <view class="form-row">
            <text>
              账户名称
              <text class="star">*</text>
            </text>
            <input
              v-model="name"
              @input="delete fieldErrors.name"
              :maxlength="40"
              aria-label="账户名称"
              placeholder="输入账户名称"
            />
            <text v-if="fieldErrors.name" class="field-error">{{ fieldErrors.name }}</text>
          </view>
          <view v-if="editing" class="form-row">
            <view>
              启用账户
              <text class="muted">停用后仍可查看历史流水</text>
            </view>
            <switch :checked="active" @change="active = $event.detail.value" />
          </view>
        </view>
        <button class="primary" :disabled="busy" @click="saveAccount">保存</button>
      </template>
      <template v-else-if="mode === 'transfer'">
        <view class="meta">
          <button class="form-row form-choice" @click="pick('sourceAccountId')">
            <text>
              转出账户
              <text class="star">*</text>
            </text>
            <text class="picker-value">
              {{ accountName(transfer.sourceAccountId) || '选择转出账户' }}
            </text>
          </button>
          <text v-if="fieldErrors.source" class="field-error select-error">
            {{ fieldErrors.source }}
          </text>
          <button class="form-row form-choice" @click="pick('targetAccountId')">
            <text>
              转入账户
              <text class="star">*</text>
            </text>
            <text class="picker-value">
              {{ accountName(transfer.targetAccountId) || '选择转入账户' }}
            </text>
          </button>
          <text v-if="fieldErrors.target" class="field-error select-error">
            {{ fieldErrors.target }}
          </text>
          <view class="form-row">
            <text>
              金额（元）
              <text class="star">*</text>
            </text>
            <input
              v-model="amount"
              @input="delete fieldErrors.amount"
              type="digit"
              aria-label="金额（元）"
              placeholder="0.00"
            />
            <text v-if="fieldErrors.amount" class="field-error">{{ fieldErrors.amount }}</text>
          </view>
          <LedgerPicker
            label="选择转账日期"
            mode="date"
            :value="transfer.bookedOn"
            @change="transfer.bookedOn = $event.detail.value"
          >
            <view class="form-row">
              <text>
                日期
                <text class="star">*</text>
              </text>
              <text class="picker-value">{{ transfer.bookedOn }}</text>
            </view>
          </LedgerPicker>
          <view class="form-row">
            <text>备注</text>
            <view class="clear-field">
              <input v-model="transfer.note" :maxlength="100" aria-label="备注" />
              <button
                v-if="transfer.note"
                class="clear-control"
                aria-label="清除备注"
                @click="transfer.note = ''"
              >
                ⊗
              </button>
            </view>
          </view>
        </view>
        <view class="record-actions">
          <button
            v-if="oldTransfer"
            class="primary secondary danger"
            :disabled="busy"
            @click="confirmCancel"
          >
            撤销这笔转账
          </button>
          <button class="primary" :disabled="busy" @click="saveTransfer">保存转账</button>
        </view>
      </template>
      <text v-if="busy" class="muted" role="status">正在处理…</text>
      <text v-if="error" role="alert" class="error">{{ error }}</text>
      <button v-if="loadFailed" class="text-button" @click="refresh">重新加载</button>
    </view>
  </view>
</template>
<style scoped src="./ledger.css">
.field-error {
  grid-column: 2;
  color: #ae3542;
  font-size: 12px;
}
.select-error {
  display: block;
  padding: 2px 12px 6px 107px;
}
.primary.secondary.danger {
  color: #ae3542;
}
.clear-field input {
  flex: 1;
  min-width: 0;
}
</style>
<style scoped>
.account-overlay {
  z-index: 4;
}
.accounts-sheet {
  height: 100vh;
  height: 100dvh;
  max-height: 100dvh;
  border-radius: 0;
  padding-top: calc(8px + env(safe-area-inset-top));
}
.accounts-sheet > view,
.accounts-sheet > button {
  flex-shrink: 0;
}
.account-head {
  display: grid;
  grid-template-columns: 54px 1fr 54px;
  align-items: center;
  text-align: center;
  font-weight: 700;
  font-size: 17px;
}
.account-head button:first-child {
  font-size: 25px;
}
.account-search {
  background: #edf1ec;
  border-radius: 10px;
  padding: 10px 14px;
  font-size: 14px;
}
.group-title {
  display: block;
  color: #3a5146;
  font-size: 14px;
  font-weight: 650;
  margin: 8px 0;
}
.rows,
.type-grid {
  overflow: hidden;
  border: 1px solid #e5ebe5;
  border-radius: 16px;
  background: white;
}
.account-row,
.type-grid button,
.form-choice {
  border: 0;
  border-radius: 0;
  margin: 0;
  background: white;
  color: var(--ink);
  font-size: 14px;
  text-align: left;
  line-height: 1.45;
}
.account-row {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 48px;
  padding: 9px 14px;
  border-bottom: 1px solid #edf1ec;
}
.account-row:last-child {
  border-bottom: 0;
}
.account-row.picked {
  background: #e7f3ec;
}
.item-name {
  display: flex;
  flex-direction: column;
  flex: 1;
}
.muted,
.empty {
  display: block;
  color: var(--muted);
  font-size: 13px;
}
.empty {
  padding: 14px;
}
.type-icon {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  background: #eef3f0;
  border-radius: 10px;
  font-size: 21px;
}
.account-icon {
  width: 21px;
  height: 21px;
}
.type-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 2px;
  background: #edf1ec;
}
.type-grid button {
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 74px;
}
.summary {
  display: flex;
  flex-direction: column;
  gap: 6px;
  background: #164e40;
  color: white;
  padding: 18px;
  border-radius: 16px;
  font-size: 13px;
}
.hero {
  font-size: 26px;
  font-weight: 700;
}
.totals {
  display: flex;
  gap: 24px;
  margin-top: 8px;
}
.totals text {
  display: block;
  font-size: 20px;
  font-weight: 650;
}
.form-choice {
  width: 100%;
}
.form-row switch {
  justify-self: end;
}
.field-error {
  grid-column: 2;
  color: #ae3542;
  font-size: 12px;
}
.select-error {
  display: block;
  padding: 2px 12px 6px 107px;
}
.primary.secondary.danger {
  color: #ae3542;
}
.clear-field input {
  flex: 1;
  min-width: 0;
}
</style>
