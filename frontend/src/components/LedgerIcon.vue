<script setup lang="ts">
defineProps<{ src: string }>();
</script>
<template>
  <!-- #ifdef H5 -->
  <view aria-hidden="true" class="ledger-icon" :style="{ backgroundImage: 'url(' + src + ')' }" />
  <!-- #endif -->
  <!-- #ifdef MP-WEIXIN -->
  <image aria-hidden="true" class="ledger-icon" :src="src" mode="aspectFit" />
  <!-- #endif -->
</template>
<style scoped>
.ledger-icon {
  display: inline-block;
  flex: none;
  background-position: center;
  background-size: contain;
  background-repeat: no-repeat;
}
</style>
