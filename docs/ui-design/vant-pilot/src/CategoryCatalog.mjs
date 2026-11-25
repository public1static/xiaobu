export const CATEGORY_KEY = 'hezhang-vant-pilot-category-v1';
export const CATEGORIES = Object.freeze({
  expense: Object.freeze([
    '餐饮',
    '买菜',
    '交通',
    '日用品',
    '住房',
    '购物',
    '娱乐',
    '旅行',
    '医疗',
    '教育',
    '随礼',
    '其它',
  ]),
  income: Object.freeze(['工资', '奖金', '兼职', '收礼', '利息', '其它']),
});
const KINDS = ['expense', 'income'];
const LIMIT = 80;
const clean = (v) => (typeof v === 'string' ? v.trim().replace(/\s+/gu, ' ') : '');
export const categoryKey = (v) => clean(v).normalize('NFKC').toLowerCase().replace(/\s+/gu, '');
const noControls = (v) => typeof v === 'string' && !/[\p{Cc}\p{Cf}]/u.test(v);
export const validCategoryLabel = (v) =>
  noControls(v) && v === clean(v) && [...v].length >= 1 && [...v].length <= 16;
export const defaultCategoryCatalog = () => ({
  version: 1,
  expense: [...CATEGORIES.expense],
  income: [...CATEGORIES.income],
});
// 按收支方向识别旧兜底名称，不改动另一方向的自定义分类。
export function categoryName(kind, label) {
  return (kind === 'expense' && label === '其他支出') || (kind === 'income' && label === '其他收入')
    ? '其它'
    : label;
}
// 升级已排序的旧目录，只合并与新兜底项重合的名称。
export function upgradeCatalog(catalog) {
  if (!catalog || !KINDS.every((k) => Array.isArray(catalog[k]))) return catalog;
  const result = { ...catalog };
  for (const kind of KINDS) {
    if (!catalog[kind].some((label) => categoryName(kind, label) !== label)) continue;
    let seen = false;
    result[kind] = catalog[kind]
      .map((label) => categoryName(kind, label))
      .filter((label) => {
        if (label !== '其它') return true;
        if (seen) return false;
        seen = true;
        return true;
      });
  }
  return result;
}
export function validate(c) {
  if (!c || c.version !== 1) return false;
  return KINDS.every(
    (kind) =>
      Array.isArray(c[kind]) &&
      c[kind].length <= LIMIT &&
      c[kind].every(validCategoryLabel) &&
      new Set(c[kind].map(categoryKey)).size === c[kind].length &&
      CATEGORIES[kind].every((name) => c[kind].includes(name)),
  );
}
export function loadCategoryCatalog(storage) {
  try {
    const source = storage.getItem(CATEGORY_KEY);
    if (source === null) return defaultCategoryCatalog();
    const c = upgradeCatalog(JSON.parse(source));
    return validate(c)
      ? { version: 1, expense: [...c.expense], income: [...c.income] }
      : defaultCategoryCatalog();
  } catch {
    return defaultCategoryCatalog();
  }
}
export function persistCategoryCatalog(storage, catalog) {
  if (!validate(catalog)) return { error: '分类目录格式不正确。' };
  try {
    storage.setItem(CATEGORY_KEY, JSON.stringify(catalog));
    return { value: catalog };
  } catch {
    return { error: '无法保存分类，请检查浏览器存储权限后重试。' };
  }
}
export function addCategory(catalog, kind, name) {
  if (!KINDS.includes(kind) || !validate(catalog)) return { error: '请选择支出或收入分类。' };
  const label = categoryName(kind, clean(name));
  if (!noControls(name) || !validCategoryLabel(label))
    return { error: '请输入 1 到 16 个字的分类名称。' };
  if (catalog[kind].some((x) => categoryKey(x) === categoryKey(label)))
    return { error: '已有同名分类，请使用其他名称。' };
  if (catalog[kind].length >= LIMIT) return { error: '这个类型的分类数量已达到 80 个。' };
  return { value: { ...catalog, [kind]: [...catalog[kind], label] }, label };
}
export function reorderCategories(catalog, kind, from, to) {
  if (
    !KINDS.includes(kind) ||
    !validate(catalog) ||
    !Number.isInteger(from) ||
    !Number.isInteger(to) ||
    from < 0 ||
    to < 0 ||
    from >= catalog[kind].length ||
    to >= catalog[kind].length ||
    from === to
  )
    return catalog;
  const items = [...catalog[kind]];
  items.splice(to, 0, items.splice(from, 1)[0]);
  return { ...catalog, [kind]: items };
}
