package com.xiaobu.ledger.infrastructure;

import com.xiaobu.ledger.application.FixedBillCandidatePageResult;
import com.xiaobu.ledger.application.FixedBillPageResult;
import com.xiaobu.ledger.application.FixedBillPaymentReferenceResult;
import com.xiaobu.ledger.application.FixedBillPeriodResult;
import com.xiaobu.ledger.application.FixedBillRepository;
import com.xiaobu.ledger.domain.FixedBill;
import com.xiaobu.ledger.domain.FixedBillPayment;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
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
        .findByBillIdAndPeriodAndDeletedFalse(billId, month.atDay(1))
        .map(FixedBillPaymentEntity::toDomain);
  }

  @Override
  public Optional<FixedBillPaymentReferenceResult> findPaymentReference(
      String ledgerId, String transactionId) {
    return payments.findPaymentReference(ledgerId, transactionId);
  }

  @Override
  public void unlinkPayment(String paymentId, String actorId) {
    payments.findByIdAndDeletedFalse(paymentId).orElseThrow().markUnlinked(actorId);
  }

  @Override
  public boolean hasPaymentForTransaction(String transactionId) {
    return payments.existsByTransactionIdAndDeletedFalse(transactionId);
  }

  @Override
  public FixedBillCandidatePageResult listCandidates(
      FixedBill bill,
      YearMonth month,
      LocalDate today,
      String keyword,
      Long amountCents,
      List<String> categoryCodes,
      int page) {
    var rows =
        bills.listCandidates(
            bill.ledgerId(),
            today,
            keyword,
            amountCents,
            categoryCodes,
            month.atDay(1),
            month.atEndOfMonth(),
            bill.categoryCode(),
            PageRequest.of(page, PAGE_SIZE));
    return new FixedBillCandidatePageResult(
        rows.getContent(), rows.getTotalElements(), page, PAGE_SIZE);
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
