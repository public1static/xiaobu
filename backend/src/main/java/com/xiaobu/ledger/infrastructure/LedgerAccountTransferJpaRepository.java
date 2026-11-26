package com.xiaobu.ledger.infrastructure;

import java.util.List;
import java.util.Optional;
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

  // 通过账本和主键限制访问范围。
  Optional<LedgerAccountTransferEntity> findByLedgerIdAndId(String ledgerId, String id);

  // 删除当前账本中已确认撤销的转账。
  void deleteByLedgerIdAndId(String ledgerId, String id);
}
