<script setup>
import { computed, ref } from 'vue';
import {
  Form as VanForm,
  Field as VanField,
  Button as VanButton,
  Popup as VanPopup,
  Checkbox as VanCheckbox,
  CheckboxGroup as VanCheckboxGroup,
  TimePicker as VanTimePicker,
  Icon as VanIcon,
  ActionSheet as VanActionSheet,
} from 'vant';
import FieldClear from './FieldClear.vue';
import ReminderDateField from './ReminderDateField.vue';
import SelectRow from './SelectRow.vue';
import { billPeriods, shiftMonth } from './fixed-bills.mjs';
import { taskDate, taskChannels, repeats, previewTimes } from './manual-reminders.mjs';
import { validDay } from './ledger.mjs';
const props = defineProps({
  task: Object,
  book: Object,
  today: String,
  now: String,
  actor: String,
  write: Function,
});
const emit = defineEmits(['saved', 'back']);
const original = props.task;
const initial = original ? taskDate(original) : props.today;
const draft = ref(
  original
    ? { ...original, channels: taskChannels(original).filter((c) => c === 'wechat') }
    : {
        title: '',
        recipient: props.actor,
        time: '09:00',
        repeat: 'once',
        lead: '0',
        channels: ['wechat'],
        dateMode: 'solar',
        link: null,
      },
);
const due = ref(initial),
  clock = ref(false),
  parts = ref([]),
  links = ref(false),
  kind = ref('fixed'),
  month = ref(initial.slice(0, 7));
// 修改通知时间保留原始周期锚点，预览和保存共用同一份计划。
const schedule = computed(() => {
  const keep =
    original &&
    due.value === initial &&
    draft.value.repeat === original.repeat &&
    (draft.value.dateMode || 'solar') === (original.dateMode || 'solar') &&
    draft.value.festival === original.festival;
  return {
    ...draft.value,
    start: keep ? original.start : due.value,
    index: keep ? original.index : 0,
  };
});
const times = computed(() => previewTimes(schedule.value, props.now));
const repeatOptions = computed(() =>
  Object.entries(repeats)
    .filter(
      ([k]) =>
        !['festival', 'lunar'].includes(draft.value.dateMode) || ['once', 'year'].includes(k),
    )
    .map(([value, label]) => ({ value, label })),
);
// 按农历或节日创建时默认逐年重复，用户可改为单次。
function chooseDate(date, mode, festival) {
  if (mode !== 'solar' && mode !== draft.value.dateMode) draft.value.repeat = 'year';
  due.value = date;
  draft.value.dateMode = mode;
  draft.value.festival = festival || undefined;
}
const choices = computed(() => {
  if (kind.value === 'fixed')
    return billPeriods(props.book, month.value).map(({ bill }) => ({
      workspace: 'fixed',
      id: bill.id,
      month: month.value,
      label: bill.name + ' · ' + month.value,
    }));
  if (kind.value === 'rentals')
    return (props.book.leases || []).map((l) => ({
      workspace: 'rentals',
      id: l.id,
      label:
        (props.book.rentalHomes?.find((h) => h.id === l.homeId)?.name || '合同') + ' · ' + l.party,
    }));
  return (props.book.giftLists || [])
    .filter((l) => !l.archived)
    .map((l) => ({ workspace: 'gift', id: l.id, label: l.name }));
});
// 编辑标题或通知时刻保留原来的重复日号，修改日期或周期从当前期重新计算。
function save() {
  const task = {
    ...schedule.value,
    title: draft.value.title.trim(),
    id: original?.id || crypto.randomUUID(),
    owner: original?.owner || props.actor,
    history: original?.history || [],
  };
  const tasks = props.book.manualReminders || [];
  if (
    props.write({
      ...props.book,
      manualReminders: original
        ? tasks.map((t) => (t.id === task.id ? task : t))
        : [...tasks, task],
    })
  )
    emit('saved', task);
}
</script>
<template>
  <header class="business-header">
    <button aria-label="返回" @click="$emit('back')"><VanIcon name="arrow-left" /></button>
    <h1>{{ task ? '编辑提醒' : '新增提醒' }}</h1>
  </header>
  <VanForm class="managed-form manual-form" scroll-to-error @submit="save">
    <VanField
      v-model="draft.title"
      required
      label="提醒内容"
      name="title"
      maxlength="60"
      placeholder="例如：联系房东确认续租"
      :rules="[{ validator: (v) => !!v.trim(), message: '请输入提醒内容' }]"
    />
    <ReminderDateField
      :date="due"
      :mode="draft.dateMode"
      :festival="draft.festival"
      :today="today"
      @choose="chooseDate"
    />
    <SelectRow
      v-model="draft.lead"
      label="提醒日"
      :options="[
        { value: '0', label: '当天' },
        ...['1', '3', '7'].map((d) => ({ value: d, label: '提前 ' + d + ' 天' })),
      ]"
    />
    <button
      type="button"
      class="form-picker-row"
      @click="
        parts = draft.time.split(':');
        clock = true;
      "
    >
      <span>提醒时刻</span><span>{{ draft.time }}<VanIcon name="arrow" /></span>
    </button>
    <SelectRow v-model="draft.repeat" label="重复" :options="repeatOptions" />
    <div class="channel-row">
      <span id="reminder-channel-label">通知方式</span
      ><VanCheckboxGroup
        v-model="draft.channels"
        class="channel-options"
        aria-labelledby="reminder-channel-label"
      >
        <VanCheckbox name="wechat" shape="square" icon-size="15px">微信通知</VanCheckbox>
      </VanCheckboxGroup>
    </div>
    <div class="picker-with-clear">
      <button
        type="button"
        class="form-picker-row"
        @click="
          month = due.slice(0, 7);
          links = true;
        "
      >
        <span>关联业务</span
        ><span
          ><span class="picker-text">{{ draft.link?.label || '选择业务记录' }}</span
          ><VanIcon name="arrow"
        /></span>
      </button>
      <FieldClear v-if="draft.link" label="关联业务" @clear="draft.link = null" />
    </div>
    <section class="schedule-preview">
      <h2>{{ times.length === 1 ? '本次提醒时间' : '接下来 3 次提醒时间' }}</h2>
      <div v-for="at in times" :key="at">
        <span>{{ at.slice(0, 10) }}</span><time :datetime="at">{{ at.slice(11) }}</time
        ><small v-if="at < now">已过</small>
      </div>
    </section>
    <VanButton native-type="submit" type="primary" block round>保存提醒</VanButton>
  </VanForm>
  <VanPopup
    v-model:show="clock"
    round
    position="bottom"
    teleport="body"
    :style="{ maxWidth: '440px', left: 0, right: 0, marginInline: 'auto' }"
    ><VanTimePicker
      v-if="clock"
      v-model="parts"
      title="提醒时刻"
      @cancel="clock = false"
      @confirm="
        draft.time = parts.join(':');
        clock = false;
      "
  /></VanPopup>
  <VanActionSheet
    v-model:show="links"
    title="关联业务"
    teleport="body"
    :style="{ maxWidth: '440px', left: 0, right: 0, marginInline: 'auto' }"
    ><div class="link-panel">
      <div class="field-segments three-options">
        <button
          v-for="(label, key) in { fixed: '账期', rentals: '合同', gift: '礼单' }"
          :key="key"
          :aria-pressed="kind === key"
          @click="kind = key"
        >
          {{ label }}
        </button>
      </div>
      <div v-if="kind === 'fixed'" class="month-nav">
        <button aria-label="上个月" @click="month = shiftMonth(month, -1)">‹</button>{{ month
        }}<button aria-label="下个月" @click="month = shiftMonth(month, 1)">›</button>
      </div>
      <button
        v-for="item in choices"
        :key="item.id"
        class="link-option"
        @click="
          draft.link = item;
          links = false;
        "
      >
        {{ item.label }}<VanIcon name="arrow" />
      </button>
      <p v-if="!choices.length">暂无可关联记录</p>
      <button
        v-if="draft.link"
        class="link-option"
        @click="
          draft.link = null;
          links = false;
        "
      >
        清除关联
      </button>
    </div></VanActionSheet
  >
