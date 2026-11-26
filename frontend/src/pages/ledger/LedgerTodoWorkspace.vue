<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue';
import { listLedgerTodos, type LedgerTodo } from '../../api/ledger-todos';
import { ledgerTodoKey, ledgerTodoSource } from './ledger-todo-sources';
import { formatSignedCurrency } from './transaction';
import LedgerIcon from '../../components/LedgerIcon.vue';

const props = defineProps<{ ledgerId: string; revision: number }>();
const emit = defineEmits<{ close: []; select: [item: LedgerTodo]; count: [count: number] }>();
const topStyle: Record<string, string> = {};
// #ifdef MP-WEIXIN
topStyle.paddingTop = uni.getMenuButtonBoundingClientRect().bottom + 'px';
// #endif
const completed = ref(false);
const rows = ref<LedgerTodo[]>([]);
const page = ref(0);
const total = ref(0);
const pending = ref(0);
const today = ref('');
const busy = ref(false);
const error = ref(false);
let sequence = 0;
const groups = computed(() => {
  if (completed.value)
    return [{ key: 'done', name: '已完成', total: total.value, rows: rows.value }];
  return [
    {
      key: 'pending',
      name: '待处理',
      total: pending.value,
      rows: rows.value.filter((row) => row.status === 'PENDING'),
    },
    {
      key: 'planned',
      name: '已计划',
      total: total.value - pending.value,
      rows: rows.value.filter((row) => row.status === 'PLANNED'),
    },
  ].filter((group) => group.rows.length);
});

async function load(next = 0) {
  const current = ++sequence;
  error.value = false;
  busy.value = true;
  if (!next) rows.value = [];
  try {
    const result = await listLedgerTodos(props.ledgerId, completed.value, next);
    if (current !== sequence) return;
    if (
      next &&
      (total.value !== result.totalCount ||
        pending.value !== result.pendingCount ||
        result.entries.some((item) =>
          rows.value.some((row) => ledgerTodoKey(row) === ledgerTodoKey(item)),
        ))
    ) {
      await load();
      return;
    }
    rows.value = next ? [...rows.value, ...result.entries] : result.entries;
    today.value = result.today;
    page.value = result.page;
    pending.value = result.pendingCount;
    total.value = result.totalCount;
    emit('count', result.pendingCount);
  } catch {
    if (current === sequence) error.value = true;
  } finally {
    if (current === sequence) busy.value = false;
  }
}
watch([completed, () => props.ledgerId, () => props.revision], () => void load(), {
  immediate: true,
});
onUnmounted(() => {
  sequence++;
});
function dueLabel(row: LedgerTodo) {
  const source = ledgerTodoSource(row.source);
  const label = row.status === 'COMPLETED' ? source?.completedLabel : source?.label;
  const timing =
    row.status === 'COMPLETED'
      ? ''
      : row.dueOn < today.value
        ? '已逾期'
        : row.dueOn === today.value
          ? '今天到期'
          : '';
  return [label, timing, row.subtitle].filter(Boolean).join(' · ');
}
function shortMonth(date: string) {
  const year = date.slice(0, 4);
  const month = Number(date.slice(5, 7));
  return year === today.value.slice(0, 4) ? month + '月' : year + '年' + month + '月';
}
</script>

<template>
  <view class="todo-workspace" :style="topStyle">
    <view class="agenda-head">
      <button aria-label="返回待办入口" @click="emit('close')">‹</button>
      <text>待办</text>
      <view />
    </view>
    <view class="agenda-content">
      <view class="agenda-tabs">
        <button
          :class="{ active: !completed }"
          :aria-pressed="!completed"
          @click="completed = false"
        >
          未完成
        </button>
        <button :class="{ active: completed }" :aria-pressed="completed" @click="completed = true">
          已完成
        </button>
      </view>
      <view v-for="group in groups" :key="group.key" class="agenda-group" :class="group.key">
        <view v-if="group.rows.length" class="agenda-group-label">
          <LedgerIcon
            v-if="group.key === 'pending'"
            class="agenda-icon"
            src="/static/assistant/bell.svg"
          />
          <text>{{ group.name }}</text><text class="agenda-count">{{ group.total }}</text>
        </view>
        <view v-if="group.rows.length" class="agenda-list">
          <button
            v-for="row in group.rows"
            :key="ledgerTodoKey(row)"
            class="agenda-row"
            :disabled="!ledgerTodoSource(row.source)"
            @click="emit('select', row)"
          >
            <view class="agenda-date"
              ><text>{{ shortMonth(row.dueOn) }}</text
              ><text>{{ row.dueOn.slice(8) }}</text></view
            >
            <view class="agenda-copy"
              ><text>{{ row.title }}</text
              ><text class="agenda-caption">{{ dueLabel(row) }}</text></view
            >
            <view class="agenda-target">
              <text v-if="row.amountCents != null" class="agenda-amount">{{
                formatSignedCurrency(row.amountCents)
              }}</text>
              <text v-if="ledgerTodoSource(row.source)" class="agenda-link"
                >{{ ledgerTodoSource(row.source)?.action }} ›</text
              >
            </view>
          </button>
        </view>
      </view>
      <text v-if="busy" class="agenda-empty">加载中…</text>
      <button v-else-if="error" class="agenda-more" @click="load(rows.length ? page + 1 : 0)">
        加载失败，点击重试
      </button>
      <text v-else-if="!rows.length" class="agenda-empty">{{
        completed ? '暂无已完成事项' : '暂无待办'
      }}</text>
      <button v-else-if="rows.length < total" class="agenda-more" @click="load(page + 1)">
        加载更多
      </button>
    </view>
  </view>
