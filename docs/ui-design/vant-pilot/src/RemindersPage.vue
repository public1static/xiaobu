<script setup>
import { computed, inject, onUnmounted, ref } from 'vue';
import {
  Icon as VanIcon,
  Cell as VanCell,
  Button as VanButton,
  showToast,
  showConfirmDialog,
} from 'vant';
import { dateDescription } from './reminder-calendar.mjs';
import { groupReminders } from './reminders.mjs';
import { completeTask, taskDate, taskChannels, repeats, triggerAt } from './manual-reminders.mjs';
import { dueDate } from './fixed-bills.mjs';
import RecordActions from './RecordActions.vue';
import ManualReminderForm from './ManualReminderForm.vue';
const props = defineProps({
  badgeKeys: Set,
  items: Array,
  today: String,
  now: String,
  book: Object,
  actor: String,
  write: Function,
  initialTask: String,
});
const emit = defineEmits(['back', 'open']);
const writable = inject('bookWritable');
const tab = ref('pending'),
  page = ref(props.initialTask ? 'detail' : 'list'),
  selected = ref(props.initialTask || ''),
  historyItem = ref(null);
const task = computed(() =>
  (props.book.manualReminders || []).find((t) => t.id === selected.value),
);
const day = computed(() => (task.value ? taskDate(task.value) : ''));
// 完成页复用付款凭据和合同到期标记；自建事项按期保留完成时的快照。
const completed = computed(() => {
  const rows = new Set(props.book.rows.filter((r) => r.kind === 'expense').map((r) => r.id)),
    items = [];
  for (const bill of props.book.fixedBills || [])
    for (const [month, id] of Object.entries(bill.paid)) {
      if (rows.has(id))
        items.push({
          key: bill.id + month,
          title: bill.name,
          due: dueDate(bill, month),
          subtitle: '账单已付',
          target: { workspace: 'fixed', props: { openBill: bill.id, initialMonth: month } },
        });
    }
  for (const l of props.book.leases || [])
    if (l.handledEnd === l.end)
      items.push({
        key: l.id,
        title: props.book.rentalHomes?.find((h) => h.id === l.homeId)?.name || '租房合同',
        due: l.end,
        subtitle: '到期已处理',
        target: { workspace: 'rentals', props: { initialLease: l.id } },
      });
  for (const t of props.book.manualReminders || [])
    for (const [index, h] of t.history.entries()) {
      if (h.recipient === props.actor || t.owner === props.actor)
        items.push({ ...h, key: t.id + ':' + index, subtitle: '自建提醒 · 已完成' });
    }
  return items.sort((a, b) => b.due.localeCompare(a.due));
});
// 每条待办只出现一次；达到提醒时间且计入铃铛的项目优先展示。
const groups = computed(() => {
  if (tab.value !== 'pending')
    return completed.value.length
      ? [{ label: '已完成', tone: 'soon', items: completed.value }]
      : [];
  const alerted = props.items.filter((item) => props.badgeKeys.has(item.key));
  const other = groupReminders(
    props.items.filter((item) => !props.badgeKeys.has(item.key)),
    props.today,
  );
  return alerted.length ? [{ label: '待处理', tone: 'alerted', items: alerted }, ...other] : other;
});

