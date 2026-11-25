const test = require('node:test');
const assert = require('node:assert/strict');
test('rent is reminded once per unpaid installment and expiry handling follows the end date', async () => {
  const { reminders } = await import('./src/reminders.mjs');
  const book = {
    rows: [{ id: 'payment', kind: 'expense' }],
    fixedBills: [
      {
        id: 'rent',
        cycle: 'quarter',
        start: '2026-01-31',
        paid: { '2026-01': 'payment' },
        amount: 960000,
      },
    ],
    leases: [{ id: 'lease', billId: 'rent', start: '2026-01-01', end: '2026-05-01' }],
  };
  const list = () => reminders(book, '2026-04-27');
  assert.deepEqual(
    list().map((r) => r.due),
    ['2026-04-30', '2026-05-01'],
  );
  book.leases[0].handledEnd = '2026-05-01';
  assert.equal(list().length, 1);
  book.fixedBills[0].remindDays = 'off';
  assert.equal(list()[0].later, true);
  book.leases[0].end = '2026-05-02';
  assert.ok(list().some((i) => i.kind === 'lease'));
  book.fixedBills[0].remindDays = '0';
  book.rows = [];
  assert.equal(list()[0].due, '2026-01-31');
});

test('a new business source uses the same scheduling, grouping and navigation contract', async () => {
  const { reminders, groupReminders } = await import('./src/reminders.mjs');
  const source = {
    kind: 'event',
    days: [0, 3],
    defaultDays: '3',
    *pending(book) {
      for (const item of book.events)
        yield {
          ...item,
          occurrence: item.due,
          target: { workspace: 'events', props: { eventId: item.id } },
        };
    },
  };
  const book = {
    events: [
      { id: 'a', due: '2026-12-31', title: '年会' },
      { id: 'b', due: '2027-01-02', title: '新年聚会' },
      { id: 'c', due: '2026-12-30', title: '关闭', remindDays: 'off' },
      { id: 'd', due: '2027-01-01', title: '当天提醒', remindDays: '0' },
    ],
  };
  const items = reminders(book, '2026-12-31', [source]);
  assert.deepEqual(
    items.map((i) => i.target.props.eventId),
    ['c', 'a', 'd', 'b'],
  );
  assert.deepEqual(
    groupReminders(items, '2026-12-31').map((g) => g.tone),
    ['late', 'today', 'soon', 'later'],
  );
  assert.deepEqual(
    groupReminders(items, '2027-01-01').map((g) => g.tone),
    ['late', 'soon', 'later'],
  );
});

test('manual reminders keep month-end anchors, notify only at scheduled time and complete once per date', async () => {
  const { completeTask, occurrenceDate, triggerAt } = await import('./src/manual-reminders.mjs');
  const { reminders } = await import('./src/reminders.mjs');
  const t = {
    id: 'renew',
    title: '续租',
    recipient: 'family',
    owner: 'me',
    repeat: 'month',
    start: '2028-01-31',
    index: 0,
    time: '09:00',
    lead: '1',
    history: [],
  };
  const book = { rows: [], manualReminders: [t] };
  assert.equal(triggerAt(t, t.start), '2028-01-30T09:00');
  assert.equal(
    reminders(book, '2028-01-30', undefined, { now: '2028-01-30T08:59', actor: 'family' })[0].later,
    true,
  );
  assert.equal(
    reminders(book, '2028-01-30', undefined, { now: '2028-01-30T09:00', actor: 'family' })[0].later,
    false,
  );
  const next = completeTask(book, t.id, t.start);
  assert.equal(completeTask(next, t.id, t.start), null);
  assert.equal(next.manualReminders[0].history[0].title, '续租');
  assert.equal(occurrenceDate(t.start, t.repeat, 1), '2028-02-29');
  assert.equal(occurrenceDate(t.start, t.repeat, 2), '2028-03-31');
});

test('preview uses the scheduled time across past triggers and short months', async () => {
  const { previewTimes, occurrenceDate } = await import('./src/manual-reminders.mjs');
  const t = { start: '2026-01-31', index: 0, repeat: 'quarter', time: '09:00', lead: '1' };
  assert.deepEqual(previewTimes(t, '2026-01-30T09:01'), [
    '2026-04-29T09:00',
    '2026-07-30T09:00',
    '2026-10-30T09:00',
  ]);
  assert.equal(occurrenceDate(t.start, 'halfYear', 1), '2026-07-31');
  assert.equal(occurrenceDate(t.start, 'fortnight', 1), '2026-02-14');
  assert.equal(occurrenceDate(t.start, 'day', 1), '2026-02-01');
});

test('lunar and holiday reminders preserve lunar anchors and channel preferences', async () => {
  const { taskDate, taskChannels, previewTimes } = await import('./src/manual-reminders.mjs');
  const { lunarParts, lunarDate } = await import('./src/reminder-calendar.mjs');
  const t = {
    dateMode: 'festival',
    festival: '春节',
    start: '2026-02-17',
    index: 0,
    repeat: 'year',
    time: '09:00',
    lead: '1',
  };
  assert.equal(taskDate(t, 1), '2027-02-06');
  assert.equal(previewTimes(t, '2026-11-24T12:00')[0], '2027-02-05T09:00');
  const leap = lunarParts('2025-07-30');
  assert.equal(leap.month, '6bis');
  assert.equal(lunarDate(leap.year, leap.month, leap.day), '2025-07-30');
  assert.equal(
    lunarParts(taskDate({ ...t, dateMode: 'lunar', start: '2025-07-30' }, 1)).month,
    '6',
  );
  assert.deepEqual(taskChannels({ wechat: false }), ['badge']);
  assert.deepEqual(taskChannels({ channels: [] }), []);
});

test('advance settlement reveals the next quarterly payment while keeping arrears', async () => {
  const { reminders } = await import('./src/reminders.mjs');
  const bill = {
    id: 'rent',
    name: '房租',
    cycle: 'quarter',
    start: '2026-03-31',
    amount: 900000,
    paid: {},
  };
  const book = { rows: [], fixedBills: [bill] };
  const periods = () => reminders(book, '2026-04-01').map((i) => [i.due, i.later]);
  assert.deepEqual(periods(), [
    ['2026-03-31', false],
    ['2026-06-30', true],
  ]);
  book.rows.push({ id: 'p', kind: 'expense' });
  bill.paid['2026-06'] = 'p';
  assert.deepEqual(periods(), [
    ['2026-03-31', false],
    ['2026-09-30', true],
  ]);
});
