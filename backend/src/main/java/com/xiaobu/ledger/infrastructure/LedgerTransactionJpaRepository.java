package com.xiaobu.ledger.infrastructure;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

interface LedgerTransactionJpaRepository extends Repository<LedgerTransactionEntity, String> {

  @Query(
      """
      select t from LedgerTransactionEntity t
      where t.ledgerId = :ledgerId
          and t.createdBy = :creatorId
          and t.direction = :direction
          and t.deleted = false
          and t.bookedOn between :startInclusive and :endInclusive
      order by t.bookedOn desc, t.id desc
      """)
  List<LedgerTransactionEntity> findActiveByCreatorAndDateRange(
      @Param("ledgerId") String ledgerId,
      @Param("creatorId") String creatorId,
      @Param("direction") TransactionDirection direction,
      @Param("startInclusive") LocalDate startInclusive,
      @Param("endInclusive") LocalDate endInclusive);
}
