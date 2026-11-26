package com.xiaobu.ledger.infrastructure;

import com.xiaobu.ledger.application.LedgerStatementDayResult;
import com.xiaobu.ledger.application.LedgerStatementGroupResult;
import com.xiaobu.ledger.application.LedgerStatementQueryRepository;
import com.xiaobu.ledger.application.LedgerTransactionResult;
import com.xiaobu.ledger.application.ListLedgerTransactionsQuery;
import com.xiaobu.ledger.domain.TransactionDirection;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
class LedgerStatementQueryPersistenceAdapter implements LedgerStatementQueryRepository {
  private final LedgerStatementJpaRepository statements;

  LedgerStatementQueryPersistenceAdapter(LedgerStatementJpaRepository statements) {
    this.statements = statements;
  }

  @Override
  public List<LedgerTransactionResult> findPage(ListLedgerTransactionsQuery query) {
    return statements
        .findPage(
            query.ledgerId(),
            query.from(),
            query.through(),
            query.keyword(),
            query.filter().categoryCode(),
            query.filter().accountId(),
            query.filter().unassignedAccount(),
            query.filter().direction() == null ? null : query.filter().direction().name(),
            query.filter().recordedByMe() ? query.actorId() : query.filter().recorderId(),
            query.pageSize(),
            query.page() * query.pageSize())
        .stream()
        .map(row ->
            new LedgerTransactionResult(
                row.getId(),
                TransactionDirection.valueOf(row.getDirection()),
                row.getAmountCents().longValue(),
                row.getBookedOn(),
                row.getCategoryCode(),
                row.getNote(),
                row.getAccountId(),
                row.getOriginalTransactionId()))
        .toList();
  }

  @Override
  public List<LedgerStatementDayResult> summarizeByDay(ListLedgerTransactionsQuery query) {
    return statements
        .summarizeByDay(
            query.ledgerId(),
            query.from(),
            query.through(),
            query.keyword(),
            query.filter().categoryCode(),
            query.filter().accountId(),
            query.filter().unassignedAccount(),
            query.filter().direction() == null ? null : query.filter().direction().name(),
            query.filter().recordedByMe() ? query.actorId() : query.filter().recorderId())
        .stream()
        .map(row ->
            new LedgerStatementDayResult(
                row.getBookedOn(),
                row.getIncomeCents().longValue(),
                row.getExpenseCents().longValue(),
                row.getEntryCount().longValue()))
        .toList();
  }

  // 将数据库投影转换为统一的整数分结果。
  @Override
  public List<LedgerStatementGroupResult> summarizeByGroup(ListLedgerTransactionsQuery query) {
    return statements
        .summarizeByGroup(
            query.ledgerId(),
            query.from(),
            query.through(),
            query.keyword(),
            query.filter().categoryCode(),
            query.filter().accountId(),
            query.filter().unassignedAccount(),
            query.filter().direction() == null ? null : query.filter().direction().name(),
            query.filter().recordedByMe() ? query.actorId() : query.filter().recorderId(),
            query.groupBy().name())
        .stream()
        .map(row ->
            new LedgerStatementGroupResult(
                row.getGroupKey(),
                row.getEntryCount(),
                row.getIncomeCents(),
                row.getGrossExpenseCents(),
                row.getRefundCents(),
                row.getNetExpenseCents()))
        .toList();
  }
}
