export const rentalExpiryOptions = [
  { days: 7, name: '提前 7 天' },
  { days: 15, name: '提前 15 天' },
  { days: 30, name: '提前 30 天' },
  { days: 0, name: '关闭提前提醒' },
] as const;

export const DEFAULT_RENTAL_EXPIRY_DAYS = 30;

export function rentalExpiryOptionIndex(days: number) {
  return Math.max(
    0,
    rentalExpiryOptions.findIndex((item) => item.days === days),
  );
}

export function isRentalExpiryPending(
  endsOn: string,
  handledEnd: string | null,
  days: number,
  today: string,
) {
  if (endsOn === handledEnd) return false;
  const threshold = new Date(today + 'T00:00:00Z');
  threshold.setUTCDate(threshold.getUTCDate() + days);
  return endsOn <= threshold.toISOString().slice(0, 10);
}
