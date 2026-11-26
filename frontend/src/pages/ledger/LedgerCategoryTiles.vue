<script setup lang="ts">
import { getCurrentInstance, onBeforeUnmount, onMounted, ref } from 'vue';

type Choice = readonly [code: string, name: string];
type TouchPosition = { clientX: number; clientY: number };
type TileBounds = { left: number; right: number; top: number; bottom: number };
type PickEvent = { detail?: number | object };
const props = defineProps<{
  items: readonly Choice[];
  selected: string;
  columns: number;
  sortable?: boolean;
  sortableCount?: number;
  disabled?: boolean;
}>();
const emit = defineEmits<{
  (event: 'select', code: string): void;
  (event: 'move', from: number, to: number): void;
}>();

const instance = getCurrentInstance();
const grid = ref<HTMLElement | { $el: HTMLElement } | null>(null);
const source = ref<number | null>(null);
const destination = ref<number | null>(null);
let press: (TouchPosition & { index: number; pointerId?: number }) | null = null;
let suppressTap = false;
let bounds: TileBounds[] = [];

function canSort(index: number) {
  return !!props.sortable && !props.disabled && index < (props.sortableCount ?? props.items.length);
}

function begin(index: number, point: TouchPosition, pointerId?: number) {
  suppressTap = false;
  if (!canSort(index)) return;
  press = { index, clientX: point.clientX, clientY: point.clientY, pointerId };
}

function track(point: TouchPosition, index: number) {
  if (!press) return;
  if (
    source.value === null &&
    Math.hypot(point.clientX - press.clientX, point.clientY - press.clientY) < 6
  )
    return;
  source.value = press.index;
  destination.value = index >= 0 && canSort(index) ? index : null;
}

function resetDrag() {
  press = null;
  source.value = null;
  destination.value = null;
}

function finishDrag() {
  if (source.value !== null) {
    suppressTap = true;
    if (destination.value !== null && destination.value !== source.value) {
      emit('move', source.value, destination.value);
    }
  }
  resetDrag();
}

// #ifdef H5
let h5Grid: HTMLElement | undefined;

function tileIndex(target: EventTarget | null) {
  if (!(target instanceof Element)) return -1;
  const tile = target.closest('[data-category-index]');
  return tile && h5Grid?.contains(tile) ? Number(tile.getAttribute('data-category-index')) : -1;
}

function onGridKeydown(event: KeyboardEvent) {
  const index = tileIndex(event.target);
  if (index >= 0) keyMove(event, index);
}

function pointerDown(event: PointerEvent) {
  if (!event.isPrimary || event.button !== 0) return;
  const index = tileIndex(event.target);
  if (index < 0) return;
  begin(index, event, event.pointerId);
  if (press) (event.target as HTMLElement).setPointerCapture(event.pointerId);
}

function pointerMove(event: PointerEvent) {
  if (!press || press.pointerId !== event.pointerId) return;
  track(event, tileIndex(document.elementFromPoint(event.clientX, event.clientY)));
}

function pointerUp(event: PointerEvent) {
  if (!press || press.pointerId !== event.pointerId) return;
  pointerMove(event);
  finishDrag();
}

onMounted(() => {
  const element = grid.value;
  h5Grid = element instanceof HTMLElement ? element : element?.$el;
  h5Grid?.addEventListener('keydown', onGridKeydown);
  h5Grid?.addEventListener('pointerdown', pointerDown);
  h5Grid?.addEventListener('pointermove', pointerMove);
  h5Grid?.addEventListener('pointerup', pointerUp);
  h5Grid?.addEventListener('pointercancel', resetDrag);
  h5Grid?.addEventListener('lostpointercapture', resetDrag);
});
onBeforeUnmount(() => {
  h5Grid?.removeEventListener('keydown', onGridKeydown);
  h5Grid?.removeEventListener('pointerdown', pointerDown);
  h5Grid?.removeEventListener('pointermove', pointerMove);
  h5Grid?.removeEventListener('pointerup', pointerUp);
  h5Grid?.removeEventListener('pointercancel', resetDrag);
  h5Grid?.removeEventListener('lostpointercapture', resetDrag);
});
// #endif

