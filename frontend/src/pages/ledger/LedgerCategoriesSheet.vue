<script setup lang="ts">
import { computed, ref, onMounted } from 'vue';
import {
  changeLedgerCategoryOrder,
  changeLedgerCategoryStatus,
  createLedgerCategory,
  listLedgerCategories,
  type LedgerCategory,
} from '../../api/ledger-categories';
import { ApiRequestError } from '../../api/request';
import type { TransactionDirection } from './transaction';

const props = defineProps<{
  ledgerId: string;
  direction: TransactionDirection;
  selected: string;
  allowManage: boolean;
}>();
const emit = defineEmits<{
  (event: 'close'): void;
  (event: 'select', code: string): void;
  (event: 'catalog', catalog: LedgerCategory[]): void;
}>();
const catalog = ref<LedgerCategory[]>([]);
const busy = ref(false);
const managing = ref(false);
const adding = ref(false);
const name = ref('');
const error = ref('');
const dragFrom = ref<number | null>(null);
const dragTo = ref<number | null>(null);
const active = computed(() =>
  catalog.value.filter((row) => row.direction === props.direction && row.active),
);
const inactive = computed(() =>
  catalog.value.filter((row) => row.direction === props.direction && !row.active),
);

function replaceCatalog(rows: LedgerCategory[]) {
  catalog.value = rows;
  emit('catalog', rows);
}

async function refresh() {
  busy.value = true;
  error.value = '';
  try {
    replaceCatalog(await listLedgerCategories(props.ledgerId));
  } catch {
    error.value = '分类加载失败，请重试。';
  } finally {
    busy.value = false;
  }
}

async function add() {
  const label = name.value.trim();
  if (!label) {
    error.value = '请输入分类名称。';
    return;
  }
  busy.value = true;
  error.value = '';
  try {
    const item = await createLedgerCategory(props.ledgerId, props.direction, label);
    replaceCatalog([...catalog.value, item]);
    emit('select', item.code);
  } catch (failure) {
    error.value =
      failure instanceof ApiRequestError && failure.status === 409
        ? '已有同名分类或达到上限；可恢复停用分类。'
        : '未能新增分类，请重试。';
  } finally {
    busy.value = false;
  }
}

async function move(from: number, to: number) {
  if (busy.value || from === to || to < 0 || to >= active.value.length) return;
  const codes = active.value.map((item) => item.code);
  codes.splice(to, 0, codes.splice(from, 1)[0]);
  busy.value = true;
  error.value = '';
  try {
    replaceCatalog(await changeLedgerCategoryOrder(props.ledgerId, props.direction, codes));
  } catch {
    error.value = '排序未保存，请重新加载目录。';
  } finally {
    busy.value = false;
  }
}

async function setActive(row: LedgerCategory, enabled: boolean) {
  busy.value = true;
  error.value = '';
  try {
    replaceCatalog(await changeLedgerCategoryStatus(props.ledgerId, row.code, enabled));
  } catch (failure) {
    error.value =
      failure instanceof ApiRequestError && failure.status === 409
        ? '每个方向至少保留一个可用分类。'
        : '状态未保存，请重新加载目录。';
  } finally {
    busy.value = false;
  }
}

function startDrag(event: PointerEvent, index: number) {
  // #ifdef H5
  if (busy.value) return;
  dragFrom.value = index;
  dragTo.value = index;
  (event.currentTarget as HTMLElement)?.setPointerCapture?.(event.pointerId);
  // #endif
}
function updateDrag(event: PointerEvent) {
  // #ifdef H5
  if (dragFrom.value === null) return;
  const row = document
    .elementFromPoint(event.clientX, event.clientY)
    ?.closest('[data-category-order]');
  const index = row?.getAttribute('data-category-order');
  if (index != null) dragTo.value = Number(index);
  // #endif
}
function resetDrag() {
  dragFrom.value = null;
  dragTo.value = null;
}
function endDrag() {
  if (dragFrom.value !== null && dragTo.value !== null) void move(dragFrom.value, dragTo.value);
  resetDrag();
}

onMounted(refresh);
</script>

