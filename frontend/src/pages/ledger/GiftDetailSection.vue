<script setup lang="ts">
import type { TransactionExtensionDetail } from '../../api/transaction-extensions';
import type { TransactionDirection } from './transaction';

defineProps<{ detail: TransactionExtensionDetail; direction: TransactionDirection }>();
</script>

<template>
  <view class="gift-detail" aria-label="往来信息">
    <text class="gift-title">往来信息</text>
    <view class="gift-line"
      ><text>礼单</text><text>{{ detail.bookName }}</text></view
    >
    <view v-if="detail.personName" class="gift-line">
      <text>{{ direction === 'EXPENSE' ? '随礼对象' : '送礼人' }}</text>
      <text>{{ [detail.personName, detail.distinction].filter(Boolean).join(' · ') }}</text>
    </view>
    <view v-if="detail.reason" class="gift-line"
      ><text>事由</text><text>{{ detail.reason }}</text></view
    >
  </view>
</template>

<style scoped>
.gift-detail {
  padding: 10px 13px;
  border: 1px solid var(--line, #e4eae5);
  border-radius: 14px;
  background: #fff;
}
.gift-title {
  font-size: 14px;
  font-weight: 700;
}
.gift-line {
  display: grid;
  grid-template-columns: 84px minmax(0, 1fr);
  gap: 10px;
  padding-top: 8px;
  font-size: 14px;
  overflow-wrap: anywhere;
}
.gift-line text:first-child {
  color: var(--muted, #536660);
}
</style>