function touchStart(event: { touches: TouchPosition[] }, index: number) {
  // #ifdef MP-WEIXIN
  if (event.touches.length !== 1) {
    resetDrag();
    return;
  }
  begin(index, event.touches[0]);
  if (!press) return;
  bounds = [];
  const current = press;
  uni
    .createSelectorQuery()
    .in(instance?.proxy)
    .selectAll('.ledger-category-tile')
    .boundingClientRect((result) => {
      if (press === current && Array.isArray(result)) bounds = result;
    })
    .exec();
  // #endif
}

function touchMove(event: { touches: TouchPosition[]; changedTouches: TouchPosition[] }) {
  // #ifdef MP-WEIXIN
  const point = event.touches[0] ?? event.changedTouches[0];
  if (!press || !point) return;
  const index = bounds.findIndex(
    ({ left, right, top, bottom }) =>
      point.clientX >= left &&
      point.clientX <= right &&
      point.clientY >= top &&
      point.clientY <= bottom,
  );
  track(point, index);
  // #endif
}

function touchEnd(event: { touches: TouchPosition[]; changedTouches: TouchPosition[] }) {
  // #ifdef MP-WEIXIN
  touchMove(event);
  finishDrag();
  // #endif
}

function select(event: PickEvent, code: string) {
  if (suppressTap && event.detail !== 0) {
    suppressTap = false;
    return;
  }
  if (!props.disabled) emit('select', code);
}

function keyMove(event: KeyboardEvent, index: number) {
  const delta = { ArrowLeft: -1, ArrowUp: -1, ArrowRight: 1, ArrowDown: 1 }[event.key];
  if (!delta || !canSort(index)) return;
  event.preventDefault();
  if (index + delta >= 0 && canSort(index + delta)) emit('move', index, index + delta);
}

function icon(code: string) {
  return `/static/ledger/vant/${code.startsWith('CUS_') ? 'CUSTOM' : code}.svg`;
}
</script>

<template>
  <view
    ref="grid"
    class="ledger-category-grid"
    :style="{ gridTemplateColumns: `repeat(${columns}, minmax(0, 1fr))` }"
  >
    <view
      v-for="([code, name], index) in items"
      :key="code"
      class="ledger-category-tile"
      :class="{
        sortable: canSort(index),
        dragging: source === index,
        destination: destination === index && source !== index,
      }"
      :data-category-index="index"
      @touchstart="touchStart($event, index)"
      @touchmove="touchMove"
      @touchend="touchEnd"
      @touchcancel="resetDrag"
    >
      <button
        class="category-option"
        :aria-label="name"
        :title="name"
        :aria-pressed="selected === code"
        :class="{ selected: selected === code }"
        :disabled="disabled"
        @click="select($event, code)"
        :tabindex="disabled ? -1 : 0"
      >
        <image :src="icon(code)" aria-hidden="true" />
        <text class="category-name" :class="{ long: name.length >= 4 }">{{ name }}</text>
      </button>
    </view>
  </view>
</template>

<style scoped>
.ledger-category-grid {
  display: grid;
  grid-auto-rows: 44px;
  gap: 4px;
}
.ledger-category-tile {
  min-width: 0;
}
.category-option {
  display: grid;
  grid-template-rows: 18px auto;
  align-content: center;
  justify-items: center;
  gap: 1px;
  width: 100%;
  height: 100%;
  margin: 0;
  padding: 2px 1px;
  border: 1px solid transparent;
  border-radius: 10px;
  background: #f3f5f1;
  color: #182623;
  font-size: 12px;
  line-height: 1.2;
}
.category-option::after {
  border: 0;
}
.category-option.selected,
.destination .category-option {
  border-color: #176a57;
  background: #eaf4ee;
  color: #176a57;
}
.category-option:focus-visible {
  outline: 2px solid #176a57;
  outline-offset: -2px;
}
.category-option image {
  width: 18px;
  height: 18px;
  pointer-events: none;
}
.category-name {
  display: block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  pointer-events: none;
}
.category-name.long {
  font-size: 11px;
}
.sortable {
  touch-action: none;
  user-select: none;
}
.sortable .category-option {
  cursor: grab;
}
.dragging {
  opacity: 0.65;
}
</style>
