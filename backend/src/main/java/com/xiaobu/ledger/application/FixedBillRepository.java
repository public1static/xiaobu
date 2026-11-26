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

  Optional<FixedBillPayment> findPayment(String billId, YearMonth month);

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

  FixedBillPageResult listPeriods(String ledgerId, YearMonth month, boolean paid, int page);
}