</template>
<style scoped>
.channel-row {
  display: grid;
  grid-template-columns: var(--field-label) minmax(0, 1fr);
  column-gap: var(--field-gap);
  align-items: center;
  min-height: 46px;
  border-bottom: 1px solid var(--line);
  font-size: 14px;
}
.channel-options {
  display: flex;
  flex-wrap: wrap;
  column-gap: clamp(6px, 2vw, 14px);
}
.channel-options :deep(.van-checkbox) {
  min-height: 42px;
}
.channel-options :deep(.van-checkbox__label) {
  margin-left: 4px;
  color: var(--ink);
}
.channel-options :deep(.van-checkbox__icon .van-icon) {
  border-radius: 4px;
}
.link-panel p,
.explain {
  color: var(--muted);
  font-size: 13px;
  line-height: 1.6;
}
.schedule-preview {
  padding: 12px 14px;
  margin: 14px 0;
  background: var(--soft);
  border-radius: 10px;
  font-size: 14px;
}
.schedule-preview h2 {
  font-size: 13px;
  color: var(--muted);
  font-weight: 500;
  margin: 0 0 7px;
}
.schedule-preview div {
  display: flex;
  align-items: center;
  gap: 12px;
  line-height: 1.8;
  font-variant-numeric: tabular-nums;
}
.schedule-preview small {
  color: var(--muted);
}
.link-panel,
.explain {
  padding: 6px 18px 18px;
}
.month-nav,
.link-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.month-nav button,
.link-option {
  background: transparent;
  border: 0;
  font: inherit;
  color: var(--green);
  padding: 12px;
}
.link-option {
  width: 100%;
  border-bottom: 1px solid var(--line);
  font-size: 14px;
}
.manual-form .form-picker-row span:last-child {
  gap: 8px;
}
</style>
