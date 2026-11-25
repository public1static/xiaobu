import { asDate, localDay, validDay } from './ledger.mjs';
import { reminderSources } from './reminder-sources.mjs';

// 提前天数按本地自然日计算，避免跨夏令时按小时换算。
function cutoff(today, days) {
  const end = asDate(today);
  end.setDate(end.getDate() + Number(days));
  return localDay(end);
}

// 提醒设置与列表读取同一来源的提前天数和默认值。
export const reminderOptions = (source) => [
  ...source.days.map((d) => ({ value: String(d), label: d ? '提前 ' + d + ' 天' : '到期当天' })),
  { value: 'off', label: '关闭提醒' },
];

// 各业务只提供待处理事项；统一处理关闭、提前天数、身份标识和日期排序。
export function reminders(
  book,
  today,
  sources = reminderSources,
  { actor, now = today + 'T23:59' } = {},
) {
  const result = [];
  for (const source of sources) {
    const windows = new Map(source.days.map((days) => [String(days), cutoff(today, days)]));

    for (const item of source.pending(book, today)) {
      const until = windows.get(String(item.remindDays ?? source.defaultDays));
      if (!validDay(item.due) || (actor && item.recipient && item.recipient !== actor)) continue;
      const later = item.at ? item.at > now : item.due > (until ?? today);
      // 关闭提前提醒时仍保留待办，到期当天进入待处理。
      result.push({
        ...item,
        later,
        kind: source.kind,
        key: JSON.stringify([source.kind, item.id, item.occurrence]),
      });
    }
  }
  return result.sort(
    (a, b) => a.due.localeCompare(b.due) || a.title.localeCompare(b.title, 'zh-CN'),
  );
}

// 业务类型不影响时间分组；跨日后同一事项移入新的分组。
export function groupReminders(items, today) {
  const groups = [
    { label: '已逾期', tone: 'late', items: [] },
    { label: '今天', tone: 'today', items: [] },
    { label: '即将到期', tone: 'soon', items: [] },
    { label: '已计划', tone: 'later', items: [] },
  ];
  for (const item of items)
    groups[item.later ? 3 : item.due < today ? 0 : item.due === today ? 1 : 2].items.push(item);
  return groups.filter((group) => group.items.length);
}
