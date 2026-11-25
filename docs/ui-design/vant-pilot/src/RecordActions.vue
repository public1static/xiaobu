<script setup>
import { computed, ref } from 'vue';
import { ActionSheet as VanActionSheet, Button as VanButton } from 'vant';
defineOptions({ inheritAttrs: false });
const props = defineProps({ options: { type: Array, default: () => [] } });
const open = ref(false);
// 保留调用页的可用操作和办理函数，风险操作沿用业务确认。
const actions = computed(() =>
  props.options
    .filter((item) => item.visible !== false && !item.disabled)
    .map((item) => ({ ...item, color: item.danger ? '#a54341' : undefined })),
);
</script>
<template>
  <div class="record-actions" v-bind="$attrs">
    <VanButton
      v-if="actions.length === 1"
      plain
      native-type="button"
      :class="{ 'record-danger': actions[0].danger }"
      @click="actions[0].run()"
      >{{ actions[0].name }}</VanButton
    >
    <VanButton
      v-else-if="actions.length > 1"
      plain
      native-type="button"
      aria-haspopup="dialog"
      @click="open = true"
      >更多操作</VanButton
    >
    <slot />
  </div>
  <VanActionSheet
    v-model:show="open"
    teleport="body"
    title="更多操作"
    cancel-text="取消"
    close-on-click-action
    :actions="actions"
    @select="(item) => item.run()"
  />
</template>
