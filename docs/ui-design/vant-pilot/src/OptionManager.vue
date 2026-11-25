<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue';
import { Form as VanForm, Field as VanField, Icon as VanIcon, Popup as VanPopup } from 'vant';
const open = defineModel({ type: Boolean });
const props = defineProps({
  label: String,
  title: String,
  items: Array,
  sortableCount: Number,
  selected: String,
  icons: Object,
  columns: Number,
  railWidth: Number,
  add: Function,
  move: Function,
});
const emit = defineEmits(['select', 'closed']);
const adding = ref(false),
  name = ref(''),
  error = ref(''),
  input = ref(null),
  grid = ref(null),
  closeButton = ref(null);
const source = ref(''),
  target = ref('');
const count = computed(() => props.sortableCount ?? props.items.length);
let press = null,
  skipClick = false,
  frame = 0;
// 弹层打开时直接展示可拖动的选项。
watch(open, () => {
  resetDrag();
  adding.value = false;
  name.value = '';
  error.value = '';
  skipClick = false;
});
function toggleAdd() {
  adding.value = !adding.value;
  name.value = '';
  error.value = '';
  if (adding.value) nextTick(() => input.value?.focus());
}
function save() {
  error.value = props.add(name.value) || '';
}
// 区分轻点和拖动，只有移动超过阈值才进入排序。
function begin(e, item, index) {
  skipClick = false;
  if (index >= count.value || e.button !== 0 || !e.isPrimary) return;
  press = { item, id: e.pointerId, x: e.clientX, y: e.clientY, lastX: e.clientX, lastY: e.clientY };
  e.currentTarget.setPointerCapture(e.pointerId);
}
// 目标须属于当前弹层，松开时才写入目录。
function trackTarget() {
  const tile = document.elementFromPoint(press.lastX, press.lastY)?.closest('[data-option]');
  target.value =
    grid.value?.contains(tile) && props.items.indexOf(tile.dataset.option) < count.value
      ? tile.dataset.option
      : '';
}
function scrollEdge() {
  frame = 0;
  if (!press || !source.value || !grid.value) return;
  const box = grid.value,
    rect = box.getBoundingClientRect(),
    y = press.lastY;
  const step = y < rect.top + 25 ? -8 : y > rect.bottom - 25 ? 8 : 0;
  const before = box.scrollTop;
  box.scrollTop += step;
  if (box.scrollTop !== before) {
    trackTarget();
    frame = requestAnimationFrame(scrollEdge);
  }
}
function track(e) {
  if (!press || press.id !== e.pointerId) return;
  press.lastX = e.clientX;
  press.lastY = e.clientY;
  if (!source.value && Math.hypot(e.clientX - press.x, e.clientY - press.y) < 6) return;
  source.value = press.item;
  trackTarget();
  if (!frame) frame = requestAnimationFrame(scrollEdge);
}
function resetDrag() {
  cancelAnimationFrame(frame);
  frame = 0;
  press = null;
  source.value = '';
  target.value = '';
}
function finish(e) {
  if (!press || press.id !== e.pointerId) return;
  track(e);
  if (source.value) {
    skipClick = true;
    if (target.value && target.value !== source.value)
      error.value =
        props.move(props.items.indexOf(source.value), props.items.indexOf(target.value)) || '';
  }
  resetDrag();
}
// 键盘用户可用方向键调整当前项的位置。
function keyMove(e, index) {
  const delta = { ArrowLeft: -1, ArrowUp: -1, ArrowRight: 1, ArrowDown: 1 }[e.key];
  if (!delta) return;
  e.preventDefault();
  if (index < count.value && index + delta >= 0 && index + delta < count.value)
    error.value = props.move(index, index + delta) || '';
}
function select(e, item) {
  if (skipClick && e.detail) {
    skipClick = false;
    return;
  }
  emit('select', item);
}
onBeforeUnmount(resetDrag);
</script>
<template>
  <VanPopup
    v-model:show="open"
    round
    position="bottom"
    teleport="body"
    class="option-manager"
    role="dialog"
    aria-modal="true"
    :aria-label="title"
    @opened="closeButton?.focus({ preventScroll: true })"
    @closed="emit('closed')"
  >
    <div class="panel" @keydown.esc.stop="open = false">
      <header>
        <button
          ref="closeButton"
          type="button"
          :aria-label="'关闭' + label + '管理'"
          @click="open = false"
        >
          <VanIcon name="cross" /></button
        ><strong>{{ title }}</strong
        ><button type="button" @click="toggleAdd">{{ adding ? '取消' : '新增' }}</button>
      </header>
      <VanForm
        scroll-to-error
        validate-trigger="onSubmit"
        v-if="adding"
        class="option-entry"
        @submit="save"
      >
        <VanField
          ref="input"
          :error-message="error"
          name="optionName"
          :rules="[
            { required: true, formatter: (v) => v.trim(), message: '请输入' + label + '名称' },
          ]"
          v-model="name"
          :label="label + '名称'"
          required
          aria-required="true"
          :maxlength="icons ? 16 : 60"
          :placeholder="'输入' + label + '名称'"
          @update:model-value="error = ''"
        />
        <button type="submit">保存并选用</button>
      </VanForm>
      <p v-if="!adding && error" role="alert">{{ error }}</p>
      <small>点选使用，拖动调整顺序</small>
      <div
        ref="grid"
        class="manager-options"
        :class="icons ? 'category-options' : 'reason-chips'"
        :style="icons ? { '--columns': columns, width: railWidth + 'px' } : undefined"
      >
        <button
          v-for="(item, i) in items"
          :key="item"
          :data-option="item"
          :title="item"
          :class="[
            icons ? 'category-option' : 'reason-chip',
            { dragging: source === item, destination: source !== item && target === item },
          ]"
          type="button"
          :aria-pressed="selected === item"
          @pointerdown="begin($event, item, i)"
          @pointermove="track"
          @pointerup="finish"
          @pointercancel="resetDrag"
          @keydown="keyMove($event, i)"
          @click="select($event, item)"
        >
          <template v-if="icons"
            ><span aria-hidden="true"><VanIcon :name="icons[item] || 'apps-o'" /></span
            ><span class="category-name" :class="{ long: item.length >= 4 }">{{
              item
            }}</span></template
          ><template v-else>{{ item }}</template>
        </button>
      </div>
    </div>
  </VanPopup>
