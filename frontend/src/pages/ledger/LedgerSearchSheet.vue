<script setup lang="ts">
// #ifdef H5
import { Calendar as VanCalendar } from 'vant';
import 'vant/es/calendar/style';
import { localDay } from './transaction';
// #endif
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

// #ifdef H5
// 日历的初始值在打开面板时固定，选择起点后保留未完成的区间。
const minDate = new Date(1900, 0, 1);
const maxDate = new Date(props.today + 'T00:00:00');
const initialRange =
  from.value && through.value
    ? [new Date(from.value + 'T00:00:00'), new Date(through.value + 'T00:00:00')]
    : null;
// 先暂存区间，只有点击查看流水才切换筛选条件。
function stageRange(days: Date[]) {
  from.value = localDay(days[0]);
  through.value = days[1] ? localDay(days[1]) : '';
  error.value = '';
}
// #endif
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
      <view class="sheet-head sheet-child">
        <text>选择日期</text>
        <button class="close" aria-label="关闭日期筛选" @click="emit('close')">×</button>
      </view>
      <!-- #ifdef H5 -->
      <VanCalendar
        class="sheet-child"
        :poppable="false"
        type="range"
        switch-mode="year-month"
        allow-same-day
        :show-title="false"
        :show-confirm="false"
        :min-date="minDate"
        :max-date="maxDate"
        :default-date="initialRange"
        color="#176a57"
        style="height: min(50vh, 380px)"
        @select="stageRange"
      />
      <!-- #endif -->
      <!-- #ifdef MP-WEIXIN -->
      <view class="meta sheet-child">
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
      <!-- #endif -->
      <text v-if="error" class="error sheet-child" role="alert">{{ error }}</text>
      <view class="record-actions sheet-child">
        <button
          class="primary secondary"
          @click="emit('apply', { from: '', through: '', keyword: props.filter.keyword })"
        >
          恢复按月</button
        ><button class="primary" @click="apply">查看流水</button></view
      >
    </view>
  </view>
</template>

<style scoped src="./ledger.css"></style>
