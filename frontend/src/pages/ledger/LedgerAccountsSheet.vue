<script setup lang="ts">
import { onMounted, ref } from 'vue';
import {
  createLedgerAccount,
  listLedgerAccounts,
  type LedgerAccount,
} from '../../api/ledger-accounts';
import { ApiRequestError } from '../../api/request';
import { formatCents } from './transaction';

const props = defineProps<{ ledgerId: string; allowCreate: boolean; selectable: boolean }>();
const emit = defineEmits<{
  (event: 'close'): void;
  (event: 'select', account: LedgerAccount | null): void;
}>();
const accounts = ref<LedgerAccount[]>([]);
const busy = ref(false);
const loaded = ref(false);
const editing = ref(false);
const name = ref('');
const error = ref('');

async function refresh() {
  busy.value = true;
  error.value = '';
  try {
    accounts.value = await listLedgerAccounts(props.ledgerId);
    loaded.value = true;
  } catch {
    error.value = '账户加载失败，请重试。';
  } finally {
    busy.value = false;
  }
}

async function create() {
  if (busy.value) return;
  const value = name.value.trim();
  if (!value) {
    error.value = '请输入账户名称。';
    return;
  }
  busy.value = true;
  error.value = '';
  try {
    const account = await createLedgerAccount(props.ledgerId, value);
    accounts.value = [...accounts.value, account];
    editing.value = false;
    name.value = '';
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

onMounted(refresh);
</script>

<template>
  <view class="overlay account-overlay">
    <view
      class="sheet login-sheet"
      role="dialog"
      :aria-label="selectable ? '选择账户' : '账户'"
      aria-modal="true"
    >
      <view class="sheet-head">
        <text>{{ selectable ? '选择账户' : '账户' }}</text>
        <button class="close" aria-label="关闭账户" :disabled="busy" @click="emit('close')">
          ×
        </button>
      </view>
      <text class="preview-label">已记净额只按这本账已记录的收支计算。</text>
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
        v-for="account in accounts"
        :key="account.id"
        class="text-button account-row"
        :disabled="busy || !selectable"
        @click="emit('select', account)"
      >
        <text>{{ account.name }}</text>
        <text
          >{{ account.recordedBalanceCents < 0 ? '−' : '' }}¥{{
            formatCents(Math.abs(account.recordedBalanceCents))
          }}</text
        >
      </button>
      <view v-if="editing" class="account-create">
        <input v-model="name" :maxlength="32" placeholder="例如：现金" aria-label="账户名称" />
        <button class="primary" :disabled="busy" @click="create">保存账户</button>
        <button
          class="text-button"
          :disabled="busy"
          @click="
            editing = false;
            error = '';
          "
        >
          取消
        </button>
      </view>
      <button
        v-else-if="allowCreate && loaded"
        class="primary secondary"
        :disabled="busy"
        @click="editing = true"
      >
        新增账户
      </button>
      <text v-if="error" class="error" role="alert">{{ error }}</text>
      <button v-if="error && !loaded" class="text-button" :disabled="busy" @click="refresh">
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
.account-row {
  display: flex;
  justify-content: space-between;
  text-align: left;
  color: var(--ink);
  border: 1px solid var(--line);
  border-radius: 10px;
  padding-inline: 10px;
  background: white;
}
.account-row[disabled] {
  color: var(--ink);
  background: white;
}
.account-create {
  display: grid;
  gap: 8px;
}
.account-create input {
  min-height: 43px;
  border: 1px solid var(--line);
  border-radius: 10px;
  padding-inline: 10px;
}
</style>
