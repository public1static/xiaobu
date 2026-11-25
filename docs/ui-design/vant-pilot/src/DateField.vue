<script setup>
import FieldClear from './FieldClear.vue';
import { computed, ref } from 'vue';
import { Calendar as VanCalendar, Field as VanField } from 'vant';
import { asDate, localDay, validDay } from './ledger.mjs';
defineOptions({ inheritAttrs: false });
defineProps({ label: String, clearable: Boolean });
const model = defineModel({ type: String });
const open = ref(false),
  trigger = ref(null);
// 日历允许跨年登记，编辑时覆盖已保存日期所在年份。
const current = computed(() => (validDay(model.value) ? asDate(model.value) : new Date()));
const min = computed(() => new Date(Math.min(current.value.getFullYear(), 1900), 0, 1));
const max = computed(() => new Date(Math.max(current.value.getFullYear(), 2100), 11, 31));
// 确认后才写入，关闭弹层时保留原值。
function confirm(date) {
  model.value = localDay(date);
  open.value = false;
}
</script>
<template>
  <VanField
    v-bind="$attrs"
    :class="{ 'clearable-value': clearable }"
    :label="label"
    :model-value="model"
    is-link
    readonly
    clickable
    @click="open = true"
  >
    <template #input
      ><button
        ref="trigger"
        type="button"
        class="date-trigger"
        :class="{ empty: !model }"
        aria-haspopup="dialog"
        :aria-label="label + ' ' + (model || '请选择')"
        @click.stop="open = true"
      >
        {{ model || '请选择' }}
      </button></template
    >
    <template v-if="clearable && model" #button
      ><FieldClear
        :label="label"
        @clear="
          model = '';
          trigger?.focus({ preventScroll: true });
        "
    /></template>
  </VanField>
  <VanCalendar
    v-model:show="open"
    teleport="body"
    :title="'选择' + label"
    switch-mode="year-month"
    :default-date="current"
    :min-date="min"
    :max-date="max"
    :style="{ left: 0, right: 0, marginInline: 'auto', maxWidth: '440px' }"
    @confirm="confirm"
    @closed="trigger?.focus({ preventScroll: true })"
  />
</template>
<style scoped>
.date-trigger {
  background: transparent;
  border: 0;
  padding: 0;
  color: inherit;
  text-align: left;
  font: inherit;
  width: 100%;
  min-height: 24px;
}
.date-trigger.empty {
  color: var(--muted);
}
</style>
