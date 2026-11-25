import type { CategoryCode } from '../pages/ledger/expense';

export interface ExpenseDraft {
  amountCents: number;
  bookedOn: string;
  categoryCode: CategoryCode;
  note: string;
}

export type Expense = ExpenseDraft & { id: string };

class ExpenseRequestError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
  }
}

function request<T>(suffix: string, method: 'GET' | 'POST', data?: ExpenseDraft) {
  const base = import.meta.env.VITE_XIAOBU_API_BASE ?? '';
  return new Promise<T>((resolve, reject) => {
    // #ifndef H5
    if (!base) {
      reject(new ExpenseRequestError(0, '尚未配置联调服务'));
      return;
    }
    // #endif
    uni.request({
      url: `${base}/api/v1/expenses${suffix}`,
      method,
      data,
      header: { 'Content-Type': 'application/json' },
      success: ({ data: raw, statusCode }) => {
        const body = raw as { success?: boolean; data?: T } | undefined;
        if (statusCode >= 200 && statusCode < 300 && body?.success && body.data != null) {
          resolve(body.data);
        } else {
          const message =
            statusCode === 400
              ? '金额、日期或分类有误，请检查后重试。'
              : '暂时无法访问账本，请稍后再试。';
          reject(new ExpenseRequestError(statusCode, message));
        }
      },
      fail: () => reject(new ExpenseRequestError(0, '账本连接失败，请重试。')),
    });
  });
}

export function listExpenses(month: string) {
  return request<Expense[]>(`?month=${encodeURIComponent(month)}`, 'GET');
}

export function saveExpense(draft: ExpenseDraft) {
  return request<Expense>('', 'POST', draft);
}
