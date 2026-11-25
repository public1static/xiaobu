const test = require('node:test');
const assert = require('node:assert/strict');

test('book membership isolates directories, permits shared entries and enforces revocation on existing handles', async () => {
  const { catalog, exampleStorage, forBook, INDEX } = await import('./src/book-space.mjs');
  const { KEY } = await import('./src/ledger.mjs');
  const { GIFT_CONTACTS_KEY } = await import('./src/gift-options.mjs');
  const map = new Map(),
    storage = { getItem: (k) => map.get(k) ?? null, setItem: (k, v) => map.set(k, v) };
  const directory = catalog(storage);
  directory.books.push({ id: 'party', owner: 'me', name: '家宴', members: { me: 'owner' } });
  storage.setItem(INDEX, JSON.stringify(directory));
  const first = forBook(storage, 'home-me', 'me'),
    party = forBook(storage, 'party', 'me');
  assert.deepEqual(JSON.parse(first.getItem(GIFT_CONTACTS_KEY)).contacts, []);
  assert.deepEqual(JSON.parse(first.getItem(KEY)).rows, []);
  const example = exampleStorage();
  assert.ok(JSON.parse(example.getItem(KEY)).rows.length);
  assert.equal(example.setItem, undefined);
  assert.deepEqual(JSON.parse(party.getItem(GIFT_CONTACTS_KEY)).contacts, []);
  assert.throws(() => forBook(storage, 'party', 'family'));
  const book = directory.books.at(-1);
  book.members.family = 'editor';
  storage.setItem(INDEX, JSON.stringify(directory));
  const guest = forBook(storage, 'party', 'family');
  const data = JSON.parse(guest.getItem(KEY));
  data.rows.push({ id: 'row', author: 'family' });
  guest.setItem(KEY, JSON.stringify(data));
  assert.equal(JSON.parse(party.getItem(KEY)).rows[0].author, 'family');
  assert.ok(!JSON.parse(first.getItem(KEY)).rows.some((row) => row.id === 'row'));
  book.members.family = 'reader';
  storage.setItem(INDEX, JSON.stringify(directory));
  assert.throws(() => guest.setItem(KEY, '{}'));
  assert.equal(JSON.parse(guest.getItem(KEY)).rows.length, 1);
  delete book.members.family;
  storage.setItem(INDEX, JSON.stringify(directory));
  assert.throws(() => guest.getItem(KEY));
});
