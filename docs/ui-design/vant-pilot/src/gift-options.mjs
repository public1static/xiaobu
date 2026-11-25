export const GIFT_CONTACTS_KEY = 'hezhang-vant-pilot-v1:gift-contacts:personal';
export const GIFT_REASONS = Object.freeze([
  '婚礼',
  '生日',
  '满月',
  '乔迁',
  '升学',
  '节日',
  '探望',
  '订婚',
  '百日',
  '周岁',
  '寿宴',
  '开业',
  '毕业',
  '慰问',
  '丧事',
]);
export const normalizeContact = (value) =>
  String(value ?? '')
    .normalize('NFKC')
    .trim()
    .toLocaleLowerCase('zh-CN')
    .replace(/\s+/gu, '');
const clean = (value) => String(value ?? '').trim();
const idValid = (id) => typeof id === 'string' && id.length > 0 && id.length <= 100;
export const contactValid = (c) =>
  c &&
  idValid(c.id) &&
  typeof c.name === 'string' &&
  c.name.trim() &&
  c.name.length <= 40 &&
  typeof c.distinction === 'string' &&
  c.distinction.length <= 40;
function localContacts(storage) {
  const raw = storage?.getItem(GIFT_CONTACTS_KEY);
  if (raw == null) return [];
  const data = JSON.parse(raw);
  if (
    data?.version !== 1 ||
    !Array.isArray(data.contacts) ||
    data.contacts.length > 1500 ||
    !data.contacts.every(contactValid) ||
    new Set(data.contacts.map((c) => c.id)).size !== data.contacts.length
  )
    throw new Error('联系人目录无法读取，未新增。');
  return data.contacts;
}
function combine(seeds, custom) {
  const savedIds = new Set(custom.map((contact) => contact.id));
  return [...custom, ...seeds.filter((contact) => !savedIds.has(contact.id))];
}
export function readGiftContacts(storage, seeds = []) {
  try {
    return combine(seeds, localContacts(storage));
  } catch {
    return [...seeds];
  }
}
export function sameNameContacts(contacts, name) {
  const key = normalizeContact(name);
  return key ? contacts.filter((item) => normalizeContact(item.name) === key) : [];
}
// 家庭按名称和区分说明识别；旧资料没有说明时仍归入原家庭。
export function householdKey(person) {
  return JSON.stringify([
    normalizeContact(person.household),
    normalizeContact(person.householdNote),
  ]);
}
// 候选从亲友资料汇总，沿用姓名输入的匹配和选中格式。
export function householdOptions(contacts) {
  const options = new Map();
  for (const person of contacts) {
    if (!person.household) continue;
    const id = householdKey(person);
    if (!options.has(id))
      options.set(id, { id, name: person.household, distinction: person.householdNote || '' });
  }
  return [...options.values()];
}
// 保存亲友时才确定家庭，取消表单不会留下空家庭。
export function resolveHousehold(contacts, model, createSame, note) {
  const options = householdOptions(contacts),
    same = sameNameContacts(options, model.name);
  if (createSame) {
    if (!normalizeContact(note)) return { error: '请输入家庭区分说明' };
    if (same.some((h) => normalizeContact(h.distinction) === normalizeContact(note)))
      return { error: '此说明已用于同名家庭' };
  } else {
    const selected = model.id ? options.find((h) => h.id === model.id) : null;
    if (selected) return { contact: selected };
    if (same.length > 1) return { ambiguous: true, error: '请选择同名家庭' };
    if (same.length === 1) return { contact: same[0] };
  }
  return { contact: { name: model.name.trim(), distinction: createSame ? note.trim() : '' } };
}
export function matchGiftContacts(contacts, query, limit = 10) {
  const key = normalizeContact(query);
  return contacts
    .map((contact, index) => ({
      contact,
      index,
      exact: !!key && normalizeContact(contact.name) === key,
    }))
    .filter(
      ({ contact }) =>
        !key ||
        [contact.name, contact.distinction, ...(contact.aliases || [])].some((value) =>
          normalizeContact(value).includes(key),
        ),
    )
    .sort((a, b) => Number(b.exact) - Number(a.exact) || a.index - b.index)
    .slice(0, limit)
    .map(({ contact }) => contact);
}
export function newGiftContact(
  storage,
  seeds,
  input,
  makeId = () =>
    'contact-' +
    (globalThis.crypto?.randomUUID?.() || Date.now() + '-' + Math.random().toString(36).slice(2)),
) {
  const name = clean(input?.name),
    distinction = clean(input?.distinction);
  if (!name || name.length > 40)
    return { field: 'person', error: '请输入不超过 40 字的联系人姓名。' };
  if (distinction.length > 40)
    return { field: 'distinction', error: '区分称呼或关系不能超过 40 字。' };
  if (!storage?.setItem) return { error: '无法访问浏览器存储，联系人未新增。' };
  try {
    const custom = localContacts(storage),
      contacts = combine(seeds, custom),
      same = sameNameContacts(contacts, name);
    if (same.length && !normalizeContact(distinction))
      return { field: 'distinction', error: '已有同名联系人，请填写可区分的称呼或关系。' };
    if (same.some((c) => normalizeContact(c.distinction) === normalizeContact(distinction)))
      return {
        field: 'distinction',
        error: '这个称呼或关系已有同名联系人使用，请选择已有联系人或换一个。',
      };
    if (custom.length >= 1500) return { error: '联系人目录已满，未新增。' };
    const id = makeId();
    if (!idValid(id) || contacts.some((c) => c.id === id))
      return { error: '未能分配联系人编号，请重试。' };
    const contact = {
        id,
        name,
        distinction,
        ...(input.giftMember === false ? { giftMember: false } : {}),
        ...(input.householdNote ? { householdNote: clean(input.householdNote).slice(0, 40) } : {}),
        ...(input.aliases?.length ? { aliases: input.aliases } : {}),
        ...(input.village ? { village: input.village } : {}),
        ...(clean(input.household) ? { household: clean(input.household).slice(0, 40) } : {}),
      },
      updated = [contact, ...custom];
    storage.setItem(GIFT_CONTACTS_KEY, JSON.stringify({ version: 1, contacts: updated }));
    return { contact, contacts: combine(seeds, updated) };
  } catch {
    return { error: '无法保存联系人，现有联系人未改变。' };
  }
}
// 使用内存快照解析这次输入，调用方把目录变更与合同或流水一次写入。
export function preparePerson(
  storage,
  model,
  { rental = false, createSame = false, distinction = '' } = {},
) {
  const before = storage.getItem(GIFT_CONTACTS_KEY);
  let after = before;
  const stage = {
    getItem: () => after,
    setItem: (_, v) => {
      after = v;
    },
  };
  const directory = localContacts(stage),
    same = sameNameContacts(directory, model.name);
  let contact;
  if (!createSame && model.id) {
    contact = directory.find((p) => p.id === model.id);
    if (!contact) return { error: '联系人资料已更新，请重新输入姓名。' };
  } else if (!createSame) {
    if (same.length > 1) return { ambiguous: true, error: '请选择同名联系人。' };
    contact = same[0];
  }
  if (!contact) {
    const result = newGiftContact(stage, [], {
      name: model.name,
      distinction: createSame ? distinction : '',
      giftMember: !rental,
    });
    if (result.error) return result;
    contact = result.contact;
  } else if (!rental && contact.giftMember === false) {
    contact = { ...contact, giftMember: true };
    after = JSON.stringify({
      version: 1,
      contacts: directory.map((p) => (p.id === contact.id ? contact : p)),
    });
  }
  return { contact, before, after };
}
// 将资料修改保存为同一编号的覆盖记录，保留已有流水的关联。
export function editGiftContact(storage, seeds, id, input) {
  try {
    const custom = localContacts(storage);
    const contacts = combine(seeds, custom);
    const existing = contacts.find((item) => item.id === id);
    if (!existing) return { error: '联系人已不存在。' };
    const contact = {
      ...existing,
      ...(input.giftMember === true ? { giftMember: true } : {}),
      aliases: input.aliases ?? existing.aliases ?? [],
      village: input.village ?? existing.village ?? '',
      householdNote: clean(input.householdNote ?? existing.householdNote).slice(0, 40),
      household: clean(input.household ?? existing.household).slice(0, 40),
      name: clean(input.name),
      distinction: clean(input.distinction),
    };
    if (!contactValid(contact)) return { error: '请输入姓名，姓名和关系最多 40 字。' };
    const others = sameNameContacts(
      contacts.filter((item) => item.id !== id),
      contact.name,
    );
    if (
      others.length &&
      (!contact.distinction ||
        others.some(
          (item) => normalizeContact(item.distinction) === normalizeContact(contact.distinction),
        ))
    )
      return { field: 'distinction', error: '请填写能区分同名联系人的称呼或关系。' };
    const updated = [contact, ...custom.filter((item) => item.id !== id)];
    if (updated.length > 1500) return { error: '联系人目录已满。' };
    storage.setItem(GIFT_CONTACTS_KEY, JSON.stringify({ version: 1, contacts: updated }));
    return { contact };
  } catch {
    return { error: '联系人资料保存失败。' };
  }
}
export function reasonDraft(value) {
  const reason = clean(value);
  return GIFT_REASONS.includes(reason)
    ? { preset: reason, custom: '' }
    : { preset: reason ? 'custom' : '', custom: reason };
}
export function resolveGiftReason(preset, custom) {
  return preset === 'custom' ? clean(custom) : GIFT_REASONS.includes(preset) ? preset : '';
}

// 主动添加亲友时复用可唯一识别的出租方，保留其合同所引用的编号。
export function addFriend(storage, input) {
  const same = sameNameContacts(readGiftContacts(storage), input.name);
  const matches = same.filter(
    (c) => normalizeContact(c.distinction) === normalizeContact(input.distinction),
  );
  const existing =
    matches.length === 1 ? matches[0] : same.length === 1 && !same[0].distinction ? same[0] : null;
  if (existing?.giftMember === false) {
    return editGiftContact(storage, [], existing.id, { ...input, giftMember: true });
  }
  return newGiftContact(storage, [], input);
}

// 换礼单时替换继承的默认值，未配置的项目沿用手工填写值。
export function applyListDefaults(list, previous, date, reason, today) {
  return {
    date: list?.date || (date === previous?.date ? today : date) || today,
    reason: list?.defaultReason || (reason === previous?.defaultReason ? '' : reason),
  };
}
