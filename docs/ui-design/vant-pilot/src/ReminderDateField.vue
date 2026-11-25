<script setup>
import { computed, ref } from 'vue';
import {
  ActionSheet as VanActionSheet,
  Calendar as VanCalendar,
  Field as VanField,
  Picker as VanPicker,
} from 'vant';
import { asDate, localDay } from './ledger.mjs';
import {
  lunarParts,
  lunarMonths,
  lunarDate,
  monthLabel,
  dayLabel,
  festivals,
  festivalDate,
  dateDescription,
} from './reminder-calendar.mjs';
const props = defineProps({ date: String, mode: String, festival: String, today: String });
const emit = defineEmits(['choose']);
const show = ref(false),
  tab = ref('solar'),
  values = ref([]);
const yearOptions = Array.from({ length: 201 }, (_, i) => ({
  value: String(1900 + i),
  text: 1900 + i + '年',
}));
const columns = computed(() => {
  const months = lunarMonths(Number(values.value[0]) || 2026);
  const m = months.find((m) => m.month === values.value[1]) || months[0];
  return [
    yearOptions,
    months.map((m) => ({ value: m.month, text: monthLabel(m.month) })),
    Array.from({ length: m.length }, (_, i) => ({ value: String(i + 1), text: dayLabel(i + 1) })),
  ];
});
// 弹层内暂存选择，取消时保留原日期和历法。
function open() {
  const p = lunarParts(props.date);
  values.value = [String(p.year), p.month, String(p.day)];
  tab.value = props.mode || 'solar';
  show.value = true;
}
function choose(date, festival = '') {
  emit('choose', date, tab.value, festival);
  show.value = false;
}
function nextFestival(name) {
  let y = Number(props.today.slice(0, 4)),
    d = festivalDate(name, y);
  return d < props.today ? festivalDate(name, y + 1) : d;
}
</script>
<template>
  <VanField
    label="日期"
    required
    readonly
    is-link
    clickable
    :model-value="dateDescription(date, mode, festival)"
    @click="open"
    @keydown.enter.prevent="open"
  />
  <VanActionSheet
    v-model:show="show"
    teleport="body"
    title="选择日期"
    :style="{ maxWidth: '440px', left: 0, right: 0, marginInline: 'auto' }"
    ><div v-if="show" class="calendar-choice">
      <div class="field-segments three-options">
        <button
          v-for="(text, key) in { solar: '公历', lunar: '农历', festival: '节日' }"
          :key="key"
          :aria-pressed="tab === key"
          @click="tab = key"
        >
          {{ text }}
        </button>
      </div>
      <VanCalendar
        v-if="tab === 'solar'"
        :poppable="false"
        :show-title="false"
        switch-mode="year-month"
        :min-date="new Date(1900, 0, 1)"
        :max-date="new Date(2100, 11, 31)"
        :default-date="asDate(date)"
        @confirm="choose(localDay($event))"
      />
      <VanPicker
        v-if="tab === 'lunar'"
        v-model="values"
        :columns="columns"
        @cancel="show = false"
        @confirm="({ selectedValues: v }) => choose(lunarDate(Number(v[0]), v[1], Number(v[2])))"
      />
      <div v-if="tab === 'festival'" class="festival-grid">
        <button
          v-for="(_, name) in festivals"
          :key="name"
          @click="choose(nextFestival(name), name)"
        >
          {{ name }}<small>{{ nextFestival(name) }}</small>
        </button>
      </div>
    </div></VanActionSheet
  >
</template>
<style scoped>
.calendar-choice {
  padding: 4px 16px 16px;
}
.calendar-choice .van-calendar {
  height: min(420px, 65dvh);
}
.festival-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  margin-top: 14px;
}
.festival-grid button {
  border: 1px solid var(--line);
  border-radius: 9px;
  background: white;
  color: var(--ink);
  font: inherit;
  padding: 10px;
}
.festival-grid small {
  display: block;
  color: var(--muted);
  margin-top: 4px;
}
</style>
