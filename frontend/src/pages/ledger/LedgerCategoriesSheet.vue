<script setup lang="ts">
import { computed, ref, onMounted } from 'vue';
import {
  changeLedgerCategoryOrder,
  changeLedgerCategoryStatus,
  createLedgerCategory,
  listLedgerCategories,
  type LedgerCategory,
} from '../../api/ledger-categories';
import { useLedgerCategoryColumns } from './category-layout';
import LedgerCategoryTiles from './LedgerCategoryTiles.vue';
import { ApiRequestError } from '../../api/request';
import type { TransactionDirection } from './transaction';

const props = defineProps<{
  ledgerId: string;
  direction: TransactionDirection;
  selected: string;
  allowManage: boolean;
  initialCatalog: LedgerCategory[] | null;
}>();
const emit = defineEmits<{
  (event: 'close'): void;
  (event: 'select', code: string): void;
  (event: 'catalog', catalog: LedgerCategory[]): void;
}>();
const columns = useLedgerCategoryColumns();
const catalog = ref<LedgerCategory[]>(props.initialCatalog ?? []);
const busy = ref(false);
const maintenanceOpen = ref(false);
const adding = ref(false);
const name = ref('');
const error = ref('');
const active = computed(() =>
  catalog.value.filter((row) => row.direction === props.direction && row.active),
);
const inactive = computed(() =>
  catalog.value.filter((row) => row.direction === props.direction && !row.active),
);

const directionLabel = computed(() => (props.direction === 'INCOME' ? '收入' : '支出'));
const choices = computed<readonly [string, string][]>(() => {
  const historical = inactive.value.find((item) => item.code === props.selected);
  return [...active.value, ...(historical ? [historical] : [])].map(({ code, name }) => [
    code,
    name,
  ]);
});

function toggleAdd() {
  adding.value = !adding.value;
  name.value = '';
  error.value = '';
}

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
  if (busy.value) return;
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

onMounted(refresh);
</script>

<template>
  <view class="overlay category-sheet-overlay" @click.self="!busy && emit('close')">
    <view
      class="sheet category-sheet"
      role="dialog"
      :aria-label="allowManage ? directionLabel + '分类管理' : '选分类'"
      aria-modal="true"
      @keydown.esc.stop="!busy && emit('close')"
    >
      <view class="category-toolbar">
        <button
          class="text-button category-close"
          aria-label="关闭分类"
          :disabled="busy"
          @click="emit('close')"
        >
          ×
        </button>
        <text>{{ directionLabel }}分类{{ allowManage ? '管理' : '' }}</text>
        <button v-if="allowManage" class="text-button" :disabled="busy" @click="toggleAdd">
          {{ adding ? '取消' : '新增' }}
        </button>
      </view>
      <view v-if="adding" class="category-add">
        <text>分类名称<text class="star">*</text></text>
        <input
          v-model="name"
          :focus="adding"
          :maxlength="24"
          aria-label="新分类名称"
          placeholder="输入分类名称"
          confirm-type="done"
          @confirm="add"
          @input="error = ''"
        />
        <button class="text-button" :disabled="busy" @click="add">保存并选用</button>
      </view>
      <text v-if="error" class="error" role="alert">{{ error }}</text>
      <button
        v-if="error && !adding"
        class="text-button category-reload"
        :disabled="busy"
        @click="refresh"
      >
        重新加载
      </button>
      <text class="category-hint">{{ allowManage ? '点选使用，拖动调整顺序' : '点选使用' }}</text>
      <LedgerCategoryTiles
        class="category-sheet-grid"
        :items="choices"
        :columns="columns"
        :selected="selected"
        :sortable="allowManage"
        :sortable-count="active.length"
        :disabled="busy"
        @select="emit('select', $event)"
        @move="move"
      />
      <view v-if="allowManage && choices.length" class="category-maintenance">
        <button
          class="text-button category-reload"
          :aria-expanded="maintenanceOpen"
          @click="maintenanceOpen = !maintenanceOpen"
        >
          {{ maintenanceOpen ? '收起' : '停用与恢复' }}
        </button>
        <template v-if="maintenanceOpen">
          <text class="category-hint">停用不影响历史流水，也可用箭头调整顺序。</text>
          <view v-for="(item, index) in active" :key="item.code" class="category-manage-row">
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
          <text v-if="inactive.length" class="category-hint">已停用</text>
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
      </view>
    </view>
  </view>
</template>

<style scoped src="./ledger.css"></style>
<style scoped>
.category-sheet-overlay {
  z-index: 4;
  background: #000b;
}
.category-sheet {
  gap: 7px;
  padding-top: 6px;
  background: #fff;
  max-height: 80vh;
  max-height: 80dvh;
}
.category-sheet-grid {
  margin-inline: 14px;
  overflow-y: auto;
  flex-shrink: 1;
  min-height: 88px;
  overscroll-behavior: contain;
}
.category-toolbar {
  display: grid;
  grid-template-columns: 50px 1fr 50px;
  align-items: center;
  text-align: center;
  font-size: 16px;
  font-weight: 650;
}
.category-toolbar .text-button {
  width: 100%;
  padding: 0;
  font-weight: 400;
}
.category-toolbar .category-close {
  font-size: 23px;
  justify-self: start;
  width: 40px;
}
.category-add,
.category-manage-row {
  display: flex;
  gap: 8px;
  align-items: center;
  border-bottom: 1px solid var(--line);
  font-size: 14px;
}
.category-add {
  display: grid;
  grid-template-columns: 84px minmax(0, 1fr) auto;
  gap: 10px;
}
.category-add input {
  flex: 1;
  width: 100%;
  min-width: 0;
}
.category-hint {
  display: block;
  margin-bottom: 3px;
  color: var(--muted);
  font-size: 13px;
}
.category-reload {
  width: auto;
  margin: 0;
  padding: 0;
  align-self: flex-start;
}
.category-maintenance .category-reload {
  color: var(--muted);
  font-size: 12px;
  min-height: 32px;
  margin-left: 14px;
  margin-right: auto;
}
.category-name {
  flex: 1;
  min-width: 0;
  overflow-wrap: anywhere;
}
</style>
