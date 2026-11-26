<script setup lang="ts">
import { computed, ref } from 'vue';
// #ifdef H5
import { ActionSheet as VanActionSheet, Calendar as VanCalendar } from 'vant';
import 'vant/es/action-sheet/style';
import 'vant/es/calendar/style';
import { localDay } from '../pages/ledger/transaction';
// #endif

const props = defineProps<{
  label: string;
  mode?: 'date';
  value?: string | number;
  range?: Array<string | object>;
  rangeKey?: string;
  start?: string;
  end?: string;
  disabled?: boolean;
}>();
const emit = defineEmits<{ change: [event: { detail: { value: string } }] }>();
// #ifdef H5
// 确认日期或选项后才向所属表单写入。
const opened = ref(false);
const actions = computed(() =>
  (props.range ?? []).map((item, index) => ({
    name: typeof item === 'string' ? item : String(Reflect.get(item, props.rangeKey || 'name')),
    index,
    className: index === props.value ? 'ledger-option-selected' : '',
  })),
);
// 将选择结果转换为与原生 picker 一致的 change 事件。
function confirm(value: string | number) {
  opened.value = false;
  emit('change', { detail: { value: String(value) } });
}
// 按设备本地日历构造日期，避免 UTC 换算造成跨日。
function calendarDate(value?: string) {
  if (!value) return new Date();
  const [year, month, day] = value.split('-').map(Number);
  return new Date(year, month - 1, day);
}
// #endif
</script>
<template>
  <!-- #ifdef H5 -->
  <button
    class="ledger-picker-trigger"
    :aria-label="label"
    aria-haspopup="dialog"
    :disabled="disabled"
    @click="opened = true"
  >
    <slot />
  </button>
  <VanCalendar
    v-if="opened && mode === 'date'"
    v-model:show="opened"
    teleport="body"
    :title="label"
    switch-mode="year-month"
    class="ledger-picker-popup"
    color="#176a57"
    :default-date="calendarDate(String(value || ''))"
    :min-date="calendarDate(start || '1900-01-01')"
    :max-date="calendarDate(end || '2100-12-31')"
    @confirm="confirm(localDay($event))"
  />
  <VanActionSheet
    v-else-if="opened"
    v-model:show="opened"
    teleport="body"
    class="ledger-picker-popup"
    :title="label"
    :actions="actions"
    cancel-text="取消"
    @select="confirm($event.index)"
  />
  <!-- #endif -->
  <!-- #ifdef MP-WEIXIN -->
  <picker
    :mode="mode || 'selector'"
    :range="range"
    :range-key="rangeKey"
    :start="start"
    :end="end"
    :value="value"
    :disabled="disabled"
    @change="emit('change', { detail: { value: String($event.detail.value) } })"
    ><slot
  /></picker>
  <!-- #endif -->
</template>
<style>
.ledger-picker-trigger {
  display: block;
  width: 100%;
  min-width: 0;
  padding: 0;
  margin: 0;
  background: transparent;
  border: 0;
  border-radius: 0;
  color: inherit;
  font: inherit;
  text-align: left;
  line-height: inherit;
}
.ledger-picker-trigger::after {
  border: 0;
}
.ledger-picker-popup.van-popup {
  max-width: 560px;
  margin-inline: auto;
  left: 0;
  right: 0;
}
.ledger-option-selected {
  color: #176a57;
  background: #eaf4ee;
}
.ledger-option-selected::after {
  content: '✓';
  margin-left: 8px;
}
</style>
