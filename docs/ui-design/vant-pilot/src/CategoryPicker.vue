<script setup>
import { inject, computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { Icon as VanIcon } from 'vant';
import OptionManager from './OptionManager.vue';
import {
  addCategory,
  loadCategoryCatalog,
  persistCategoryCatalog,
  reorderCategories,
} from './CategoryCatalog.mjs';
const bookStorage = inject('bookStorage');

const props = defineProps({
  modelValue: { type: String, default: '' },
  kind: { type: String, required: true },
  scope: { type: String, default: 'personal' },
  required: Boolean,
});
const emit = defineEmits(['update:modelValue']);
const catalogue = ref(loadCategoryCatalog(bookStorage));
const open = ref(false);
const rail = ref(null),
  opener = ref(null);
const items = computed(() => catalogue.value[props.kind] ?? []);
const railItems = computed(() =>
  props.modelValue && !items.value.includes(props.modelValue)
    ? [...items.value, props.modelValue]
    : items.value,
);
// 每屏按目录顺序从左到右排列两行，后续分类可继续横滑。
// 依照实际可用宽度分列，保持常用分类两行并保留可点击面积。
const columns = ref(6),
  railWidth = ref(0);
let sizeObserver;
onMounted(() => {
  sizeObserver = new ResizeObserver(([entry]) => {
    railWidth.value = entry.contentRect.width;
    columns.value = Math.max(1, Math.floor((entry.contentRect.width + 4) / 48));
  });
  sizeObserver.observe(rail.value);
});
onBeforeUnmount(() => sizeObserver?.disconnect());
const categoryPages = computed(() => {
  const pages = [],
    pageSize = columns.value * 2;
  for (let start = 0; start < railItems.value.length; start += pageSize) {
    pages.push(railItems.value.slice(start, start + pageSize));
  }
  return pages;
});
const type = computed(() => (props.kind === 'income' ? '收入' : '支出'));
const icons = {
  餐饮: 'fire-o',
  买菜: 'shopping-cart-o',
  交通: 'logistics',
  日用品: 'bag-o',
  住房: 'wap-home-o',
  购物: 'bag-o',
  娱乐: 'music-o',
  旅行: 'guide-o',
  医疗: 'like-o',
  教育: 'certificate',
  随礼: 'gift-o',
  其它: 'ellipsis',
  工资: 'balance-o',
  奖金: 'gold-coin-o',
  兼职: 'records',
  收礼: 'gift-o',
  利息: 'points',
};
function revealSelection() {
  const box = rail.value,
    active = box?.querySelector('[aria-pressed="true"]');
  if (!active || !box) return;
  if (active.offsetLeft < box.scrollLeft) box.scrollLeft = active.offsetLeft - 5;
  else if (active.offsetLeft + active.offsetWidth > box.scrollLeft + box.clientWidth)
    box.scrollLeft = active.offsetLeft + active.offsetWidth - box.clientWidth + 5;
}
watch(
  () => [props.kind, props.modelValue],
  ([kind], before) => {
    if (before && kind !== before[0]) {
      if (rail.value) rail.value.scrollLeft = 0;
    }
    nextTick(revealSelection);
  },
  { immediate: true, flush: 'post' },
);
function selectCategory(value, close = false) {
  emit('update:modelValue', value);
  if (close) open.value = false;
}
// 每次打开时直接展示可选择和拖动的分类网格。
function showPanel() {
  // 弹层打开前按当前表单实宽同步，兼容从隐藏状态展开的记账页。
  railWidth.value = rail.value.clientWidth;
  columns.value = Math.max(1, Math.floor((railWidth.value + 4) / 48));
  open.value = true;
}
// 分类新增和排序均沿用当前账本、当前收支方向的目录。
function store(result) {
  if (result.error) return result.error;
  const saved = persistCategoryCatalog(bookStorage, result.value);
  if (saved.error) return saved.error;
  catalogue.value = saved.value;
}
function add(name) {
  const result = addCategory(catalogue.value, props.kind, name);
  const error = store(result);
  if (!error) selectCategory(result.label, true);
  return error;
}
function move(from, to) {
  return store({ value: reorderCategories(catalogue.value, props.kind, from, to) });
}
defineExpose({
  focus: () =>
    (
      rail.value?.querySelector('[aria-pressed="true"]') ??
      rail.value?.querySelector('button') ??
      opener.value
    )?.focus(),
  isAvailable: (value) => railItems.value.includes(value),
});
</script>

<template>
  <section class="pilot-entry-categories" aria-labelledby="pilot-entry-category-label">
    <div class="pilot-category-head">
      <h2 id="pilot-entry-category-label">
        分类<span v-if="required" class="field-mark" aria-hidden="true">*</span
        ><span v-if="required" class="pilot-category-sr">（必填）</span>
      </h2>
      <button
        ref="opener"
        class="pilot-category-more"
        type="button"
        aria-haspopup="dialog"
        :aria-expanded="open"
        @click="showPanel"
      >
        分类管理 <VanIcon name="arrow" aria-hidden="true" />
      </button>
    </div>
    <div
      ref="rail"
      class="pilot-entry-category-rail"
      :style="{ '--columns': columns }"
      :aria-label="type + '快捷分类'"
    >
      <div class="category-page category-options" v-for="page in categoryPages" :key="page[0]">
        <button
          class="category-option"
          v-for="item in page"
          :key="item"
          type="button"
          :title="item"
          :aria-pressed="modelValue === item"
          @click="selectCategory(item)"
        >
          <span aria-hidden="true"><VanIcon :name="icons[item] || 'apps-o'" /></span
          ><span class="category-name" :class="{ long: item.length >= 4 }">{{ item }}</span>
        </button>
      </div>
    </div>
    <OptionManager
      v-model="open"
      label="分类"
      :title="type + '分类管理'"
      :items="railItems"
      :sortable-count="items.length"
      :selected="modelValue"
      :icons="icons"
      :columns="columns"
      :rail-width="railWidth"
      :add="add"
      :move="move"
      @select="selectCategory($event, true)"
      @closed="opener?.focus({ preventScroll: true })"
    />
  </section>
</template>

<style scoped>
.pilot-entry-categories {
  min-width: 0;
  padding: 0 13px 10px;
  border: 1px solid var(--entry-rule);
  border-radius: 14px;
  background: white;
}
.pilot-category-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 37px;
  margin-bottom: 3px;
}
.pilot-category-head h2 {
  color: var(--entry-ink);
  font: inherit;
  font-size: 14px;
  font-weight: 400;
  margin: 0;
}
.pilot-category-toolbar h2 {
  color: #202c2b;
  font-size: 0.91rem;
  margin: 0;
  font-weight: 650;
}
.pilot-category-more,
.pilot-category-action {
  background: transparent;
  border: 0;
  min-height: 44px;
  min-width: 44px;
  padding: 0 4px;
  color: #176a57;
  font: inherit;
  font-size: 0.875rem;
}
.pilot-category-more {
  display: flex;
  align-items: center;
  gap: 3px;
}
.pilot-category-required {
  color: #a2303d;
}
.pilot-entry-category-rail {
  display: flex;
  overflow-x: auto;
  gap: 6px;
  padding: 2px 0;
  position: relative;
  scroll-snap-type: x mandatory;
  scrollbar-color: #d1dcd8 transparent;
  scrollbar-width: thin;
  overscroll-behavior-inline: contain;
}
.category-page {
  flex: 0 0 100%;
  min-width: 0;
  scroll-snap-align: start;
}
.pilot-category-sr {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
}
</style>
