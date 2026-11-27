import { request } from './request';

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
}

export interface RentalContract extends RentalContractInput {
  id: string;
  homeName: string;
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
