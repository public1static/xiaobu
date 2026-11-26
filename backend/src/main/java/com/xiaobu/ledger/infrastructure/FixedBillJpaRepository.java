package com.xiaobu.ledger.infrastructure;

import java.time.LocalDate;
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

  interface PeriodRow {
    FixedBillEntity getBill();

    FixedBillPaymentEntity getPayment();
  }
}
