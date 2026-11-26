package com.xiaobu.ledger.infrastructure;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

interface LedgerTransactionJpaRepository extends Repository<LedgerTransactionEntity, String> {

  <S extends LedgerTransactionEntity> S save(S entity);

  @Query(
      """
      select t from LedgerTransactionEntity t
      where t.ledgerId = :ledgerId
          and t.deleted = false
          and t.bookedOn between :startInclusive and :endInclusive
      order by t.bookedOn desc, t.createdAt desc, t.id desc
      """)
  List<LedgerTransactionEntity> findActiveByLedgerAndDateRange(
      @Param("ledgerId") String ledgerId,
      @Param("startInclusive") LocalDate startInclusive,
      @Param("endInclusive") LocalDate endInclusive);

  @Query(
      """
      select t.accountId as accountId,
          sum(case when t.direction = com.xiaobu.ledger.domain.TransactionDirection.INCOME
              then t.amountCents else -t.amountCents end) as balanceCents
      from LedgerTransactionEntity t
      where t.ledgerId = :ledgerId and t.deleted = false and t.accountId is not null
      group by t.accountId
      """)
  List<LedgerAccountBalanceRow> findAccountBalancesByLedger(@Param("ledgerId") String ledgerId);
}
