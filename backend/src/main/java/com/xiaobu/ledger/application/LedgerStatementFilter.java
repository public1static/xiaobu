package com.xiaobu.ledger.application;

import com.aegisid.foundation.shared.error.ErrorCode;
import com.aegisid.foundation.shared.error.FoundationException;
import com.xiaobu.ledger.domain.TransactionDirection;

/**
 * 当前账本内的组合筛选；记账人筛选分别采用流水和退款的登记身份。
 *
 * @param recorderId 指定登记人的身份，与仅本人筛选互斥
 */
public record LedgerStatementFilter(
    String categoryCode,
    String accountId,
    TransactionDirection direction,
    boolean recordedByMe,
    String recorderId,
    boolean unassignedAccount) {
  // 互斥的筛选不应隐式覆盖用户已经指定的条件。
  public LedgerStatementFilter {
    if ((accountId != null && unassignedAccount) || (recordedByMe && recorderId != null)) {
      throw new FoundationException(
          new ErrorCode("XIAOBU_INVALID_STATEMENT_FILTER"), "同一字段只能指定一个筛选条件");
    }
  }

  // 旧调用方继续查询整个授权账本。
  public static final LedgerStatementFilter ALL =
      new LedgerStatementFilter(null, null, null, false, null, false);
}
