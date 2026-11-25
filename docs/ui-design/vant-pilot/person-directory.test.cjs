const test = require('node:test');
const assert = require('node:assert/strict');
test('rent and gifts resolve a shared person only on commit and require a choice for duplicate names', async () => {
  const { preparePerson, readGiftContacts, addFriend } = await import('./src/gift-options.mjs');
  let raw = null;
  const storage = {
    getItem: () => raw,
    setItem: (_, v) => {
      raw = v;
    },
  };
  const who = { name: '陈女士' };
  const rental = preparePerson(storage, who, { rental: true });
  assert.deepEqual(readGiftContacts(storage), []);
  raw = rental.after;
  assert.deepEqual(
    readGiftContacts(storage).filter((p) => p.giftMember !== false),
    [],
  );
  const gift = preparePerson(storage, who);
  assert.equal(gift.contact.id, rental.contact.id);
  assert.equal(readGiftContacts(storage)[0].giftMember, false);
  raw = gift.after;
  const second = preparePerson(storage, who, {
    rental: true,
    createSame: true,
    distinction: '东区房东',
  });
  raw = second.after;
  assert.equal(preparePerson(storage, who).ambiguous, true);
  assert.equal(
    preparePerson(storage, { ...who, id: second.contact.id }, { rental: true }).contact.id,
    second.contact.id,
  );
  const friend = addFriend(storage, { ...who, distinction: '东区房东' });
  assert.equal(friend.contact.id, second.contact.id);
  assert.equal(readGiftContacts(storage).length, 2);
});
