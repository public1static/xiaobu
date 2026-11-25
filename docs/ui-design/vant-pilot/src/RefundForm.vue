<script setup>
import RecordActions from './RecordActions.vue';
import { computed, ref } from 'vue';
import { Button as VanButton, Field as VanField, Form as VanForm } from 'vant';
import DateField from './DateField.vue';
import ChoiceField from './ChoiceField.vue';
import { amountText, refunded, validDay } from './ledger.mjs';
const props = defineProps({ row: Object, draft: Object, today: String });
const emit = defineEmits(['save', 'close', 'remove']);
// 编辑退款时，将原退款金额加回本次可退额度。
const existing = computed(() => props.row.refunds?.find((r) => r.id === props.draft.id));
const limit = computed(
  () =>
    props.row.amount -
    refunded(props.row) +
    (props.row.refunds?.find((r) => r.id === props.draft.id)?.amount || 0),
);
const money = ref(props.draft.amount == null ? '' : (props.draft.amount / 100).toFixed(2));
const date = ref(props.draft.date),
  account = ref(props.draft.account);
const cents = computed(() => Math.round(Number(money.value) * 100));
// 表单校验通过后按分写入，保留编辑前的退款编号。
function submit() {
  emit('save', {
    id: props.draft.id,
    amount: cents.value,
    date: date.value,
    account: account.value,
  });
}
</script>
<template>
  <div class="pilot-sheet-head">
    <h2>{{ existing ? '编辑退款' : '记退款' }}</h2>
    <VanButton round plain icon="cross" aria-label="关闭退款" @click="$emit('close')" />
  </div>
  <VanForm class="managed-form" scroll-to-error validate-trigger="onSubmit" @submit="submit">
    <VanField
      v-model="money"
      name="amount"
      label="退款金额"
      required
      inputmode="decimal"
      :placeholder="'最多可退 ¥' + amountText(limit)"
      :rules="[
        {
          validator: (v) => /^\d+(?:\.\d{1,2})?$/.test(v) && cents > 0,
          message: '请输入大于零的金额，最多两位小数',
        },
        { validator: () => cents <= limit, message: '退款金额超过可退金额' },
      ]"
    />
    <DateField
      v-model="date"
      required
      name="date"
      label="到账日期"
      :rules="[
        {
          validator: (v) => validDay(v) && v >= row.date && v <= today,
          message: '到账日期须在消费日至今天之间',
        },
      ]"
    />
    <ChoiceField v-model="account" label="到账账户" resource="account" />
    <RecordActions
      :options="[
        { name: '撤销退款', visible: !!existing, danger: true, run: () => $emit('remove') },
      ]"
    >
      <VanButton type="primary" native-type="submit">保存退款</VanButton>
    </RecordActions>
  </VanForm>
</template>
