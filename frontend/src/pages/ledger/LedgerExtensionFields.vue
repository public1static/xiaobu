<script setup lang="ts">
import { computed } from 'vue';
import type { TransactionExtensionDetail } from '../../api/transaction-extensions';
import GiftEntryFields from './GiftEntryFields.vue';
import type { TransactionDirection } from './transaction';

const props = defineProps<{
  type: string;
  detail: TransactionExtensionDetail;
  direction: TransactionDirection;
  disabled?: boolean;
}>();
const emit = defineEmits<{ (event: 'change', detail: TransactionExtensionDetail): void }>();
const fields = { GIFT: GiftEntryFields };
const component = computed(() => fields[props.type as keyof typeof fields]);
</script>

<template>
  <!-- #ifdef H5 -->
  <component
    :is="component"
    :detail="detail"
    :direction="direction"
    :disabled="disabled"
    @change="emit('change', $event)"
  />
  <!-- #endif -->
  <!-- #ifdef MP-WEIXIN -->
  <GiftEntryFields
    v-if="type === 'GIFT'"
    :detail="detail"
    :direction="direction"
    :disabled="disabled"
    @change="emit('change', $event)"
  />
  <!-- #endif -->
</template>
