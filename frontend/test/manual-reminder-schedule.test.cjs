const { test } = require('node:test');
const assert = require('node:assert/strict');
const schedule = import('../src/pages/ledger/manual-reminder-schedule.ts');

test('月末提醒短月截断后回原日号，维持北京时间和提前量', async () => {
  const { previewManualReminderTimes } = await schedule;
  assert.deepEqual(
    previewManualReminderTimes(
      '2027-02-28',
      '2027-01-31',
      'MONTHLY',
      1,
      '09:30',
      '2027-02-01 00:00',
    ),
    ['2027-02-27 09:30', '2027-03-30 09:30', '2027-04-29 09:30'],
  );
});

test('重复预览跳过过去时间，单次可补记，闰日会回到锚点', async () => {
  const { previewManualReminderTimes } = await schedule;
  assert.deepEqual(
    previewManualReminderTimes(
      '2024-02-29',
      '2024-02-29',
      'YEARLY',
      0,
      '09:00',
      '2026-03-01 00:00',
    ),
    ['2027-02-28 09:00', '2028-02-29 09:00', '2029-02-28 09:00'],
  );
  assert.deepEqual(
    previewManualReminderTimes('2025-01-31', '2025-01-31', 'ONCE', 3, '10:30', '2027-01-01 00:00'),
    ['2025-01-28 10:30'],
  );
});
