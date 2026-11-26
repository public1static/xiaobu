import { request } from './request';

export interface FixedBillPeriod {
  billId: string;
  name: string;
  dueOn: string;
  amountCents: number;
  categoryCode: string;
  accountId?: string | null;
  transactionId?: string | null;
  paidOn?: string | null;
  paidAmountCents?: number | null;
}

interface FixedBillPage {
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

export function listFixedBillPeriods(ledgerId: string, month: string, paid: boolean, page: number) {
  return request<FixedBillPage>(path(ledgerId), 'GET', { month, paid, page });
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
