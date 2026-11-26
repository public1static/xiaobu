<script setup lang="ts">
import LedgerCategoryTiles from './LedgerCategoryTiles.vue';
import { computed, ref, watch } from 'vue';
import { listGiftReasons, saveGiftReasons } from '../../api/ledger-gifts';
import { ApiRequestError } from '../../api/request';
const props = defineProps<{
  ledgerId: string;
  value: string;
  disabled?: boolean;
  label?: string;
}>();
const emit = defineEmits<{ select: [value: string] }>();
const names = ref<string[]>([]),
  open = ref(false),
  adding = ref(false),
  name = ref(''),
  error = ref(''),
  busy = ref(false);
// 忽略早于当前账本切换或保存操作开始的加载结果。
let generation = 0;
const options = computed(() =>
  props.value && !names.value.includes(props.value) ? [...names.value, props.value] : names.value,
);
// 打开不同账本时重新读取，旧流水中的自由文本仍在当前表单可选。
async function load() {
  const ledger = props.ledgerId;
  const request = ++generation;
  try {
    const values = await listGiftReasons(ledger);
    if (request === generation && ledger === props.ledgerId) names.value = values;
  } catch {
    if (request === generation && ledger === props.ledgerId) error.value = '事由加载失败，请重试。';
  }
}
watch(
  () => props.ledgerId,
  () => {
    names.value = [];
    error.value = '';
    void load();
  },
  { immediate: true },
);
// 只有服务端确认后才回填或调整顺序。
async function save(next: string[], selected?: string) {
  if (busy.value) return;
  ++generation;
  busy.value = true;
  error.value = '';
  try {
    names.value = await saveGiftReasons(props.ledgerId, names.value, next);
    if (selected) {
      emit('select', selected);
      open.value = false;
    }
  } catch (e) {
    error.value =
      e instanceof ApiRequestError && e.status === 409
        ? '事由已更新，请按最新顺序重试。'
        : '未能保存事由，请重试。';
    await load();
  } finally {
    busy.value = false;
  }
}
// 校验名称后加入账本目录。
function add() {
  const value = name.value.trim();
  if (!value || names.value.some((n) => n.toLowerCase() === value.toLowerCase())) {
    error.value = value ? '已有同名事由。' : '请输入事由名称。';
    return;
  }
  void save([...names.value, value], value);
}
// 移动单项，不改变其余事由的相对位置。
function move(index: number, destination: number) {
  const next = [...names.value];
  const [item] = next.splice(index, 1);
  next.splice(destination, 0, item);
  void save(next);
}
// 管理面板始终从可选择的目录开始。
function manage() {
  adding.value = false;
  name.value = '';
  error.value = '';
  open.value = true;
  void load();
}
</script>
<template>
  <view class="reason-field"
    ><view class="reason-header"
      ><text>{{ label || '事由' }}</text
      ><button :disabled="disabled" @click="manage">事由管理 ›</button></view
    >
    <view class="chips"
      ><button
        v-for="r in options"
        :key="r"
        :aria-pressed="value === r"
        :class="{ active: value === r }"
        :disabled="disabled"
        @click="emit('select', value === r ? '' : r)"
      >
        {{ r }}
      </button></view
    >
    <text v-if="error && !open" class="error">{{ error }}</text>
    <view v-if="open" class="overlay reason-overlay" @click.self="!busy && (open = false)"
      ><view class="sheet" role="dialog" aria-label="事由管理"
        ><view class="reason-toolbar"
          ><button aria-label="关闭事由管理" :disabled="busy" @click="open = false">×</button
          ><text>事由管理</text
          ><button
            :disabled="busy"
            @click="
              adding = !adding;
              error = '';
            "
          >
            {{ adding ? '取消' : '新增' }}
          </button></view
        >
        <view v-if="adding" class="reason-add"
          ><text>事由名称<text class="star">*</text></text
          ><input
            v-model="name"
            aria-label="事由名称"
            placeholder="输入事由名称"
            :maxlength="60"
            @confirm="add"
          /><button :disabled="busy" @click="add">保存并选用</button></view
        >
        <text v-if="error" class="error" role="alert">{{ error }}</text>
        <text class="hint">点选使用，拖动调整顺序</text>
        <LedgerCategoryTiles
          class="reason-list"
          :items="names.map((n) => [n, n] as const)"
          :selected="value"
          :columns="1"
          text-only
          sortable
          :disabled="busy"
          @move="move"
          @select="
            emit('select', $event);
            open = false;
          "
        /> </view></view
  ></view>
</template>
<style scoped src="./ledger.css"></style>
<style scoped>
.reason-field {
  padding: 0 13px 10px;
  color: #182623;
  font-size: 14px;
  border-top: 1px solid #e4eae5;
  background: white;
}
.reason-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.reason-field button {
  margin: 0;
  background: transparent;
  padding: 0 6px;
  color: #176a57;
  font-size: 14px;
  line-height: 40px;
}
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.chips button {
  line-height: 20px;
  padding: 4px 10px;
  min-height: 30px;
  font-size: 13px;
  border: 1px solid transparent;
  border-radius: 7px;
  background: #f3f5f1;
  color: #182623;
}
.chips button.active {
  background: #e8f4eb;
  color: #176a57;
  border-color: #176a57;
}
.reason-overlay {
  z-index: 8;
}
.reason-toolbar,
.reason-add {
  display: grid;
  align-items: center;
  grid-template-columns: 50px 1fr 50px;
  text-align: center;
}
.reason-add {
  grid-template-columns: 84px minmax(0, 1fr) auto;
  gap: 10px;
  text-align: left;
}
.reason-add input {
  min-width: 0;
}
.reason-list {
  overflow: auto;
  max-height: 50vh;
}
.hint {
  color: #536660;
  font-size: 13px;
}
</style>
