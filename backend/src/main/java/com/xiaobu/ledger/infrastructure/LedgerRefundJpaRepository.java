package com.xiaobu.ledger.infrastructure;

import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

interface LedgerRefundJpaRepository extends Repository<LedgerRefundEntity, String> {

  <S extends LedgerRefundEntity> S save(S entity);

  List<LedgerRefundEntity> findByLedgerIdAndTransactionIdOrderByReceivedOnDescIdDesc(
      String ledgerId, String transactionId);

  @Query(
      """
      select r.accountId as accountId, sum(r.amountCents) as balanceCents
      from LedgerRefundEntity r join r.original original
      where r.ledgerId = :ledgerId and original.deleted = false and r.accountId is not null
      group by r.accountId
      """)
  List<LedgerAccountBalanceRow> findAccountCredits(@Param("ledgerId") String ledgerId);
}
