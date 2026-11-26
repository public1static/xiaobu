import { localDay } from './transaction';
import type {
  LedgerStatementDay,
  LedgerStatementPage,
  LedgerTransactionDetail,
  LedgerTransactionFilter,
} from '../../api/ledger-transactions';
import type { TransactionDirection } from './transaction';

// 示例沿用原型中的七笔虚构收支，日期随设备日历移动。
export function exampleDetails(today: string): LedgerTransactionDetail[] {
  function row(
    id: number,
    offset: number,
    direction: TransactionDirection,
    categoryCode: string,
    amountCents: number,
    note: string,
    accountName: string,
    gift?: Record<string, string>,
  ): LedgerTransactionDetail {
    const day = new Date(today + 'T12:00:00');
    day.setDate(day.getDate() + offset);
    const bookedOn = localDay(day);
    return {
      transaction: { id: 'sample-' + id, bookedOn, direction, categoryCode, amountCents, note },
      authorName: '示例成员',
      accountName,
      canModify: false,
      version: 0,
      refunds: [],
      extensions: gift ? [{ type: 'GIFT', detail: gift }] : [],
    };
  }
  return [
    row(1, 0, 'EXPENSE', 'DINING', 3260, '午饭 · 牛肉面', '微信'),
    row(2, 0, 'EXPENSE', 'TRANSIT', 650, '地铁通勤', '支付宝'),
    row(3, -1, 'EXPENSE', 'GROCERIES', 18640, '周末采购', '微信'),
    row(4, -1, 'EXPENSE', 'GIFT_SENT', 50000, '新婚贺礼', '微信', {
      bookName: '日常送礼',
      personName: '林小满',
      distinction: '朋友',
      reason: '婚礼',
    }),
    row(5, -1, 'INCOME', 'SALARY', 1260000, '本月工资到账', '银行卡'),
    row(6, -2, 'EXPENSE', 'HOUSING', 238000, '房租', '银行卡'),
    row(7, -2, 'INCOME', 'GIFT_RECEIVED', 30000, '', '微信', {
      bookName: '日常收礼',
      personName: '周宁',
      distinction: '亲友',
      reason: '乔迁',
    }),
  ];
}

// 日期、搜索和汇总都只读取内存示例。
export function exampleStatement(
  details: LedgerTransactionDetail[],
  month: string,
  filter: LedgerTransactionFilter,
): LedgerStatementPage {
  const entries = details
    .map((row) => row.transaction)
    .filter((row) => {
      const within =
        filter.from && filter.through
          ? row.bookedOn >= filter.from && row.bookedOn <= filter.through
          : row.bookedOn.startsWith(month);
      return within && row.note.includes(filter.keyword);
    });
  const days = new Map<string, LedgerStatementDay>();
  let incomeCents = 0;
  let expenseCents = 0;
  for (const row of entries) {
    const day = days.get(row.bookedOn) ?? {
      bookedOn: row.bookedOn,
      incomeCents: 0,
      expenseCents: 0,
      entryCount: 0,
    };
    if (row.direction === 'INCOME') {
      incomeCents += row.amountCents;
      day.incomeCents += row.amountCents;
    } else {
      expenseCents += row.amountCents;
      day.expenseCents += row.amountCents;
    }
    day.entryCount++;
    days.set(row.bookedOn, day);
  }
  return {
    entries,
    days: [...days.values()],
    incomeCents,
    expenseCents,
    balanceCents: incomeCents - expenseCents,
    totalCount: entries.length,
    page: 0,
    pageSize: details.length,
  };
}
