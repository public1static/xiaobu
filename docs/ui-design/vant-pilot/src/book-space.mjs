import { KEY, CONTACTS, fixture, localDay } from './ledger.mjs';
import { CATEGORY_KEY } from './CategoryCatalog.mjs';
import { readGiftContacts, GIFT_CONTACTS_KEY } from './gift-options.mjs';

// 目录记录本机演示身份的成员关系；各账本资料单独保存。
export const INDEX = 'hezhang:book-directory';
export const identities = { me: '我', family: '家人' };
const dataKey = (id) => 'hezhang:book:' + id;

// 每个身份首次使用自动获得一本自己的账本。
export function catalog(storage) {
  const raw = storage.getItem(INDEX);
  if (raw) return JSON.parse(raw);
  const result = {
    books: ['me', 'family'].map((owner) => ({
      id: 'home-' + owner,
      name: '我的账本',
      owner,
      members: { [owner]: 'owner' },
    })),
    invitations: [],
    last: {},
  };
  storage.setItem(INDEX, JSON.stringify(result));
  return result;
}

// 游客只读取内存中的虚构账本，不接触浏览器保存的成员目录和私人账本。
export function exampleStorage(day = localDay(new Date())) {
  const book = fixture(day);
  book.rentalHomes = [{ id: 'sample-home', name: '朝阳路公寓' }];
  book.leases = [
    {
      id: 'sample-lease',
      homeId: 'sample-home',
      party: '陈女士',
      start: day.slice(0, 4) + '-01-01',
      end: day.slice(0, 4) + '-12-31',
      rent: 238000,
      deposit: 238000,
      cycle: 'month',
      billId: 'sample-rent',
    },
  ];
  book.fixedBills = [
    {
      id: 'sample-rent',
      name: '朝阳路公寓房租',
      amount: 238000,
      category: '住房',
      account: '银行卡',
      cycle: 'month',
      start: day,
      paid: {},
    },
  ];
  const values = {
    [KEY]: JSON.stringify(book),
    [GIFT_CONTACTS_KEY]: JSON.stringify({ version: 1, contacts: CONTACTS }),
  };
  return { getItem: (key) => values[key] ?? null };
}

// 切账本时重新实例化视图，绑定的存储始终指向同一账本。
export function forBook(storage, id, actor) {
  const key = dataKey(id);
  function access(write = false) {
    const role = catalog(storage).books.find((b) => b.id === id)?.members[actor];
    if (!role || (write && role === 'reader')) throw new Error('当前身份没有此操作权限。');
  }
  // 一次性写入流水与亲友索引，合并操作不会只更新一侧。
  function batch(values) {
    access(true);
    const data = JSON.parse(storage.getItem(key) || '{}');
    for (const [k, v] of Object.entries(values)) {
      if (v === null) delete data[k];
      else data[k] = v;
    }
    storage.setItem(key, JSON.stringify(data));
  }
  access();
  if (!storage.getItem(key)) {
    const legacy = id === 'home-me';
    const book = { version: 1, rows: [], trash: [] };
    const initial = {
      [KEY]: (legacy && storage.getItem(KEY)) || JSON.stringify(book),
      [GIFT_CONTACTS_KEY]: JSON.stringify({
        version: 1,
        contacts: legacy ? readGiftContacts(storage, []) : [],
      }),
    };
    if (legacy && storage.getItem(CATEGORY_KEY))
      initial[CATEGORY_KEY] = storage.getItem(CATEGORY_KEY);
    storage.setItem(key, JSON.stringify(initial));
  }
  return {
    getItem(k) {
      access();
      return JSON.parse(storage.getItem(key))[k] ?? null;
    },
    setItem: (k, v) => batch({ [k]: v }),
    removeItem: (k) => batch({ [k]: null }),
    batch,
  };
}
