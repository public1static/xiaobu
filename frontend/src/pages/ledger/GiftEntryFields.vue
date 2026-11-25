<script setup lang="ts">
import type { TransactionExtensionDetail } from '../../api/transaction-extensions';
import type { TransactionDirection } from './transaction';

const props = defineProps<{
  detail: TransactionExtensionDetail;
  direction: TransactionDirection;
  disabled?: boolean;
}>();
const emit = defineEmits<{
  (event: 'change', detail: TransactionExtensionDetail): void;
}>();

function update(key: 'personName' | 'reason', value: string) {
  emit('change', { ...props.detail, [key]: value });
}
</script>

<template>
  <view class="gift-fields" aria-label="往来明细">
    <view class="gift-field">
      <text>所属礼单</text>
      <text>{{ direction === 'EXPENSE' ? '日常送礼' : '日常收礼' }}</text>
    </view>
    <view class="gift-field">
      <text>{{ direction === 'EXPENSE' ? '随礼对象' : '送礼人' }}</text>
      <input
        :value="String(detail.personName ?? '')"
        :aria-label="direction === 'EXPENSE' ? '随礼对象' : '送礼人'"
        :maxlength="60"
        :disabled="disabled"
        @input="update('personName', $event.detail.value)"
      />
    </view>
    <view class="gift-field">
      <text>事由</text>
      <input
        :value="String(detail.reason ?? '')"
        aria-label="事由"
        :maxlength="60"
        :disabled="disabled"
        @input="update('reason', $event.detail.value)"
      />
    </view>
  </view>
</template>

<style scoped>
.gift-fields {
  background: #fff;
  border: 1px solid var(--line, #e4eae5);
  border-radius: 14px;
}
.gift-field {
  min-height: 46px;
  box-sizing: border-box;
  display: grid;
  grid-template-columns: 84px minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  padding: 8px 13px;
  font-size: 14px;
}
.gift-field + .gift-field {
  border-top: 1px solid var(--line, #e4eae5);
}
.gift-field input {
  width: 100%;
  font-size: 14px;
}
</style>
