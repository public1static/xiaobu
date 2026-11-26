package com.xiaobu.ledger.application;

import com.xiaobu.ledger.domain.FixedBill;
import com.xiaobu.ledger.domain.FixedBillPayment;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

public interface FixedBillRepository {
  void addBill(FixedBill bill, String actorId);

  Optional<FixedBill> findBill(String ledgerId, String billId);

  void adoptForRental(FixedBill bill, String actorId);

  RentalPageResult<RentalBillPlanResult> listRentalPlanCandidates(
      String ledgerId, int cycleMonths, LocalDate startsOn, LocalDate endsOn, int page);

  void closePlan(String ledgerId, String billId, LocalDate stopMonth, String actorId);

  Optional<LocalDate> findLatestProtectedMonth(String billId);

  FixedBillPlanPageResult listPlans(String ledgerId, YearMonth currentMonth, int page);

  Optional<FixedBillPayment> findPayment(String billId, YearMonth month);

  Optional<FixedBillPaymentReferenceResult> findPaymentReference(
      String ledgerId, String transactionId);

  Optional<FixedBillPayment> findActivePaymentForTransaction(String ledgerId, String transactionId);

  Optional<FixedBillPayment> findSuspendedPaymentForTransaction(
      String ledgerId, String transactionId);

  void suspendPayment(String paymentId, String actorId);

  void finishSourceRestore(String paymentId, String actorId);

  void unlinkPayment(String paymentId, String actorId);

  boolean hasPaymentForTransaction(String transactionId);

  FixedBillCandidatePageResult listCandidates(
      FixedBill bill,
      YearMonth month,
      LocalDate today,
      String keyword,
      Long amountCents,
      List<String> categoryCodes,
      int page);

  void addPayment(FixedBillPayment payment, String actorId);

  FixedBillPageResult listPeriods(
      String ledgerId, YearMonth month, boolean paid, int page, String billId);
}
