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

  // A ledger-wide paid view preserves original payments even outside an adopted rental window.
  String PERIODS =
      """
      from FixedBillEntity b left join FixedBillPaymentEntity p
          on p.billId = b.id and p.period = :period and p.deleted = false
      where b.ledgerId = :ledgerId and b.deleted = false and b.firstDueOn <= :monthEnd
          and (:billId is null or b.id = :billId)
          and (:rentalOnly = false or b.lastDueOn is not null)
          and (b.stopMonth is null or b.stopMonth > :period)
          and mod((year(:period) - year(b.firstDueOn)) * 12 + month(:period) - month(b.firstDueOn), b.cycleMonths) = 0
          and ((:billId is null and p.id is not null) or (
              (b.lastDueOn is null or b.lastDueOn >= :monthEnd
                  or (b.lastDueOn >= :period and day(b.lastDueOn) >= day(b.firstDueOn)))
              and (b.rentalStartsOn is null or b.rentalStartsOn <= :period
                  or (b.rentalStartsOn <= :monthEnd
                      and day(b.rentalStartsOn) <= least(day(b.firstDueOn), day(:monthEnd))))))
          and ((:paid = true and p.id is not null) or (:paid = false and p.id is null))
      """;

  @Query(
      value =
          "select b as bill, p as payment "
              + PERIODS
              + " order by least(day(b.firstDueOn), day(:monthEnd)), b.id",
      countQuery = "select count(b.id) " + PERIODS)
  Page<PeriodRow> listPeriods(
      String ledgerId,
      String billId,
      LocalDate period,
      LocalDate monthEnd,
      boolean paid,
      boolean rentalOnly,
      Pageable pageable);

  String OPEN_PLANS =
      """
      from FixedBillEntity b
      where b.ledgerId = :ledgerId and b.deleted = false and b.stopMonth is null and b.lastDueOn is null
      """;

  @Query(
      value =
          """
          select b as bill, (select max(p.period) from FixedBillPaymentEntity p
              where p.billId = b.id and (p.deleted = false or p.awaitingSourceRestore = true)) as latestProtected
          """
              + OPEN_PLANS
              + " order by b.id",
      countQuery = "select count(b.id) " + OPEN_PLANS)
  Page<PlanRow> listPlans(String ledgerId, Pageable pageable);

  // Anchor offsets describe the first and last admissible months after day clamping.
  String ANCHOR = "(year(b.firstDueOn) * 12 + month(b.firstDueOn))";
  String FIRST_OFFSET =
      "(:startMonth - "
          + ANCHOR
          + " + case when least(day(b.firstDueOn), :startMonthDays) < :startDay then 1 else 0 end)";
  String LAST_OFFSET =
      "(:endMonth - "
          + ANCHOR
          + " - case when least(day(b.firstDueOn), :endMonthDays) > :endDay then 1 else 0 end)";
  String RENTAL_PLANS =
      OPEN_PLANS
          + " and b.categoryCode = :housingCategory and b.cycleMonths = :cycleMonths"
          + " and not exists (select c.id from RentalContractEntity c where c.billId = b.id)"
          + " and "
          + LAST_OFFSET
          + " >= 0"
          + " and "
          + FIRST_OFFSET
          + " <= "
          + LAST_OFFSET
          + " - mod("
          + LAST_OFFSET
          + ", b.cycleMonths)";

  @Query(
      value = "select b " + RENTAL_PLANS + " order by b.name, b.id",
      countQuery = "select count(b.id) " + RENTAL_PLANS)
  Page<FixedBillEntity> listRentalPlanCandidates(
      String ledgerId,
      String housingCategory,
      int cycleMonths,
      int startMonth,
      int startMonthDays,
      int startDay,
      int endMonth,
      int endMonthDays,
      int endDay,
      Pageable pageable);

  interface PlanRow {
    FixedBillEntity getBill();

    LocalDate getLatestProtected();
  }

  String CANDIDATES =
      """
      from LedgerTransactionEntity t
      left join LedgerAccountEntity a on a.ledgerId = t.ledgerId and a.id = t.accountId
      where t.ledgerId = :ledgerId and t.deleted = false
          and t.direction = com.xiaobu.ledger.domain.TransactionDirection.EXPENSE
          and t.bookedOn <= :today
          and not exists (select p.id from FixedBillPaymentEntity p where p.transactionId = t.id and p.deleted = false)
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
