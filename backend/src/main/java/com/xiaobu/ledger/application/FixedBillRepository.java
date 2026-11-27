package com.xiaobu.ledger.application;

import com.xiaobu.ledger.domain.FixedBill;
import com.xiaobu.ledger.domain.FixedBillPayment;
import java.time.YearMonth;
import java.util.Optional;

public interface FixedBillRepository {
  void addBill(FixedBill bill, String actorId);

  Optional<FixedBill> findBill(String ledgerId, String billId);

  Optional<FixedBillPayment> findPayment(String billId, YearMonth month);

  void addPayment(FixedBillPayment payment, String actorId);

  FixedBillPageResult listPeriods(String ledgerId, YearMonth month, boolean paid, int page);
}
