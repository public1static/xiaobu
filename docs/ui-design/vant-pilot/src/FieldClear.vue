<script setup>
import { Icon as VanIcon } from 'vant';
defineProps({ label: String });
const emit = defineEmits(['clear']);
</script>
<template>
  <button
    class="field-clear"
    type="button"
    :aria-label="'清除' + label"
    @click.stop="emit('clear')"
  >
    <VanIcon name="clear" aria-hidden="true" />
  </button>
</template>
<style scoped>
button.field-clear {
  position: absolute;
  top: 50%;
  right: 0;
  transform: translateY(-50%);
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  min-height: 0;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--van-field-clear-icon-color, var(--van-gray-5));
  font-size: 16px;
  cursor: pointer;
}
</style>