</template>
<style scoped>
.option-manager.van-popup {
  left: 0;
  right: 0;
  margin: auto;
  width: min(100%, 560px);
  background: var(--surface);
}
.panel {
  padding: 6px 16px max(16px, env(safe-area-inset-bottom));
  max-height: 80dvh;
  display: flex;
  flex-direction: column;
}
header {
  display: grid;
  grid-template-columns: 50px 1fr 50px;
  align-items: center;
  text-align: center;
}
header button,
form button {
  background: transparent;
  border: 0;
  color: var(--green);
  min-height: 44px;
  cursor: pointer;
}
header button:first-child {
  justify-self: start;
  width: 40px;
  font-size: 18px;
}
small {
  display: block;
  margin: 4px 0 10px;
  color: var(--muted);
}
.manager-options {
  overflow: auto;
  overscroll-behavior: contain;
  max-width: 100%;
  margin-inline: auto;
}
.manager-options.reason-chips {
  width: 100%;
}
.manager-options button {
  touch-action: none;
  user-select: none;
  cursor: grab;
}
.manager-options button.destination {
  border-color: var(--green);
  background: var(--mint);
}
.manager-options button.dragging {
  opacity: 0.65;
}
.option-entry {
  display: flex;
  align-items: center;
  gap: 8px;
  border-bottom: 1px solid var(--line);
}
.option-entry :deep(.van-field) {
  flex: 1;
  min-width: 0;
  padding-inline: 0;
}
.option-entry > button {
  flex: none;
}
.option-entry :deep(.van-cell::after) {
  display: none;
}
p {
  color: var(--danger);
}
</style>
