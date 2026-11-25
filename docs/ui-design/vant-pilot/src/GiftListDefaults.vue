<script setup>
import DateField from './DateField.vue';
import GiftReasonPicker from './GiftReasonPicker.vue';
import { validDay } from './ledger.mjs';
const date = defineModel('date', { type: String });
const reason = defineModel('reason', { type: String });
</script>
<template>
  <DateField
    v-model="date"
    name="defaultDate"
    label="默认日期"
    clearable
    :rules="[{ validator: (v) => !v || validDay(v), message: '请选择有效日期' }]"
  />
  <GiftReasonPicker v-model="reason" label="默认事由" />
</template>
