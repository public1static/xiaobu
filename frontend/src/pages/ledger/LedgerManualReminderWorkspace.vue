<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import {
  getLedgerManualReminder,
  saveLedgerManualReminder,
  setLedgerManualReminderCompletion,
  type LedgerManualReminder,
  type LedgerManualReminderInput,
} from '../../api/ledger-manual-reminders';
import { ledgerToday } from './transaction';

const props = defineProps<{ ledgerId: string; reminderId: string; writable: boolean }>();
const emit = defineEmits<{ close: []; changed: [] }>();
const topStyle: Record<string, string> = {};
// #ifdef MP-WEIXIN
topStyle.paddingTop = uni.getMenuButtonBoundingClientRect().bottom + 'px';
// #endif
const original = ref<LedgerManualReminder>();
const editing = ref(!props.reminderId);
const busy = ref(Boolean(props.reminderId));
const error = ref('');
const titleMissing = ref(false);
const draft = ref<LedgerManualReminderInput>({
  title: '',
  dueOn: ledgerToday(),
  time: '09:00',
  leadDays: 0,
});
const days = [0, 1, 3, 7];
const leadOpen = ref(false);
const options = ['当天', '提前 1 天', '提前 3 天', '提前 7 天'];
const preview = computed(() => {
  const date = new Date(draft.value.dueOn + 'T00:00:00Z');
  date.setUTCDate(date.getUTCDate() - draft.value.leadDays);
  return date.toISOString().slice(0, 10) + ' ' + draft.value.time;
});

async function load() {
  busy.value = true;
  error.value = '';
  try {
    original.value = await getLedgerManualReminder(props.ledgerId, props.reminderId);
  } catch {
    error.value = '无法加载提醒，请重试。';
  } finally {
    busy.value = false;
  }
}
onMounted(() => {
  if (props.reminderId) void load();
});
function beginEdit() {
  const item = original.value;
  if (!item || item.completed || !props.writable) return;
  draft.value = {
    title: item.title,
    dueOn: item.dueOn,
    time: item.remindAt.slice(11, 16),
    leadDays: item.leadDays,
  };
  error.value = '';
  editing.value = true;
}
function back() {
  if (busy.value) return;
  if (editing.value && original.value) {
    editing.value = false;
    error.value = '';
  } else emit('close');
}
async function save() {
  if (busy.value || !props.writable) return;
  const title = draft.value.title.trim();
  if (!title) {
    titleMissing.value = true;
    return;
  }
  busy.value = true;
  error.value = '';
  try {
    await saveLedgerManualReminder(props.ledgerId, { ...draft.value, title }, props.reminderId);
    emit('changed');
    emit('close');
  } catch {
    error.value = '保存未确认，请返回待办核对。';
  } finally {
    busy.value = false;
  }
}
async function toggleCompletion() {
  const item = original.value;
  if (!item || busy.value || !props.writable) return;
  busy.value = true;
  error.value = '';
  try {
    await setLedgerManualReminderCompletion(props.ledgerId, item.id, !item.completed);
    emit('changed');
    emit('close');
  } catch {
    error.value = '未能更新办结状态，请重试。';
  } finally {
    busy.value = false;
  }
}
</script>

<template>
  <view class="manual-workspace" :style="topStyle">
    <view class="manual-head">
      <button class="manual-back" aria-label="返回待办" @click="back">‹</button>
      <text>{{ editing ? (reminderId ? '编辑提醒' : '新增提醒') : '提醒详情' }}</text>
      <button v-if="!editing && original && !original.completed && writable" @click="beginEdit">
        编辑
      </button>
      <view v-else />
    </view>
    <view class="manual-content">
      <template v-if="editing">
        <view class="manual-card">
          <label class="manual-field">
            <text>提醒内容 <text class="manual-required">*</text></text>
            <input
              v-model="draft.title"
              aria-label="提醒内容"
              maxlength="60"
              @input="titleMissing = false"
              placeholder="例如：联系房东确认续租"
            />
          </label>
          <picker
            mode="date"
            aria-label="事项日期"
            :value="draft.dueOn"
            start="1900-01-01"
            end="2100-12-31"
            @change="draft.dueOn = $event.detail.value"
          >
            <view class="manual-field"
              ><text>事项日期</text><text>{{ draft.dueOn }} ›</text></view
            >
          </picker>
          <button class="manual-field manual-picker" @click="leadOpen = true">
            <text>提醒日</text><text>{{ options[days.indexOf(draft.leadDays)] }} ›</text>
          </button>
          <picker
            aria-label="提醒时刻"
            mode="time"
            :value="draft.time"
            @change="draft.time = $event.detail.value"
          >
            <view class="manual-field"
              ><text>提醒时刻</text><text>{{ draft.time }} ›</text></view
            >
          </picker>
        </view>
        <view class="manual-preview">
          <text>本次提醒时间</text><text>{{ preview }}</text>
          <text class="manual-hint">仅在小簿待办显示，不会发通知</text>
        </view>
        <text v-if="titleMissing" role="alert" class="manual-error">请输入提醒内容。</text>
        <text v-else-if="error" role="alert" class="manual-error">{{ error }}</text>
        <button class="manual-primary" :disabled="busy || !writable" @click="save">保存提醒</button>
      </template>
      <template v-else-if="original">
        <view class="manual-summary">
          <text>{{ original.completed ? '已办结' : '单次 · 自建提醒' }}</text>
          <text class="manual-title">{{ original.title }}</text>
          <text>{{ original.dueOn }}</text>
        </view>
        <view class="manual-card">
          <view class="manual-field"
            ><text>提醒时间</text
            ><text>{{ original.remindAt.slice(0, 16).replace('T', ' ') }}</text></view
          >
        </view>
        <text v-if="error" role="alert" class="manual-error">{{ error }}</text>
        <button v-if="writable" class="manual-primary" :disabled="busy" @click="toggleCompletion">
          {{ original.completed ? '撤销办结' : '标记为已办' }}
        </button>
      </template>
      <button v-else-if="error" class="manual-retry" @click="load">{{ error }}</button>
      <text v-else class="manual-hint">加载中…</text>
    </view>
    <view v-if="leadOpen" class="manual-picker-backdrop" @click.self="leadOpen = false">
      <view class="manual-picker-sheet" role="dialog" aria-label="提醒日">
        <text class="manual-picker-heading">提醒日</text>
        <button
          v-for="(label, index) in options"
          :key="label"
          :aria-pressed="draft.leadDays === days[index]"
          @click="
            draft.leadDays = days[index];
            leadOpen = false;
          "
        >
          {{ label }}
        </button>
        <button class="manual-picker-cancel" @click="leadOpen = false">取消</button>
      </view>
    </view>
  </view>
