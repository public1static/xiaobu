package com.xiaobu.ledger.infrastructure;

import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

interface LedgerAccountTransferJpaRepository
    extends Repository<LedgerAccountTransferEntity, String> {

  <S extends LedgerAccountTransferEntity> S save(S entity);

  List<LedgerAccountTransferEntity> findTop50ByLedgerIdOrderByBookedOnDescIdDesc(String ledgerId);

  @Query(
      """
      select t.sourceAccountId as accountId, sum(t.amountCents) as balanceCents
      from LedgerAccountTransferEntity t where t.ledgerId = :ledgerId group by t.sourceAccountId
      """)
  List<LedgerAccountBalanceRow> findOutgoingTotals(@Param("ledgerId") String ledgerId);

  @Query(
      """
      select t.targetAccountId as accountId, sum(t.amountCents) as balanceCents
      from LedgerAccountTransferEntity t where t.ledgerId = :ledgerId group by t.targetAccountId
      """)
  List<LedgerAccountBalanceRow> findIncomingTotals(@Param("ledgerId") String ledgerId);
}
