<script setup lang="ts">
import { computed } from 'vue';
import type { TransactionExtensionDetail } from '../../api/transaction-extensions';
import GiftEntryFields from './GiftEntryFields.vue';
import type { TransactionDirection } from './transaction';

const props = defineProps<{
  ledgerId: string;
  type: string;
  detail: TransactionExtensionDetail;
  direction: TransactionDirection;
  disabled?: boolean;
  quick?: boolean;
}>();
const emit = defineEmits<{
  (event: 'change', detail: TransactionExtensionDetail): void;
  (event: 'date-default', value: string): void;
}>();
const fields = { GIFT: GiftEntryFields };
const component = computed(() => fields[props.type as keyof typeof fields]);
</script>

<template>
  <!-- #ifdef H5 -->
  <component
    :is="component"
    :ledger-id="ledgerId"
    :detail="detail"
    :direction="direction"
    :disabled="disabled"
    :quick="quick"
    @change="emit('change', $event)"
    @date-default="emit('date-default', $event)"
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
  <!-- #endif -->
</template>
