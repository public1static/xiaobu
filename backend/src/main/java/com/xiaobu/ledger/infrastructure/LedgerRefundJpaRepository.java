package com.xiaobu.ledger.infrastructure;

import com.xiaobu.ledger.application.LedgerRefundActivity;
import java.time.LocalDate;
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
      select new com.xiaobu.ledger.application.LedgerRefundActivity(
        r.id, r.transactionId, r.amountCents, r.receivedOn,
        original.categoryCode, original.note, r.accountId)
      from LedgerRefundEntity r join r.original original
      where r.ledgerId = :ledgerId and original.deleted = false
        and r.receivedOn between :dayFrom and :dayThrough
        and (:keyword = '' or locate(lower(:keyword), lower(original.note)) > 0
            or locate(lower(:keyword), '退款') > 0)
      order by r.receivedOn desc, r.createdAt desc, r.id desc
      """)
  List<LedgerRefundActivity> findActiveByDateRange(
      @Param("ledgerId") String ledgerId,
      @Param("dayFrom") LocalDate from,
      @Param("dayThrough") LocalDate through,
      @Param("keyword") String keyword);

  @Query(
      """
      select r.accountId as accountId, sum(r.amountCents) as balanceCents
      from LedgerRefundEntity r join r.original original
      where r.ledgerId = :ledgerId and original.deleted = false and r.accountId is not null
      group by r.accountId
      """)
  List<LedgerAccountBalanceRow> findAccountCredits(@Param("ledgerId") String ledgerId);
}
