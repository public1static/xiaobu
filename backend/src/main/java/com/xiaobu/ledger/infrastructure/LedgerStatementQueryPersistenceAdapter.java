package com.xiaobu.ledger.infrastructure;

import com.xiaobu.ledger.application.LedgerStatementDayResult;
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
        .summarizeByDay(query.ledgerId(), query.from(), query.through(), query.keyword())
        .stream()
        .map(row ->
            new LedgerStatementDayResult(
                row.getBookedOn(),
                row.getIncomeCents().longValue(),
                row.getExpenseCents().longValue(),
                row.getEntryCount().longValue()))
        .toList();
  }
}
