'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const source = import('./src/gift-options.mjs');
const storage = () => {
  const map = new Map();
  return { getItem: (k) => map.get(k) ?? null, setItem: (k, v) => map.set(k, String(v)) };
};
const existing = [
  { id: 'seed-neighbor', name: '林小满', distinction: '街坊' },
  { id: 'seed-work', name: '林小满', distinction: '同事' },
];

test('same-name candidates expose stable identities; explicit new distinction is normalized before writing', async () => {
  const M = await source,
    db = storage();
  assert.deepEqual(
    M.matchGiftContacts(M.readGiftContacts(db, existing), '林小满').map((x) => x.id),
    existing.map((x) => x.id),
  );
  assert.deepEqual(
    M.matchGiftContacts(existing, '同事').map((x) => x.id),
    ['seed-work'],
  );
  assert.match(
    M.newGiftContact(db, existing, { name: '林 小满', distinction: '   ' }).error,
    /可区分/,
  );
  assert.match(
    M.newGiftContact(db, existing, { name: '林 小满', distinction: ' 同　事 ' }).error,
    /已有同名/,
  );
  assert.equal(db.getItem(M.GIFT_CONTACTS_KEY), null);
  const saved = M.newGiftContact(
    db,
    existing,
    { name: '林小满', distinction: '大学同学' },
    () => 'new-lin',
  );
  assert.equal(saved.contact.id, 'new-lin');
  assert.deepEqual(
    M.sameNameContacts(M.readGiftContacts(db, existing), '林 小满').map((x) => [
      x.id,
      x.distinction,
    ]),
    [
      ['new-lin', '大学同学'],
      ['seed-neighbor', '街坊'],
      ['seed-work', '同事'],
    ],
  );
  assert.match(
    M.newGiftContact(db, existing, { name: '林小满', distinction: '大学 同学' }).error,
    /已有同名/,
  );
  assert.deepEqual(
    M.readGiftContacts(storage(), existing),
    existing,
    'another browser starts with only seed contacts',
  );
  assert.equal(
    db.getItem('hezhang-vant-pilot-v1'),
    null,
    'contact creation does not create or mutate an account entry',
  );
});

test('uniquely named new contact may omit distinction and cannot overwrite bad stored data or duplicate ID', async () => {
  const M = await source,
    db = storage();
  const julie = M.newGiftContact(db, existing, { name: 'JULIE' }, () => 'new-julie');
  assert.deepEqual(julie.contact, { id: 'new-julie', name: 'JULIE', distinction: '' });
  assert.equal(
    M.matchGiftContacts(M.readGiftContacts(db, existing), 'ｊｕｌｉｅ')[0].id,
    'new-julie',
  );
  assert.match(M.newGiftContact(db, existing, { name: '杜甫' }, () => 'new-julie').error, /编号/);
  db.setItem(M.GIFT_CONTACTS_KEY, '{broken');
  assert.deepEqual(M.readGiftContacts(db, existing), existing);
  assert.match(M.newGiftContact(db, existing, { name: '李白' }).error, /未改变/);
  assert.equal(db.getItem(M.GIFT_CONTACTS_KEY), '{broken');
});

test('preset reason is optional and an earlier custom reason remains editable without conversion', async () => {
  const M = await source;
  assert.deepEqual(M.reasonDraft('婚礼'), { preset: '婚礼', custom: '' });
  assert.deepEqual(M.reasonDraft('回老家探亲'), { preset: 'custom', custom: '回老家探亲' });
  assert.equal(M.resolveGiftReason('custom', '  满月回礼  '), '满月回礼');
  assert.equal(M.resolveGiftReason('生日', '上一段未用的草稿'), '生日');
  assert.equal(M.resolveGiftReason('', '不保存的草稿'), '');
  assert.equal(M.resolveGiftReason('custom', '  '), '');
});
