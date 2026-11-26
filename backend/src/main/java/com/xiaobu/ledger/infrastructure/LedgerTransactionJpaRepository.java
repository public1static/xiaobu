package com.xiaobu.ledger.infrastructure;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

interface LedgerTransactionJpaRepository extends Repository<LedgerTransactionEntity, String> {

  <S extends LedgerTransactionEntity> S save(S entity);

  Optional<LedgerTransactionEntity> findByLedgerIdAndIdAndDeletedFalse(String ledgerId, String id);

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

  Optional<LedgerTransactionEntity> findByLedgerIdAndId(String ledgerId, String id);

  @Query(
      """
      select t from LedgerTransactionEntity t
      where t.ledgerId = :ledgerId and t.deleted = true
          and (:creator is null or t.createdBy = :creator)
      order by t.updatedAt desc, t.id desc
      """)
  List<LedgerTransactionEntity> findRecentlyDeleted(
      @Param("ledgerId") String ledgerId, @Param("creator") String creatorFilter, Pageable page);
}
