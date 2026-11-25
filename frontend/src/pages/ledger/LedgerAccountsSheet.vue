<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import {
  createLedgerAccount,
  listAccountTransfers,
  listLedgerAccounts,
  recordAccountTransfer,
  type AccountTransfer,
  type LedgerAccount,
} from '../../api/ledger-accounts';
import { ApiRequestError } from '../../api/request';
import { formatCents, localDay, parseCents, parseOpeningBalance } from './transaction';

const props = defineProps<{
  ledgerId: string;
  allowCreate: boolean;
  allowTransfer: boolean;
  selectable: boolean;
}>();
const emit = defineEmits<{
  (event: 'close'): void;
  (event: 'select', account: LedgerAccount | null): void;
}>();
const accounts = ref<LedgerAccount[]>([]);
const transfers = ref<AccountTransfer[]>([]);
const busy = ref(false);
const loaded = ref(false);
const historyError = ref(false);
const mode = ref<'list' | 'account' | 'transfer'>('list');
const name = ref('');
const opening = ref('');
const amount = ref('');
const sourceId = ref('');
const targetId = ref('');
const note = ref('');
const bookedOn = ref(localDay());
const error = ref('');
const targetAccounts = computed(() => accounts.value.filter(({ id }) => id !== sourceId.value));

function accountName(id: string) {
  return accounts.value.find((account) => account.id === id)?.name ?? '账户';
}

function formatBalance(cents: number) {
  return `${cents < 0 ? '−' : ''}¥${formatCents(Math.abs(cents))}`;
}

async function refresh() {
  busy.value = true;
  error.value = '';
  historyError.value = false;
  try {
    accounts.value = await listLedgerAccounts(props.ledgerId);
    loaded.value = true;
    if (!props.selectable) {
      try {
        transfers.value = await listAccountTransfers(props.ledgerId);
      } catch {
        historyError.value = true;
      }
    }
  } catch {
    error.value = '账户加载失败，请重试。';
  } finally {
    busy.value = false;
  }
}

function returnToList() {
  mode.value = 'list';
  error.value = '';
}

function openTransfer() {
  sourceId.value = accounts.value[0].id;
  targetId.value = '';
  bookedOn.value = localDay();
  amount.value = '';
  note.value = '';
  mode.value = 'transfer';
}

function selectSource(event: { detail: { value: string | number } }) {
  sourceId.value = accounts.value[Number(event.detail.value)].id;
  if (sourceId.value === targetId.value) targetId.value = '';
}

function selectTarget(event: { detail: { value: string | number } }) {
  targetId.value = targetAccounts.value[Number(event.detail.value)].id;
}

function selectDate(event: { detail: { value: string } }) {
  bookedOn.value = event.detail.value;
}

async function create() {
  if (busy.value) return;
  const value = name.value.trim();
  const openingBalanceCents = parseOpeningBalance(opening.value);
  if (!value) {
    error.value = '请输入账户名称。';
    return;
  }
  if (openingBalanceCents === null) {
    error.value = '期初余额最多支持 8 位整数和 2 位小数。';
    return;
  }
  busy.value = true;
  error.value = '';
  try {
    const account = await createLedgerAccount(props.ledgerId, value, openingBalanceCents);
    accounts.value = [...accounts.value, account];
    name.value = '';
    opening.value = '';
    returnToList();
    if (props.selectable) emit('select', account);
  } catch (failure) {
    error.value =
      failure instanceof ApiRequestError && failure.status === 409
        ? '当前账本已有同名账户。'
        : '未确认新增，请先查看账户列表。';
  } finally {
    busy.value = false;
  }
}

async function transfer() {
  if (busy.value || !props.allowTransfer) return;
  const amountCents = parseCents(amount.value);
  if (!sourceId.value || !targetId.value || sourceId.value === targetId.value) {
    error.value = '请选择两个不同的账户。';
    return;
  }
  if (amountCents === null) {
    error.value = '请输入大于 0 的金额，最多 2 位小数。';
    return;
  }
  busy.value = true;
  error.value = '';
  try {
    await recordAccountTransfer(props.ledgerId, {
      sourceAccountId: sourceId.value,
      targetAccountId: targetId.value,
      bookedOn: bookedOn.value,
      amountCents,
      note: note.value.trim(),
    });
    returnToList();
    await refresh();
  } catch (failure) {
    if (failure instanceof ApiRequestError && (failure.status === 400 || failure.status === 404)) {
      error.value = '账户或转账信息有误，请检查后再保存。';
    } else {
      returnToList();
      await refresh();
      error.value = '转账结果未确认，请先查看最近转账。';
    }
  } finally {
    busy.value = false;
  }
}

onMounted(refresh);
</script>

