package com.xiaobu.ledger.infrastructure;

import com.xiaobu.ledger.application.FixedBillCandidateResult;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

interface FixedBillJpaRepository extends Repository<FixedBillEntity, String> {
  FixedBillEntity save(FixedBillEntity bill);

  Optional<FixedBillEntity> findByLedgerIdAndIdAndDeletedFalse(String ledgerId, String id);

  String PERIODS =
      """
      from FixedBillEntity b left join FixedBillPaymentEntity p
          on p.billId = b.id and p.period = :period
      where b.ledgerId = :ledgerId and b.deleted = false and b.firstDueOn <= :monthEnd
          and ((:paid = true and p.id is not null) or (:paid = false and p.id is null))
      """;

  @Query(
      value = "select b as bill, p as payment " + PERIODS + " order by day(b.firstDueOn), b.id",
      countQuery = "select count(b.id) " + PERIODS)
  Page<PeriodRow> listPeriods(
      String ledgerId, LocalDate period, LocalDate monthEnd, boolean paid, Pageable pageable);

  String CANDIDATES =
      """
      from LedgerTransactionEntity t
      left join LedgerAccountEntity a on a.ledgerId = t.ledgerId and a.id = t.accountId
      where t.ledgerId = :ledgerId and t.deleted = false
          and t.direction = com.xiaobu.ledger.domain.TransactionDirection.EXPENSE
          and t.bookedOn <= :today
          and not exists (select p.id from FixedBillPaymentEntity p where p.transactionId = t.id)
          and (:keyword = '' or locate(:keyword, lower(t.note)) > 0
              or locate(:keyword, lower(a.name)) > 0
              or locate(:keyword, cast(t.bookedOn as string)) > 0
              or t.amountCents = :amountCents or t.categoryCode in :categoryCodes)
      """;

  @Query(
      value =
          """
          select new com.xiaobu.ledger.application.FixedBillCandidateResult(t.id, t.version,
              t.bookedOn, t.amountCents, t.note, t.categoryCode, a.name)
          """
              + CANDIDATES
              + """
              order by (case when t.bookedOn between :monthStart and :monthEnd then 2 else 0 end
                  + case when t.categoryCode = :preferredCategory then 1 else 0 end) desc,
                  t.bookedOn desc, t.createdAt desc, t.id desc
              """,
      countQuery = "select count(t.id) " + CANDIDATES)
  Page<FixedBillCandidateResult> listCandidates(
      String ledgerId,
      LocalDate today,
      String keyword,
      Long amountCents,
      List<String> categoryCodes,
      LocalDate monthStart,
      LocalDate monthEnd,
      String preferredCategory,
      Pageable pageable);

  interface PeriodRow {
    FixedBillEntity getBill();

    FixedBillPaymentEntity getPayment();
  }
}