function open(item) {
  if (item.target?.props.taskId) {
    selected.value = item.target.props.taskId;
    page.value = 'detail';
  } else if (item.target) emit('open', item);
  else {
    historyItem.value = item;
    page.value = 'history';
  }
}
// 关联业务只负责导航，单独办理不会改变自建提醒的完成状态。
function openLink() {
  const l = task.value.link;
  const config = {
    fixed: { openBill: l.id, initialMonth: l.month },
    rentals: { initialLease: l.id },
    gift: { initialList: l.id },
  };
  const collection = {
    fixed: props.book.fixedBills,
    rentals: props.book.leases,
    gift: props.book.giftLists,
  };
  if (!collection[l.workspace]?.some((x) => x.id === l.id)) {
    showToast('关联记录已移除');
    return;
  }
  emit('open', { target: { workspace: l.workspace, props: config[l.workspace] } }, task.value.id);
}
// 新增从列表发起；编辑从详情发起，保存后返回各自入口。
function saved() {
  if (selected.value) {
    page.value = 'detail';
    return;
  }
  tab.value = 'pending';
  page.value = 'list';
  showToast('提醒已添加');
}
const undo = ref(null);
let undoTimer;
onUnmounted(() => clearTimeout(undoTimer));
// 撤销窗口只对应最近一次办结；另一个窗口或后续操作改过同一事项时保留新版本。
function finish(id, due) {
  const before = props.book.manualReminders?.find((t) => t.id === id);
  const next = completeTask(props.book, id, due);
  if (!next || !props.write(next)) return;
  page.value = 'list';
  undo.value = { before, after: JSON.stringify(next.manualReminders.find((t) => t.id === id)) };
  clearTimeout(undoTimer);
  undoTimer = setTimeout(() => {
    undo.value = null;
  }, 8000);
}
// 停止整个重复系列，保留已办历史，不把当前期记作完成。
async function stopRepeat() {
  const current = task.value;
  try {
    await showConfirmDialog({
      title: '停止重复提醒？',
      message: '本期及后续提醒将移出待办，已完成记录保留。',
      confirmButtonText: '停止重复',
    });
  } catch {
    return;
  }
  const tasks = props.book.manualReminders.map((t) =>
    t.id === current.id ? { ...t, finished: true } : t,
  );
  if (props.write({ ...props.book, manualReminders: tasks })) {
    page.value = 'list';
    undo.value = null;
    showToast('已停止重复提醒');
  }
}
function undoFinish() {
  const { before, after } = undo.value;
  const tasks = props.book.manualReminders;
  if (JSON.stringify(tasks.find((t) => t.id === before.id)) !== after) {
    undo.value = null;
    showToast('事项已更新');
    return;
  }
  if (
    props.write({
      ...props.book,
      manualReminders: tasks.map((t) => (t.id === before.id ? before : t)),
    })
  ) {
    clearTimeout(undoTimer);
    undo.value = null;
  }
}
</script>
<template>
  <ManualReminderForm
    v-if="page === 'form'"
    :task="task"
    :book="book"
    :today="today"
    :now="now"
    :actor="actor"
    :write="write"
    @back="page = task ? 'detail' : 'list'"
    @saved="saved"
  />
  <template v-else-if="page === 'detail' && task"
    ><header class="business-header">
      <button aria-label="返回" @click="page = 'list'"><VanIcon name="arrow-left" /></button>
      <h1>提醒详情</h1>
      <button v-if="writable" @click="page = 'form'">编辑</button>
    </header>
    <section class="task-summary">
      <small>{{ repeats[task.repeat] }} · 自建提醒</small>
      <h2>{{ task.title }}</h2>
      <span>{{ dateDescription(day, task.dateMode, task.festival) }}</span>
    </section>
    <div class="task-facts">
      <VanCell title="提醒时间" :value="triggerAt(task, day).replace('T', ' ')" /><VanCell
        title="微信通知"
        :value="taskChannels(task).includes('wechat') ? '开启' : '关闭'"
      /><VanCell
        v-if="task.link"
        title="关联业务"
        :value="task.link.label"
        is-link
        @click="openLink"
      />
    </div>
    <RecordActions
      v-if="writable"
      class="task-done"
      :options="[
        { name: '停止重复', visible: task.repeat !== 'once', danger: true, run: stopRepeat },
      ]"
      ><VanButton type="primary" round block @click="finish(task.id, day)">{{
        task.repeat === 'once' ? '标记为已办' : '标记本期已办'
      }}</VanButton></RecordActions
    ></template
  >
  <template v-else-if="page === 'history'"
    ><header class="business-header">
      <button aria-label="返回" @click="page = 'list'"><VanIcon name="arrow-left" /></button>
      <h1>已完成提醒</h1>
    </header>
    <section class="task-summary">
      <small>已完成</small>
      <h2>{{ historyItem.title }}</h2>
      <span>{{ historyItem.due }}</span>
    </section></template
  >
  <template v-else>
    <header class="business-header">
      <button aria-label="返回" @click="$emit('back')"><VanIcon name="arrow-left" /></button>
      <h1>待办</h1>
      <button
        v-if="writable"
        @click="
          selected = '';
          page = 'form';
        "
      >
        新增
      </button>
    </header>
    <div class="field-segments agenda-tabs">
      <button
        v-for="(label, k) in { pending: '未完成', done: '已完成' }"
        :key="k"
        :aria-pressed="tab === k"
        @click="tab = k"
      >
        {{ label }}
      </button>
    </div>
    <section v-for="group in groups" :key="group.tone" class="agenda-group" :class="group.tone">
      <h2>
        <VanIcon v-if="group.tone === 'alerted'" name="bell" /><i v-else />{{ group.label
        }}<small>{{ group.items.length }}</small>
      </h2>
      <div class="agenda-list">
        <div v-for="item in group.items" :key="item.key" class="agenda-row">
          <button class="agenda-main" @click="open(item)">
            <time :datetime="item.due"
              ><small
                >{{ item.due.slice(0, 4) !== today.slice(0, 4) ? item.due.slice(0, 4) + '年' : ''
                }}{{ Number(item.due.slice(5, 7)) }}月</small
              ><b>{{ item.due.slice(8) }}</b></time
            >
            <span class="agenda-copy"
              ><strong>{{ item.title }}</strong
              ><small
                ><template v-if="group.tone === 'alerted'"
                  >{{
                    item.due < today
                      ? '已逾期'
                      : item.due === today
                        ? '今天到期'
                        : Number(item.due.slice(5, 7)) + '月' + Number(item.due.slice(8)) + '日到期'
                  }}
                  · </template
                >{{ item.subtitle }}</small
              ></span
            >
            <span v-if="item.value" class="agenda-amount">{{ item.value }}</span><span
              v-if="item.kind === 'bill' && writable"
              class="business-action"
              >处理账单</span
            ><span v-else-if="item.kind === 'lease'" class="business-action">查看合同</span
            ><VanIcon v-else name="arrow" /></button
          ><button
            v-if="item.kind === 'manual' && writable"
            class="complete-action"
            :aria-label="'标记已办：' + item.title"
            @click="finish(item.id, item.due)"
          >
            标记已办
          </button>
        </div>
      </div>
    </section>
    <div v-if="undo" class="undo-bar" role="status">
      <span>已标记为已办</span><button @click="undoFinish">撤销</button>
    </div>
    <div v-if="!groups.length" class="agenda-empty">
      <VanIcon name="passed" />
      <h2>{{ tab === 'pending' ? '暂无待办' : '暂无已完成事项' }}</h2>
      <p>
        {{
          tab === 'pending'
            ? '固定账单、合同到期和自建事项都会显示在这里。'
            : '完成的提醒和已办理的业务事项会显示在这里。'
        }}
      </p>
    </div>
  </template>
