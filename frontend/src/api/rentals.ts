import { request } from './request';
import type { FixedBillPage } from './fixed-bills';

export const rentalCycles = {
  MONTHLY: { name: '月付', months: 1 },
  QUARTERLY: { name: '季付', months: 3 },
  SEMIANNUAL: { name: '半年付', months: 6 },
  ANNUAL: { name: '年付', months: 12 },
} as const;

export type RentalCycle = keyof typeof rentalCycles;
type RentalHomeInput = { name: string; address: string; note: string };

export interface RentalHome extends RentalHomeInput {
  id: string;
  contractCount: number;
}

interface RentalContractInput {
  homeId: string;
  landlordName: string;
  startsOn: string;
  endsOn: string;
  monthlyRentCents: number;
  depositCents: number;
  cycle: RentalCycle;
  firstDueOn: string | null;
  note: string;
  existingBillId?: string;
  remindDays?: number;
}

export interface RentalContract extends RentalContractInput {
  id: string;
  homeName: string;
  remindDays: number;
  handledEnd: string | null;
  billId: string | null;
  dueDayOfMonth: number | null;
  periodAmountCents: number;
}

export interface RentalPage<T> {
  entries: T[];
  totalCount: number;
  page: number;
  pageSize: number;
}

const path = (ledgerId: string) => '/ledgers/' + encodeURIComponent(ledgerId) + '/rentals';

export function listRentalHomes(ledgerId: string, page: number) {
  return request<RentalPage<RentalHome>>(path(ledgerId) + '/homes', 'GET', { page });
}

export function saveRentalHome(ledgerId: string, home: RentalHomeInput, id?: string) {
  return request<RentalHome>(
    path(ledgerId) + '/homes' + (id ? '/' + encodeURIComponent(id) : ''),
    id ? 'PUT' : 'POST',
    home,
  );
}

export function listRentalContracts(ledgerId: string, page: number, homeId?: string) {
  return request<RentalPage<RentalContract>>(path(ledgerId) + '/contracts', 'GET', {
    page,
    ...(homeId ? { homeId } : {}),
  });
}

export function createRentalContract(ledgerId: string, contract: RentalContractInput) {
  return request<RentalContract>(path(ledgerId) + '/contracts', 'POST', contract);
}

export function generateRentalBill(ledgerId: string, contractId: string, firstDueOn: string) {
  return request<RentalContract>(
    path(ledgerId) + '/contracts/' + encodeURIComponent(contractId) + '/bill',
    'PUT',
    { firstDueOn },
  );
}

export interface RentalBillPlan {
  billId: string;
  name: string;
  firstDueOn: string;
  amountCents: number;
}

export function listRentalBillPlans(
  ledgerId: string,
  window: { cycle: RentalCycle; startsOn: string; endsOn: string },
  page: number,
) {
  return request<RentalPage<RentalBillPlan>>(path(ledgerId) + '/bill-candidates', 'GET', {
    cycle: window.cycle,
    startsOn: window.startsOn,
    endsOn: window.endsOn,
    page,
  });
}

export function linkRentalBill(ledgerId: string, contractId: string, billId: string) {
  return request<RentalContract>(
    path(ledgerId) + '/contracts/' + encodeURIComponent(contractId) + '/bill-link',
    'PUT',
    { billId },
  );
}

export function listPendingRentalPeriods(ledgerId: string, month: string, page = 0) {
  return request<FixedBillPage>(path(ledgerId) + '/payment-periods', 'GET', { month, page });
}

export interface RentalDepositDraft {
  kind: 'PAY' | 'REFUND';
  amountCents: number;
  bookedOn: string;
  accountId: string | null;
  note: string;
}

export interface RentalDeposit extends RentalDepositDraft {
  id: string;
  accountName: string | null;
  version: number;
  canModify: boolean;
}

function depositsPath(ledgerId: string, contractId: string) {
  return path(ledgerId) + '/contracts/' + encodeURIComponent(contractId) + '/deposits';
}

export function listRentalDeposits(ledgerId: string, contractId: string, page = 0) {
  return request<RentalPage<RentalDeposit> & { heldCents: number }>(
    depositsPath(ledgerId, contractId),
    'GET',
    { page },
  );
}

export function recordRentalDeposit(
  ledgerId: string,
  contractId: string,
  draft: RentalDepositDraft,
) {
  return request<void>(depositsPath(ledgerId, contractId), 'POST', draft);
}

export function editRentalDeposit(
  ledgerId: string,
  contractId: string,
  entry: RentalDeposit,
  draft: Omit<RentalDepositDraft, 'kind'>,
) {
  return request<void>(
    depositsPath(ledgerId, contractId) + '/' + encodeURIComponent(entry.id),
    'PUT',
    {
      ...draft,
      expectedVersion: entry.version,
    },
  );
}

export interface RentalExpiryReminder {
  contractId: string;
  homeName: string;
  landlordName: string;
  endsOn: string;
  remindDays: number;
  handledEnd: string | null;
  pending: boolean;
}

export function getRentalContract(ledgerId: string, contractId: string) {
  return request<RentalContract>(
    path(ledgerId) + '/contracts/' + encodeURIComponent(contractId),
    'GET',
  );
}

export function listRentalExpiryReminders(ledgerId: string, completed: boolean, page = 0) {
  return request<RentalPage<RentalExpiryReminder> & { pendingCount: number; today: string }>(
    path(ledgerId) + '/expiry-reminders',
    'GET',
    { completed, page },
  );
}

export function countRentalExpiryReminders(ledgerId: string) {
  return request<{ pendingCount: number }>(path(ledgerId) + '/expiry-reminders/count', 'GET');
}

function expiryPath(ledgerId: string, contractId: string) {
  return path(ledgerId) + '/contracts/' + encodeURIComponent(contractId) + '/expiry-reminder';
}

export function configureRentalExpiryReminder(
  ledgerId: string,
  contractId: string,
  remindDays: number,
) {
  return request<RentalContract>(expiryPath(ledgerId, contractId), 'PUT', { remindDays });
}

export function completeRentalExpiryReminder(
  ledgerId: string,
  contractId: string,
  expectedEndsOn: string,
) {
  return request<RentalContract>(expiryPath(ledgerId, contractId) + '/complete', 'PUT', {
    expectedEndsOn,
  });
}
