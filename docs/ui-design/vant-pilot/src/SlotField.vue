<script setup>
import { Field as VanField } from 'vant';
</script>
<template>
  <VanField class="slot-field" :border="false"
    ><template #input><slot /></template
  ></VanField>
</template>
<style scoped>
.slot-field.van-cell {
  display: block;
  padding: 0;
  background: transparent;
  overflow: visible;
}
.slot-field :deep(> .van-cell__value) {
  overflow: visible;
}
.slot-field :deep(.van-field__control--custom) {
  display: block;
}
</style>