<template>
  <view class="overlay account-overlay">
    <view
      class="sheet login-sheet accounts-sheet"
      role="dialog"
      :aria-label="selectable ? '选择账户' : '账户'"
      aria-modal="true"
    >
      <view class="sheet-head">
        <text>{{
          mode === 'account'
            ? '新增账户'
            : mode === 'transfer'
              ? '账户转账'
              : selectable
                ? '选择账户'
                : '账户'
        }}</text>
        <button class="close" aria-label="关闭账户" :disabled="busy" @click="emit('close')">
          ×
        </button>
      </view>
      <template v-if="mode === 'list'">
        <text v-if="!selectable" class="preview-label">账面余额按期初余额、收支和转账计算。</text>
        <text v-if="!loaded && busy" class="preview-label" role="status">正在加载账户</text>
        <text v-if="loaded && !accounts.length" class="preview-label">还没有账户</text>
        <button
          v-if="selectable"
          class="text-button account-row"
          :disabled="busy"
          @click="emit('select', null)"
        >
          不记账户
        </button>
        <button
          v-for="account in selectable ? accounts : []"
          :key="account.id"
          class="text-button account-row"
          :disabled="busy"
          @click="emit('select', account)"
        >
          <text>{{ account.name }}</text>
          <text>{{ formatBalance(account.balanceCents) }}</text>
        </button>
        <view v-if="!selectable" class="account-list">
          <view v-for="account in accounts" :key="account.id" class="account-row">
            <view class="account-summary">
              <text>{{ account.name }}</text>
              <text class="preview-label"
                >期初 {{ formatBalance(account.openingBalanceCents) }}</text
              >
            </view>
            <text class="account-balance">{{ formatBalance(account.balanceCents) }}</text>
          </view>
        </view>
        <view v-if="loaded" class="account-actions">
          <button
            v-if="allowCreate"
            class="primary secondary"
            :disabled="busy"
            @click="
              mode = 'account';
              error = '';
            "
          >
            新增账户
          </button>
          <button
            v-if="!selectable && allowTransfer && accounts.length >= 2"
            class="primary"
            :disabled="busy"
            @click="openTransfer"
          >
            转账
          </button>
        </view>
        <view v-if="!selectable && (transfers.length || historyError)" class="account-history">
          <text class="account-history-title">最近转账</text>
          <text v-if="historyError" class="error">转账记录加载失败。</text>
          <view v-for="entry in transfers" :key="entry.id" class="transfer-row">
            <view class="account-summary">
              <text
                >{{ accountName(entry.sourceAccountId) }} →
                {{ accountName(entry.targetAccountId) }}</text
              >
              <text class="preview-label"
                >{{ entry.bookedOn }}{{ entry.note ? ' · ' + entry.note : '' }}</text
              >
            </view>
            <text class="account-balance">{{ formatBalance(entry.amountCents) }}</text>
          </view>
        </view>
      </template>
      <view v-else-if="mode === 'account'" class="account-form">
        <text>账户名称</text>
        <input v-model="name" :maxlength="32" placeholder="例如：现金" aria-label="账户名称" />
        <text>期初余额 <text class="preview-label">选填</text></text>
        <input
          v-model="opening"
          type="text"
          :maxlength="12"
          placeholder="0.00"
          aria-label="期初余额"
        />
        <text class="preview-label">填开始记账时的余额，欠款可填负数。</text>
        <button class="primary" :disabled="busy" @click="create">保存账户</button>
        <button class="text-button" :disabled="busy" @click="returnToList">取消</button>
      </view>
      <view v-else class="account-form">
        <picker
          :range="accounts"
          range-key="name"
          :value="accounts.findIndex(({ id }) => id === sourceId)"
          :disabled="busy"
          @change="selectSource"
        >
          <view class="account-row picker-row"
            ><text>转出账户</text><text>{{ accountName(sourceId) }} ›</text></view
          >
        </picker>
        <picker
          :range="targetAccounts"
          range-key="name"
          :value="
            Math.max(
              0,
              targetAccounts.findIndex(({ id }) => id === targetId),
            )
          "
          :disabled="busy"
          @change="selectTarget"
        >
          <view class="account-row picker-row"
            ><text>转入账户</text
            ><text>{{ targetId ? accountName(targetId) : '请选择' }} ›</text></view
          >
        </picker>
        <text>转账金额</text>
        <input
          v-model="amount"
          type="digit"
          :maxlength="11"
          placeholder="0.00"
          aria-label="转账金额"
        />
        <picker
          mode="date"
          :value="bookedOn"
          start="1900-01-01"
          end="9999-12-31"
          :disabled="busy"
          @change="selectDate"
        >
          <view class="account-row picker-row"
            ><text>日期</text><text>{{ bookedOn }} ›</text></view
          >
        </picker>
        <input v-model="note" :maxlength="100" placeholder="备注（选填）" aria-label="转账备注" />
        <text class="preview-label">只调整两个账户的余额，不计入收支。</text>
        <button class="primary" :disabled="busy" @click="transfer">保存转账</button>
        <button class="text-button" :disabled="busy" @click="returnToList">取消</button>
      </view>
      <text v-if="error" class="error" role="alert">{{ error }}</text>
      <button
        v-if="mode === 'list' && (error || historyError)"
        class="text-button"
        :disabled="busy"
        @click="refresh"
      >
        重新加载
      </button>
    </view>
  </view>
</template>

<style scoped src="./ledger.css"></style>
<style scoped>
.account-overlay {
  z-index: 4;
}
.accounts-sheet {
  max-height: min(86vh, 700px);
  overflow-y: auto;
}
.account-list,
.account-history,
.account-form {
  display: grid;
  gap: 8px;
}
.account-list:empty {
  display: none;
}
.account-row,
.transfer-row {
  display: flex;
  min-height: 43px;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  text-align: left;
  color: var(--ink);
  padding: 7px 10px;
}
.account-row {
  border: 1px solid var(--line);
  border-radius: 10px;
  background: white;
  box-sizing: border-box;
}
.account-summary {
  display: grid;
  gap: 2px;
  overflow-wrap: anywhere;
}
.account-balance {
  flex: none;
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}
.account-actions {
  display: flex;
  gap: 8px;
}
.account-actions button {
  flex: 1;
  margin: 0;
}
.account-history {
  padding-top: 10px;
  border-top: 1px solid var(--line);
}
.account-history-title {
  font-weight: 600;
}
.transfer-row {
  border-bottom: 1px solid var(--line);
}
.account-form input {
  min-height: 43px;
  border: 1px solid var(--line);
  border-radius: 10px;
  padding-inline: 10px;
}
</style>
