<script setup lang="ts">
import { ref } from 'vue';
import type { LedgerTransactionFilter } from '../../api/ledger-transactions';

const props = defineProps<{ filter: LedgerTransactionFilter; today: string }>();
const emit = defineEmits<{
  (event: 'close'): void;
  (event: 'apply', filter: LedgerTransactionFilter): void;
}>();
const from = ref(props.filter.from);
const through = ref(props.filter.through);
const error = ref('');

function apply() {
  const start = from.value;
  const end = through.value;
  if (
    Boolean(start) !== Boolean(end) ||
    (start && (start > end || Date.parse(end) - Date.parse(start) > 365 * 86400000))
  ) {
    error.value = '请选择完整的起止日期，范围最多 366 天。';
    return;
  }
  emit('apply', { from: start, through: end, keyword: props.filter.keyword });
}
</script>

<template>
  <view class="overlay">
    <view class="sheet login-sheet" role="dialog" aria-label="选择日期" aria-modal="true">
      <view class="sheet-head">
        <text>选择日期</text>
        <button class="close" aria-label="关闭日期筛选" @click="emit('close')">×</button>
      </view>
      <view class="meta">
        <picker
          mode="date"
          :value="from || today"
          start="1900-01-01"
          :end="today"
          @change="from = $event.detail.value"
        >
          <view class="meta-row"
            ><text>开始日期</text><text>{{ from || '按所选月份' }} ›</text></view
          >
        </picker>
        <picker
          mode="date"
          :value="through || today"
          start="1900-01-01"
          :end="today"
          @change="through = $event.detail.value"
        >
          <view class="meta-row"
            ><text>结束日期</text><text>{{ through || '按所选月份' }} ›</text></view
          >
        </picker>
      </view>
      <text v-if="error" class="error" role="alert">{{ error }}</text>
      <button class="primary" @click="apply">查看流水</button>
      <button
        class="primary secondary"
        @click="emit('apply', { from: '', through: '', keyword: props.filter.keyword })"
      >
        恢复按月
      </button>
    </view>
  </view>
</template>

<style scoped src="./ledger.css"></style>
