<script setup lang="ts">
import { onMounted, ref, watch } from 'vue';
const props = defineProps<{ anchor?: string }>();
// #ifdef H5
const panel = ref();
// 在当前消息区域内定位回答。页面离开时没有待执行的滚动任务。
function reveal() {
  if (!props.anchor) return;
  const element = panel.value?.$el ?? panel.value;
  element?.querySelector('#' + CSS.escape(props.anchor))?.scrollIntoView({ block: 'nearest' });
}
onMounted(reveal);
watch(() => props.anchor, reveal, { flush: 'post' });
// #endif
</script>
<template>
  <!-- #ifdef H5 -->
  <view ref="panel" class="chat-scroll"><slot /></view>
  <!-- #endif -->
  <!-- #ifdef MP-WEIXIN -->
  <scroll-view scroll-y :scroll-into-view="anchor"><slot /></scroll-view>
  <!-- #endif -->
</template>
<style scoped>
.chat-scroll {
  overflow-y: auto;
  overscroll-behavior: contain;
}
</style>
