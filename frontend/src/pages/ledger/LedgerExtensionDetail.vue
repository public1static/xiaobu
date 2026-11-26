<script setup lang="ts">
import { computed } from 'vue';
import type { TransactionExtension } from '../../api/transaction-extensions';
import GiftDetailSection from './GiftDetailSection.vue';
import type { TransactionDirection } from './transaction';

const props = defineProps<{ extension: TransactionExtension; direction: TransactionDirection }>();
const sections = { GIFT: GiftDetailSection };
const component = computed(() => sections[props.extension.type as keyof typeof sections]);
</script>

<template>
  <!-- #ifdef H5 -->
  <component :is="component" :detail="extension.detail" :direction="direction" />
  <!-- #endif -->
  <!-- #ifdef MP-WEIXIN -->
  <GiftDetailSection
    v-if="extension.type === 'GIFT'"
    :detail="extension.detail"
    :direction="direction"
  />
  <!-- #endif -->
</template>