</template>
<style scoped>
.task-facts {
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: var(--surface);
}
.task-summary {
  background: var(--green-deep);
  color: white;
  padding: 18px;
  border-radius: 14px;
  margin: 8px 0 14px;
}
.task-summary h2 {
  font-size: 21px;
  overflow-wrap: anywhere;
  margin: 8px 0;
}
.task-summary span,
.task-summary small {
  font-size: 13px;
  color: #dae7dc;
}
.task-done {
  margin-top: 20px;
}
.agenda-tabs {
  margin-bottom: 14px;
}
.agenda-group h2,
.agenda-list button {
  display: flex;
  align-items: center;
}
.agenda-group {
  --tone: var(--green);
  --wash: var(--mint);
  margin: 0 0 22px;
}
.agenda-group.late {
  --tone: #a04635;
  --wash: #fcf0eb;
}
.agenda-group.soon {
  --tone: #626d64;
  --wash: #f1f3ec;
}
.agenda-group h2 {
  gap: 7px;
  font-size: 16px;
  margin: 0 2px 9px;
}
.agenda-group h2 i:not(.van-icon) {
  height: 7px;
  width: 7px;
  border-radius: 50%;
  background: var(--tone);
}
.agenda-group h2 small {
  color: var(--muted);
  font-size: 12px;
  font-weight: 500;
}
.alerted h2 .van-icon,
.alerted h2 small {
  color: #a04635;
}
.alerted h2 small {
  background: #fcf0eb;
  border-radius: 12px;
  padding: 2px 7px;
}
.agenda-list {
  background: white;
  border: 1px solid var(--line);
  border-radius: 14px;
  overflow: hidden;
}
.agenda-main {
  min-width: 0;
  flex: 1;
  width: 100%;
  padding: 12px;
  gap: 10px;
  border: 0;
  background: white;
  text-align: left;
  color: var(--ink);
  font: inherit;
}
.agenda-row + .agenda-row {
  border-top: 1px solid var(--line);
}
.agenda-list time {
  display: grid;
  justify-items: center;
  align-content: center;
  min-width: 43px;
  min-height: 46px;
  border-radius: 9px;
  background: var(--wash);
  color: var(--tone);
}
time small {
  font-size: 10px;
}
time b {
  font-size: 19px;
  line-height: 1.15;
}
.agenda-copy {
  flex: 1;
  min-width: 0;
}
.agenda-copy strong,
.agenda-copy small {
  display: block;
}
.agenda-copy strong {
  font-size: 15px;
  overflow-wrap: anywhere;
}
.agenda-copy small {
  margin-top: 4px;
  font-size: 12px;
  color: var(--muted);
}
.agenda-amount {
  font-size: 14px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.agenda-list .van-icon {
  color: var(--muted);
  font-size: 14px;
}
.agenda-empty {
  text-align: center;
  margin: 18vh 20px;
  color: var(--muted);
}
.agenda-empty i {
  color: var(--green);
  font-size: 38px;
}
.agenda-empty h2 {
  font-size: 18px;
  color: var(--ink);
}
.agenda-empty p {
  font-size: 14px;
  line-height: 1.6;
}
.agenda-row {
  display: flex;
  align-items: center;
  background: white;
}
.business-action,
.complete-action {
  color: var(--green);
  font-size: 13px;
  white-space: nowrap;
}
.complete-action {
  border: 0;
  border-radius: 8px;
  background: var(--mint);
  margin-right: 10px;
  padding: 8px;
  min-height: 40px;
}
.undo-bar {
  position: sticky;
  bottom: 14px;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 4px 14px;
  background: var(--green-deep);
  color: white;
  border-radius: 10px;
}
.undo-bar button {
  min-height: 40px;
  border: 0;
  background: transparent;
  color: white;
  font: inherit;
  font-weight: 600;
}
</style>
