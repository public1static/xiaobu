<script setup>
import { computed, ref } from 'vue';
import { ActionSheet as VanActionSheet, Icon as VanIcon } from 'vant';
const model = defineModel({ type: String });
const props = defineProps({
  label: String,
  options: Array,
  required: Boolean,
  readonly: Boolean,
  compact: Boolean,
});
const open = ref(false),
  trigger = ref(null);
// 选项一直使用相同的业务值，弹层中以勾选状态标明当前选择。
const current = computed(
  () => props.options.find((o) => o.value === model.value)?.label || '请选择',
);
function select(value) {
  model.value = value;
  open.value = false;
}
</script>
<template>
  <component
    :is="readonly ? 'div' : 'button'"
    :type="readonly ? undefined : 'button'"
    class="form-picker-row select-row"
    :class="{ compact }"
    :aria-label="label + '：' + current"
    :aria-haspopup="readonly ? undefined : 'dialog'"
    ref="trigger"
    @click="!readonly && (open = true)"
  >
    <span v-if="!compact"
      >{{ label }}<span v-if="required" class="field-mark" aria-label="必填">*</span></span
    >
    <span>{{ current }}<VanIcon v-if="!readonly" name="arrow" /></span>
  </component>
  <VanActionSheet
    v-model:show="open"
    teleport="body"
    :title="label"
    :style="{ left: 0, right: 0, marginInline: 'auto', maxWidth: '440px' }"
    @closed="trigger?.focus?.({ preventScroll: true })"
    @keydown.esc="open = false"
  >
    <div class="select-options">
      <button
        v-for="opt in options"
        :key="opt.value"
        type="button"
        :disabled="opt.disabled"
        :aria-pressed="model === opt.value"
        @click="select(opt.value)"
      >
        {{ opt.label }}<VanIcon v-if="model === opt.value" name="success" />
      </button>
    </div>
  </VanActionSheet>
</template>
<style scoped>
.select-row.compact {
  display: block;
  width: auto;
  flex: 1;
  min-height: 40px;
  padding: 6px 10px;
  border: 1px solid var(--line);
  border-radius: 9px;
  background: var(--surface);
}
.select-options {
  padding: 4px 16px 16px;
  display: grid;
  gap: 4px;
}
.select-options button {
  display: flex;
  justify-content: space-between;
  align-items: center;
  min-height: 48px;
  width: 100%;
  padding: 0 14px;
  text-align: left;
  border: 0;
  border-radius: 10px;
  background: var(--surface);
  color: var(--ink);
  font: inherit;
  font-size: 15px;
}
.select-options button[aria-pressed='true'] {
  background: var(--mint);
  color: var(--green);
  font-weight: 600;
}
.select-options button:disabled {
  color: var(--muted);
  opacity: 0.6;
}
</style>
