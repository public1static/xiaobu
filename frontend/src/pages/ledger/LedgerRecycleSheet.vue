<script setup lang="ts">
import { onMounted, ref } from 'vue';
import {
  listRecentlyDeleted,
  restoreLedgerTransaction,
  type DeletedLedgerTransaction,
} from '../../api/ledger-transactions';
import { ApiRequestError } from '../../api/request';
import { categoryLabel, formatCents } from './transaction';

const props = defineProps<{ ledgerId: string; categoryNames?: Record<string, string> }>();
const emit = defineEmits<{ (event: 'close'): void; (event: 'restored'): void }>();
const items = ref<DeletedLedgerTransaction[]>([]);
const loading = ref(true);
const busy = ref(false);
const error = ref('');

async function refresh() {
  loading.value = true;
  error.value = '';
  try {
    items.value = await listRecentlyDeleted(props.ledgerId);
  } catch {
    error.value = '无法查看最近删除。';
  } finally {
    loading.value = false;
  }
}

async function restore(entry: DeletedLedgerTransaction) {
  if (busy.value) return;
  busy.value = true;
  error.value = '';
  try {
    await restoreLedgerTransaction(props.ledgerId, entry.transaction.id, entry.version);
    items.value = items.value.filter((item) => item.transaction.id !== entry.transaction.id);
    emit('restored');
  } catch (failure) {
    if (failure instanceof ApiRequestError && failure.status === 409) {
      await refresh();
      if (!error.value) error.value = '这笔账的状态已变更，列表已刷新。';
    } else {
      error.value = '未能恢复，请核对当前账本权限。';
    }
  } finally {
    busy.value = false;
  }
}

onMounted(refresh);
</script>

<template>
  <view class="overlay">
    <view class="sheet login-sheet" role="dialog" aria-label="最近删除" aria-modal="true">
      <view class="sheet-head sheet-child">
        <text>最近删除</text>
        <button class="close" aria-label="关闭最近删除" :disabled="busy" @click="emit('close')">
          ×
        </button>
      </view>
      <text v-if="loading" class="preview-label sheet-child" role="status">正在加载</text>
      <text v-else-if="!items.length && !error" class="preview-label sheet-child"
        >最近没有删除的流水</text
      >
      <view v-if="items.length" class="meta sheet-child"
        ><view v-for="entry in items" :key="entry.transaction.id" class="meta-row recycle-row">
          <view class="recycle-label">
            <text
              >{{
                categoryNames?.[entry.transaction.categoryCode] ||
                categoryLabel(entry.transaction.categoryCode, entry.transaction.direction)
              }}
              · ¥{{ formatCents(entry.transaction.amountCents) }}</text
            >
            <text class="preview-label">{{
              entry.transaction.note || entry.transaction.bookedOn
            }}</text>
          </view>
          <button class="text-button" :disabled="busy" @click="restore(entry)">恢复</button>
        </view> </view
      ><text v-if="error" class="error sheet-child" role="alert">{{ error }}</text>
    </view>
  </view>
</template>

<style scoped src="./ledger.css"></style>
<style scoped>
.recycle-row {
  padding-inline: 13px;
}
.recycle-label {
  display: grid;
  gap: 4px;
}
</style>
