import calendar from 'lunar-javascript';
const { Solar, Lunar, LunarYear } = calendar;
const digits = '一二三四五六七八九十';
const monthNames = ['正', '二', '三', '四', '五', '六', '七', '八', '九', '十', '冬', '腊'];
export const festivals = {
  元旦: ['solar', 1, 1],
  春节: ['lunar', 1, 1],
  元宵节: ['lunar', 1, 15],
  清明节: ['term', 0, 0],
  劳动节: ['solar', 5, 1],
  端午节: ['lunar', 5, 5],
  中秋节: ['lunar', 8, 15],
  国庆节: ['solar', 10, 1],
  重阳节: ['lunar', 9, 9],
};
// 按公历年月日换算，结果与设备时区和浏览器内置历表无关。
export function lunarParts(date) {
  const [y, m, d] = date.split('-').map(Number);
  const l = Solar.fromYmd(y, m, d).getLunar(),
    month = l.getMonth();
  return {
    year: l.getYear(),
    month: month < 0 ? String(-month) + 'bis' : String(month),
    day: l.getDay(),
  };
}
export const monthLabel = (m) =>
  (m.endsWith('bis') ? '闰' : '') + monthNames[parseInt(m) - 1] + '月';
export const dayLabel = (d) =>
  d <= 10
    ? '初' + digits[d - 1]
    : d === 20
      ? '二十'
      : d === 30
        ? '三十'
        : (d < 20 ? '十' : '廿') + digits[(d % 10) - 1];
// 按每个农历月的真实天数建立索引，保留闰月。
export function lunarMonths(year) {
  return LunarYear.fromYear(year)
    .getMonths()
    .filter((m) => m.getYear() === year)
    .map((m) => ({
      month: m.getMonth() < 0 ? String(-m.getMonth()) + 'bis' : String(m.getMonth()),
      length: m.getDayCount(),
    }));
}
// 无对应闰月的年份使用同名普通月份，小月三十落到该月最后一天。
export function lunarDate(year, month, day) {
  const months = lunarMonths(year);
  const m =
    months.find((v) => v.month === month) ||
    months.find((v) => v.month === month.replace('bis', ''));
  return Lunar.fromYmd(
    year,
    parseInt(m.month) * (m.month.endsWith('bis') ? -1 : 1),
    Math.min(day, m.length),
  )
    .getSolar()
    .toYmd();
}
export function festivalDate(name, year) {
  const [cal, m, d] = festivals[name];
  if (cal === 'term') return Solar.fromYmd(year, 4, 1).getLunar().getJieQiTable()['清明'].toYmd();
  return cal === 'lunar'
    ? lunarDate(year, String(m), d)
    : new Date(Date.UTC(year, m - 1, d, 12)).toISOString().slice(0, 10);
}
export function dateDescription(date, mode, festival) {
  if (mode === 'festival') return festival + ' · ' + date;
  if (mode !== 'lunar') return date;
  const p = lunarParts(date);
  return monthLabel(p.month) + dayLabel(p.day) + ' · ' + date;
}
