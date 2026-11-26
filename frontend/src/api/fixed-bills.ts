import { request } from './request';

export interface FixedBillPeriod {
  billId: string;
  name: string;
  dueOn: string;
  amountCents: number;
  cycleMonths: number;
  categoryCode: string;
  accountId?: string | null;
  transactionId?: string | null;
  paidOn?: string | null;
  paidAmountCents?: number | null;
}

export interface FixedBillPage {
  entries: FixedBillPeriod[];
  totalCount: number;
  page: number;
  pageSize: number;
}

interface BillPayment {
  paidOn: string;
  amountCents: number;
  accountId: string | null;
}

interface BillPlan {
  name: string;
  firstDueOn: string;
  amountCents: number;
  categoryCode: string;
  accountId: string | null;
}

const path = (ledgerId: string) => '/ledgers/' + encodeURIComponent(ledgerId) + '/fixed-bills';

export function listFixedBillPeriods(
  ledgerId: string,
  month: string,
  paid: boolean,
  page: number,
  billId?: string,
) {
  return request<FixedBillPage>(path(ledgerId), 'GET', {
    month,
    paid,
    page,
    ...(billId ? { billId } : {}),
  });
}

export function createFixedBill(ledgerId: string, plan: BillPlan) {
  return request<FixedBillPeriod>(path(ledgerId), 'POST', plan);
}

export function payFixedBill(
  ledgerId: string,
  billId: string,
  month: string,
  payment: BillPayment,
) {
  return request<FixedBillPeriod>(
    path(ledgerId) + '/' + encodeURIComponent(billId) + '/periods/' + month + '/payment',
    'POST',
    payment,
  );
}

export interface FixedBillCandidate {
  id: string;
  version: number;
  bookedOn: string;
  amountCents: number;
  note: string;
  categoryCode: string;
  accountName?: string | null;
}

interface FixedBillCandidatePage {
  entries: FixedBillCandidate[];
  totalCount: number;
  page: number;
  pageSize: number;
}

const periodPath = (ledgerId: string, billId: string, month: string) =>
  path(ledgerId) + '/' + encodeURIComponent(billId) + '/periods/' + month;

export function getFixedBillPeriod(ledgerId: string, billId: string, month: string) {
  return request<FixedBillPeriod>(periodPath(ledgerId, billId, month), 'GET');
}

export function listFixedBillCandidates(
  ledgerId: string,
  billId: string,
  month: string,
  keyword: string,
  page: number,
) {
  return request<FixedBillCandidatePage>(
    periodPath(ledgerId, billId, month) + '/payment-candidates',
    'GET',
    { keyword, page },
  );
}

export function linkFixedBillPayment(
  ledgerId: string,
  billId: string,
  month: string,
  candidate: FixedBillCandidate,
) {
  return request<FixedBillPeriod>(periodPath(ledgerId, billId, month) + '/payment-link', 'POST', {
    transactionId: candidate.id,
    version: candidate.version,
  });
}

export interface FixedBillPaymentReference {
  paymentId: string;
  billName: string;
  periodStart: string;
}

const referencePath = (ledgerId: string, transactionId: string) =>
  path(ledgerId) + '/payment-references/' + encodeURIComponent(transactionId);

export function findFixedBillPaymentReference(ledgerId: string, transactionId: string) {
  return request<FixedBillPaymentReference | undefined>(
    referencePath(ledgerId, transactionId),
    'GET',
  );
}

export function unlinkFixedBillPayment(ledgerId: string, transactionId: string, paymentId: string) {
  return request<void>(
    referencePath(ledgerId, transactionId) + '/' + encodeURIComponent(paymentId),
    'DELETE',
  );
}

export interface FixedBillPlan {
  billId: string;
  name: string;
  firstDueOn: string;
  earliestFirstDueOn: string;
  amountCents: number;
  categoryCode: string;
  accountId?: string | null;
}

interface FixedBillPlanPage {
  entries: FixedBillPlan[];
  totalCount: number;
  page: number;
  pageSize: number;
}

export function listFixedBillPlans(ledgerId: string, page: number) {
  return request<FixedBillPlanPage>(path(ledgerId) + '/plans', 'GET', { page });
}

export function reviseFixedBillPlan(ledgerId: string, billId: string, plan: BillPlan) {
  return request<FixedBillPeriod>(
    path(ledgerId) + '/plans/' + encodeURIComponent(billId),
    'PUT',
    plan,
  );
}

export function stopFixedBillPlan(ledgerId: string, billId: string, expectedStopOn: string) {
  return request<void>(path(ledgerId) + '/plans/' + encodeURIComponent(billId) + '/stop', 'PUT', {
    expectedStopOn,
  });
}
