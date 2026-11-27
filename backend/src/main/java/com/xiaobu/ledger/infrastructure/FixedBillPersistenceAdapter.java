package com.xiaobu.ledger.infrastructure;

import com.xiaobu.ledger.application.FixedBillPageResult;
import com.xiaobu.ledger.application.FixedBillPeriodResult;
import com.xiaobu.ledger.application.FixedBillRepository;
import com.xiaobu.ledger.domain.FixedBill;
import com.xiaobu.ledger.domain.FixedBillPayment;
import java.time.YearMonth;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
class FixedBillPersistenceAdapter implements FixedBillRepository {
  private static final int PAGE_SIZE = 20;
  private final FixedBillJpaRepository bills;
  private final FixedBillPaymentJpaRepository payments;

  FixedBillPersistenceAdapter(
      FixedBillJpaRepository bills, FixedBillPaymentJpaRepository payments) {
    this.bills = bills;
    this.payments = payments;
  }

  @Override
  public void addBill(FixedBill bill, String actorId) {
    bills.save(new FixedBillEntity(bill, actorId));
  }

  @Override
  public Optional<FixedBill> findBill(String ledgerId, String billId) {
    return bills
        .findByLedgerIdAndIdAndDeletedFalse(ledgerId, billId)
        .map(FixedBillEntity::toDomain);
  }

  @Override
  public Optional<FixedBillPayment> findPayment(String billId, YearMonth month) {
    return payments
        .findByBillIdAndPeriod(billId, month.atDay(1))
        .map(FixedBillPaymentEntity::toDomain);
  }

  @Override
  public void addPayment(FixedBillPayment payment, String actorId) {
    payments.save(new FixedBillPaymentEntity(payment, actorId));
  }

  @Override
  public FixedBillPageResult listPeriods(String ledgerId, YearMonth month, boolean paid, int page) {
    var rows =
        bills.listPeriods(
            ledgerId, month.atDay(1), month.atEndOfMonth(), paid, PageRequest.of(page, PAGE_SIZE));
    return new FixedBillPageResult(
        rows.map(
                row -> {
                  var payment = row.getPayment();
                  return FixedBillPeriodResult.from(
                      row.getBill().toDomain(), month, payment == null ? null : payment.toDomain());
                })
            .getContent(),
        rows.getTotalElements(),
        page,
        PAGE_SIZE);
  }
}
