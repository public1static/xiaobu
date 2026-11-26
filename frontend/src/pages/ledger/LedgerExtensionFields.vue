<script setup lang="ts">
import { computed } from 'vue';
import type { TransactionExtensionDetail } from '../../api/transaction-extensions';
import GiftEntryFields from './GiftEntryFields.vue';
import RentalPaymentEntryFields from './RentalPaymentEntryFields.vue';
import type { TransactionDirection } from './transaction';

const props = defineProps<{
  ledgerId: string;
  type: string;
  detail: TransactionExtensionDetail;
  direction: TransactionDirection;
  bookedOn?: string;
  disabled?: boolean;
  quick?: boolean;
}>();
const emit = defineEmits<{
  (event: 'change', detail: TransactionExtensionDetail | null): void;
  (event: 'date-default', value: string): void;
  (event: 'amount-default', cents: number): void;
}>();
const fields = { GIFT: GiftEntryFields, RENTAL_PAYMENT: RentalPaymentEntryFields };
const component = computed(() => fields[props.type as keyof typeof fields]);
</script>

<template>
  <!-- #ifdef H5 -->
  <component
    :is="component"
    :ledger-id="ledgerId"
    :detail="detail"
    :direction="direction"
    :booked-on="bookedOn"
    :disabled="disabled"
    :quick="quick"
    @change="emit('change', $event)"
    @date-default="emit('date-default', $event)"
    @amount-default="emit('amount-default', $event)"
  />
  <!-- #endif -->
  <!-- #ifdef MP-WEIXIN -->
  <GiftEntryFields
    v-if="type === 'GIFT'"
    :ledger-id="ledgerId"
    :detail="detail"
    :direction="direction"
    :disabled="disabled"
    :quick="quick"
    @change="emit('change', $event)"
    @date-default="emit('date-default', $event)"
  />
  <RentalPaymentEntryFields
    v-else-if="type === 'RENTAL_PAYMENT'"
    :ledger-id="ledgerId"
    :detail="detail"
    :booked-on="bookedOn"
    :disabled="disabled"
    @change="emit('change', $event)"
    @amount-default="emit('amount-default', $event)"
  />
  <!-- #endif -->
</template>