<template>
  <view class="overlay category-sheet-overlay">
    <view class="sheet login-sheet" role="dialog" aria-label="选分类" aria-modal="true">
      <view class="sheet-head">
        <text>{{ direction === 'INCOME' ? '收入' : '支出' }}分类</text>
        <button class="close" aria-label="关闭分类" :disabled="busy" @click="emit('close')">
          ×
        </button>
      </view>
      <view v-if="allowManage && !adding" class="category-tools">
        <button class="text-button" :disabled="busy" @click="managing = !managing">
          {{ managing ? '完成管理' : '管理' }}
        </button>
        <button
          class="text-button"
          :disabled="busy"
          @click="
            adding = true;
            error = '';
          "
        >
          新增分类
        </button>
      </view>
      <view v-if="adding" class="meta">
        <view class="meta-row">
          <text>新分类</text>
          <input v-model="name" :maxlength="24" aria-label="新分类名称" placeholder="输入名称" />
        </view>
        <view class="category-tools">
          <button
            class="text-button"
            :disabled="busy"
            @click="
              adding = false;
              error = '';
            "
          >
            取消新增
          </button>
          <button class="text-button" :disabled="busy" @click="add">保存分类</button>
        </view>
      </view>
      <view v-if="!managing" class="category-grid">
        <button
          v-for="item in active"
          :key="item.code"
          class="category choice"
          :class="{ selected: item.code === selected }"
          :aria-pressed="item.code === selected"
          :disabled="busy"
          @click="emit('select', item.code)"
        >
          <text>{{ item.name }}</text>
        </button>
      </view>
      <template v-else>
        <text class="preview-label"
          >拖动手柄调整快捷顺序，或用上移、下移。停用不影响历史流水。</text
        >
        <view
          v-for="(item, index) in active"
          :key="item.code"
          :data-category-order="index"
          class="category-manage-row"
          :class="{ 'drag-target': dragTo === index }"
        >
          <!-- #ifdef H5 -->
          <span
            class="drag-handle"
            aria-hidden="true"
            @pointerdown.prevent="startDrag($event, index)"
            @pointermove="updateDrag"
            @pointerup="endDrag"
            @pointercancel="resetDrag"
            >⠿</span
          >
          <!-- #endif -->
          <text class="category-name">{{ item.name }}</text>
          <button
            class="text-button"
            :disabled="busy || index === 0"
            :aria-label="'上移' + item.name"
            @click="move(index, index - 1)"
          >
            ↑
          </button>
          <button
            class="text-button"
            :disabled="busy || index === active.length - 1"
            :aria-label="'下移' + item.name"
            @click="move(index, index + 1)"
          >
            ↓
          </button>
          <button
            class="text-button"
            :disabled="busy"
            :aria-label="'停用' + item.name"
            @click="setActive(item, false)"
          >
            停用
          </button>
        </view>
        <text v-if="inactive.length" class="preview-label">已停用</text>
        <view v-for="item in inactive" :key="item.code" class="category-manage-row">
          <text class="category-name">{{ item.name }}</text>
          <button
            class="text-button"
            :disabled="busy"
            :aria-label="'恢复' + item.name"
            @click="setActive(item, true)"
          >
            恢复
          </button>
        </view>
      </template>
      <text v-if="error" class="error" role="alert">{{ error }}</text>
      <button v-if="error && !adding" class="text-button" :disabled="busy" @click="refresh">
        重新加载
      </button>
    </view>
  </view>
</template>

<style scoped src="./ledger.css"></style>
<style scoped>
.category-sheet-overlay {
  z-index: 4;
}
.category-tools,
.category-manage-row {
  display: flex;
  align-items: center;
  gap: 6px;
}
.category-tools {
  justify-content: flex-end;
}
.category-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(66px, 1fr));
  gap: 7px;
}
.choice {
  min-width: 0;
  min-height: 46px;
  overflow-wrap: anywhere;
}
.category-manage-row {
  padding: 3px 4px;
  border-bottom: 1px solid var(--line);
}
.category-manage-row .text-button {
  min-width: 36px;
  padding-inline: 3px;
}
.category-name {
  flex: 1;
  min-width: 0;
  overflow-wrap: anywhere;
}
.drag-handle {
  padding: 8px 6px;
  cursor: grab;
  user-select: none;
  touch-action: none;
}
.drag-target {
  background: #eaf3ed;
}
</style>