</template>

<style scoped>
.todo-workspace {
  position: fixed;
  z-index: 29;
  inset: 0;
  overflow-y: auto;
  background: #f4f6f5;
  color: #21352e;
  font-size: 14px;
}
.agenda-head {
  position: sticky;
  top: 0;
  z-index: 1;
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) 42px;
  align-items: center;
  text-align: center;
  min-height: 52px;
  padding: 0 10px;
  border-bottom: 1px solid #e8efeb;
  background: white;
  font-size: 17px;
  font-weight: 650;
}
.todo-workspace button {
  margin: 0;
  padding: 0;
  border: 0;
  background: transparent;
  color: inherit;
  font: inherit;
  line-height: 1.35;
}
.todo-workspace button::after {
  border: 0;
}
.agenda-head button {
  width: 42px;
  min-height: 44px;
  font-size: 28px;
}
.agenda-content {
  width: min(100%, 540px);
  margin: auto;
  padding: 14px 14px calc(24px + env(safe-area-inset-bottom));
  box-sizing: border-box;
}
.agenda-tabs {
  display: flex;
  padding: 3px;
  gap: 3px;
  border-radius: 11px;
  background: #e8efeb;
}
.agenda-tabs button {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
  min-height: 38px;
  border-radius: 8px;
}
.agenda-tabs button.active {
  color: #fff;
  background: #176a57;
}
.agenda-group {
  margin-top: 14px;
}
.agenda-group-label {
  display: flex;
  align-items: center;
  gap: 7px;
  margin: 0 0 7px 2px;
  font-weight: 650;
}
.agenda-icon {
  width: 18px;
  height: 18px;
}
.agenda-count {
  padding: 2px 8px;
  color: #456259;
  background: #ebf0ed;
  border-radius: 12px;
  font-size: 12px;
}
.agenda-list {
  background: white;
  border-radius: 13px;
  overflow: hidden;
}
.todo-workspace .agenda-row {
  display: flex;
  align-items: center;
  gap: 9px;
  width: 100%;
  min-height: 64px;
  padding: 8px 11px;
  border-radius: 0;
  border-bottom: 1px solid #edf1ef;
  text-align: left;
}
.todo-workspace .agenda-row:last-child {
  border-bottom: 0;
}
.agenda-date {
  flex: 0 0 58px;
  padding: 5px 2px;
  border-radius: 8px;
  text-align: center;
  background: #eaf2ee;
  color: #176a57;
  font-size: 12px;
}
.agenda-date text {
  display: block;
}
.agenda-date text + text {
  font-size: 19px;
  font-weight: 650;
}
.agenda-copy {
  flex: 1;
  min-width: 0;
  overflow-wrap: anywhere;
}
.agenda-caption {
  display: block;
  margin-top: 3px;
  color: #61736b;
  font-size: 12px;
}
.pending .agenda-caption {
  color: #8b4a2b;
}
.agenda-target {
  display: grid;
  justify-items: end;
  gap: 3px;
}
.agenda-amount {
  font-size: 13px;
  font-weight: 650;
}
.agenda-link {
  color: #176a57;
  font-size: 12px;
  white-space: nowrap;
}
.agenda-empty,
.todo-workspace .agenda-more {
  display: block;
  width: 100%;
  padding: 16px 8px;
  box-sizing: border-box;
  text-align: center;
  color: #61736b;
}
.todo-workspace .agenda-more {
  color: #176a57;
}
</style>
