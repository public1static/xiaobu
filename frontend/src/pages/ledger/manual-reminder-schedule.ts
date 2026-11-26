export const manualReminderFrequencies = {
  ONCE: '单次',
  DAILY: '每天',
  WEEKLY: '每周',
  FORTNIGHTLY: '每两周',
  MONTHLY: '每月',
  QUARTERLY: '每季度',
  HALF_YEARLY: '每半年',
  YEARLY: '每年',
} as const;

export type ManualReminderFrequency = keyof typeof manualReminderFrequencies;

const daySteps = { DAILY: 1, WEEKLY: 7, FORTNIGHTLY: 14 } as const;
const monthSteps = { MONTHLY: 1, QUARTERLY: 3, HALF_YEARLY: 6, YEARLY: 12 } as const;
const end = '2100-12-31';
const toDate = (day: string) => new Date(day + 'T00:00:00Z');
const toDay = (date: Date) => date.toISOString().slice(0, 10);

function shiftDay(value: string, days: number) {
  const date = toDate(value);
  date.setUTCDate(date.getUTCDate() + days);
  return toDay(date);
}

function nextDueOn(
  due: string,
  anchor: string,
  frequency: Exclude<ManualReminderFrequency, 'ONCE'>,
) {
  if (frequency === 'DAILY' || frequency === 'WEEKLY' || frequency === 'FORTNIGHTLY')
    return shiftDay(due, daySteps[frequency]);

  const date = toDate(due);
  date.setUTCDate(1);
  date.setUTCMonth(date.getUTCMonth() + monthSteps[frequency] + 1, 0);
  date.setUTCDate(Math.min(date.getUTCDate(), Number(anchor.slice(8))));
  return toDay(date);
}

export function previewManualReminderTimes(
  dueOn: string,
  anchor: string,
  frequency: ManualReminderFrequency,
  leadDays: number,
  time: string,
  now: string,
) {
  const at = (day: string) => shiftDay(day, -leadDays) + ' ' + time;
  if (frequency === 'ONCE') return [at(dueOn)];
  const preview: string[] = [];
  for (let day = dueOn; day <= end && preview.length < 3; day = nextDueOn(day, anchor, frequency)) {
    const trigger = at(day);
    if (trigger >= now) preview.push(trigger);
  }
  return preview;
}