</template>

<style scoped>
.manual-workspace {
  position: fixed;
  z-index: 30;
  inset: 0;
  overflow-y: auto;
  background: #f4f6f5;
  color: #21352e;
  font-size: 14px;
}
.manual-head {
  position: sticky;
  top: 0;
  z-index: 1;
  display: grid;
  grid-template-columns: 55px minmax(0, 1fr) 55px;
  align-items: center;
  text-align: center;
  min-height: 52px;
  padding: 0 10px;
  border-bottom: 1px solid #e8efeb;
  background: white;
  font-size: 17px;
  font-weight: 650;
}
.manual-workspace button {
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0;
  padding: 0;
  border: 0;
  background: transparent;
  color: #176a57;
  font: inherit;
  line-height: 1.35;
  min-height: 44px;
}
.manual-workspace button::after {
  border: 0;
}
.manual-head .manual-back {
  font-size: 28px;
  color: inherit;
}
.manual-content {
  width: min(100%, 540px);
  margin: auto;
  padding: 14px 14px calc(24px + env(safe-area-inset-bottom));
  box-sizing: border-box;
}
.manual-card,
.manual-summary {
  padding: 0 14px;
  border-radius: 12px;
  background: white;
}
.manual-field {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  min-height: 46px;
  border-bottom: 1px solid #edf1ef;
}
.manual-card > :last-child .manual-field {
  border-bottom: 0;
}
.manual-field input {
  flex: 1;
  min-width: 0;
  font-size: 14px;
  text-align: right;
}
.manual-field > text:last-child {
  color: #456259;
}
.manual-required,
.manual-error {
  color: #a53836;
}
.manual-preview,
.manual-summary {
  display: grid;
  gap: 6px;
  padding: 12px 14px;
}
.manual-preview {
  margin: 12px 0;
  background: #eaf2ee;
  border-radius: 10px;
}
.manual-preview > text:first-child,
.manual-hint,
.manual-summary > text:first-child {
  color: #50685f;
  font-size: 13px;
}
.manual-error {
  display: block;
  margin: 12px 0;
}
.manual-summary {
  margin-bottom: 12px;
}
.manual-title {
  font-size: 19px;
  font-weight: 650;
  overflow-wrap: anywhere;
}
.manual-workspace .manual-primary {
  width: 100%;
  margin-top: 12px;
  border-radius: 24px;
  background: #176a57;
  color: white;
}
.manual-workspace .manual-primary:disabled {
  opacity: 0.55;
}
.manual-workspace .manual-picker {
  justify-content: space-between;
  width: 100%;
  min-height: 46px;
  border-bottom: 1px solid #edf1ef;
  border-radius: 0;
  color: inherit;
}
.manual-picker-backdrop {
  position: fixed;
  z-index: 2;
  inset: 0;
  display: flex;
  align-items: end;
  justify-content: center;
  background: rgb(25 39 34 / 42%);
}
.manual-picker-sheet {
  width: min(100%, 540px);
  padding: 10px 0 env(safe-area-inset-bottom);
  border-radius: 16px 16px 0 0;
  background: white;
  text-align: center;
}
.manual-picker-heading {
  display: block;
  padding: 8px;
  font-weight: 650;
}
.manual-picker-sheet button {
  width: 100%;
  border-radius: 0;
  border-bottom: 1px solid #edf1ef;
}
.manual-picker-sheet button[aria-pressed='true'] {
  font-weight: 650;
  background: #eaf2ee;
}
.manual-picker-sheet .manual-picker-cancel {
  border-top: 6px solid #f4f6f5;
}
</style>
