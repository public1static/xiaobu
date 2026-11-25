<script setup>
import { ref } from 'vue';
import { Field as VanField } from 'vant';
import FieldClear from './FieldClear.vue';
defineProps({ label: String, required: Boolean });
const model = defineModel(),
  field = ref(null);
// 清空后仍可在原输入框继续录入。
function clear() {
  model.value = '';
  field.value?.focus();
}
</script>
<template>
  <VanField
    ref="field"
    v-model="model"
    :label="label"
    :required="required"
    :class="{ 'clearable-value': !required }"
  >
    <template v-if="!required && model != null && model !== ''" #button
      ><FieldClear :label="label" @clear="clear"
    /></template>
  </VanField>
</template>
