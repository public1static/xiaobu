<script setup>
import { computed, inject, ref } from 'vue';
import OptionManager from './OptionManager.vue';
import { Icon as VanIcon } from 'vant';
import { normalizeContact } from './gift-options.mjs';
defineProps({ label: { type: String, default: '事由' } });
const model = defineModel({ type: String, default: '' });
const directory = inject('reasonDirectory');
const open = ref(false),
  opener = ref(null);
// 编辑旧流水时保留原来的自由填写值。
const options = computed(() => {
  const values = directory.values.value;
  return model.value && !values.includes(model.value) ? [...values, model.value] : values;
});
// 保存并选用后回到原来的记礼表单。
function add(value) {
  const name = value.trim();
  if (!name || name.length > 60) return '请输入 1 到 60 字的事由名称。';
  const existing = directory.values.value.some(
    (x) => normalizeContact(x) === normalizeContact(name),
  );
  if (existing) return '已有同名事由。';
  if (!directory.save([...directory.values.value, name])) return '事由保存失败。';
  model.value = name;
  open.value = false;
}
// 事由顺序保存在当前账本，两个记礼入口即时共享。
function move(from, to) {
  const items = [...options.value];
  items.splice(to, 0, items.splice(from, 1)[0]);
  if (!directory.save(items)) return '排序保存失败。';
}
</script>
<template>
  <section class="reason-picker" :aria-label="label">
    <div class="reason-head">
      <span>{{ label }}</span><button
        ref="opener"
        type="button"
        aria-haspopup="dialog"
        :aria-expanded="open"
        @click="open = true"
      >
        事由管理 <VanIcon name="arrow" />
      </button>
    </div>
    <div class="reason-chips">
      <button
        v-for="item in options"
        :key="item"
        type="button"
        class="reason-chip"
        :data-reason="item"
        :aria-pressed="model === item"
        @click="model = model === item ? '' : item"
      >
        {{ item }}
      </button>
    </div>
    <OptionManager
      v-model="open"
      label="事由"
      title="事由管理"
      :items="options"
      :selected="model"
      :add="add"
      :move="move"
      @select="
        model = $event;
        open = false;
      "
      @closed="opener?.focus({ preventScroll: true })"
    />
  </section>
</template>
<style scoped>
.reason-picker {
  padding: 6px 0 10px;
  min-width: 0;
  font-size: 14px;
}
.reason-head,
.reason-add {
  display: flex;
  align-items: center;
  gap: 8px;
}
.reason-head > span {
  flex: 1;
}
.reason-head button,
.reason-add button {
  background: transparent;
  border: 0;
  color: var(--green);
  font: inherit;
  font-size: 13px;
  padding: 7px 4px;
  cursor: pointer;
}
</style>
