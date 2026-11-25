import { lunarParts, lunarDate, festivals, festivalDate } from './reminder-calendar.mjs';
import { asDate, localDay, validDay } from './ledger.mjs';

export const repeats = {
  once: '单次',
  day: '每天',
  week: '每周',
  fortnight: '每两周',
  month: '每月',
  quarter: '每季度',
  halfYear: '每半年',
  year: '每年',
};

// 按原始日期计算各期，短月取月末，下个月仍回到原定日号。
export function occurrenceDate(start, repeat, index) {
  const anchor = asDate(start);
  const dayStep = { day: 1, week: 7, fortnight: 14 }[repeat];
  const monthStep = { month: 1, quarter: 3, halfYear: 6, year: 12 }[repeat];
  if (dayStep) anchor.setDate(anchor.getDate() + dayStep * index);
  if (monthStep) {
    const day = anchor.getDate();
    anchor.setDate(1);
    anchor.setMonth(anchor.getMonth() + index * monthStep);
    anchor.setDate(
      Math.min(day, new Date(anchor.getFullYear(), anchor.getMonth() + 1, 0).getDate()),
    );
  }
  return localDay(anchor);
}

// 旧版任务始终在账本内提醒，迁移时保留原微信偏好。
export function taskChannels(t) {
  return t.channels ?? (t.wechat ? ['wechat', 'badge'] : ['badge']);
}
export const channelNames = { wechat: '微信通知', badge: '账本角标' };
// 天、周按实际间隔推进，节日和农历纪念日按所属历法逐年换算。
export function taskDate(t, index = t.index) {
  if (t.repeat === 'year' && index && t.dateMode === 'festival')
    return festivalDate(t.festival, Number(t.start.slice(0, 4)) + index);
  if (t.repeat === 'year' && index && t.dateMode === 'lunar') {
    const p = lunarParts(t.start);
    return lunarDate(p.year + index, p.month, p.day);
  }
  return occurrenceDate(t.start, t.repeat, index);
}
// 本地提醒使用本机时区；日期决定分组，提醒时刻决定是否计入铃铛。
export function triggerAt(task, due) {
  const d = asDate(due);
  d.setDate(d.getDate() - Number(task.lead));
  return localDay(d) + 'T' + task.time;
}
// 预览与待办使用同一套日期计算，跳过已过的重复通知时刻，保留单次的设定值。
export function previewTimes(task, now) {
  const timeAt = (i) => triggerAt(task, taskDate(task, i));
  if (task.repeat === 'once') return [timeAt(0)];
  let i = task.index;
  while (timeAt(i) < now) i++;
  return [timeAt(i), timeAt(i + 1), timeAt(i + 2)];
}
export function validTask(t) {
  return (
    t &&
    typeof t.id === 'string' &&
    typeof t.title === 'string' &&
    !!t.title.trim() &&
    t.title.length <= 60 &&
    (!t.dateMode || ['solar', 'lunar', 'festival'].includes(t.dateMode)) &&
    (t.dateMode !== 'festival' || Object.hasOwn(festivals, t.festival)) &&
    (t.channels === undefined ||
      (Array.isArray(t.channels) && t.channels.every((c) => Object.hasOwn(channelNames, c)))) &&
    validDay(t.start) &&
    /^([01]\d|2[0-3]):[0-5]\d$/.test(t.time) &&
    Object.hasOwn(repeats, t.repeat) &&
    ['0', '1', '3', '7'].includes(t.lead) &&
    typeof t.recipient === 'string' &&
    Number.isSafeInteger(t.index) &&
    t.index >= 0 &&
    Array.isArray(t.history) &&
    t.history.every(
      (h) => validDay(h.due) && typeof h.title === 'string' && typeof h.recipient === 'string',
    ) &&
    (!t.link ||
      (['fixed', 'rentals', 'gift'].includes(t.link.workspace) &&
        typeof t.link.id === 'string' &&
        typeof t.link.label === 'string' &&
        (t.link.workspace !== 'fixed' || validDay(t.link.month + '-01'))))
  );
}
// 完成只推进当前期；过期或重复提交不推进下一期。
export function completeTask(book, id, due) {
  const tasks = book.manualReminders || [],
    task = tasks.find((t) => t.id === id);
  if (!task || task.finished || taskDate(task) !== due) return null;
  const history = [
    ...task.history,
    { due, title: task.title, recipient: task.recipient, time: task.time },
  ];
  const next = { ...task, history, index: task.index + 1, finished: task.repeat === 'once' };
  return { ...book, manualReminders: tasks.map((t) => (t.id === id ? next : t)) };
}

// 每个系列展示最早尚待办理的一期，远期事项仍可提前修改。
export const manualReminder = {
  kind: 'manual',
  defaultDays: '0',
  days: [0, 1, 3, 7],
  *pending(book) {
    for (const t of book.manualReminders || []) {
      if (t.finished) continue;
      const due = taskDate(t);
      yield {
        id: t.id,
        occurrence: due,
        due,
        remindDays: t.lead,
        recipient: t.recipient,
        owner: t.owner,
        at: triggerAt(t, due),
        title: t.title,
        subtitle: t.time + ' · ' + (t.repeat === 'once' ? '自建提醒' : repeats[t.repeat]),
        target: { workspace: 'reminders', props: { taskId: t.id } },
      };
    }
  },
};
