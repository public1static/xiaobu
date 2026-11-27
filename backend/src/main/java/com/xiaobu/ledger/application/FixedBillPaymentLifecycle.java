package com.xiaobu.ledger.application;

import com.aegisid.foundation.data.id.EntityIdGenerator;
import com.aegisid.foundation.data.id.IdPrefix;
import com.xiaobu.ledger.domain.FixedBillPayment;
import java.time.LocalDate;
import java.time.YearMonth;
import org.springframework.stereotype.Component;

/** Changes a payment relationship inside the authorized, locked ledger transaction. */
@Component
class FixedBillPaymentLifecycle {
  private static final IdPrefix PAYMENT_PREFIX = new IdPrefix("bpmt");
  private final FixedBillRepository bills;
  private final EntityIdGenerator ids;

  FixedBillPaymentLifecycle(FixedBillRepository bills, EntityIdGenerator ids) {
    this.bills = bills;
    this.ids = ids;
  }

  FixedBillPayment recordPayment(
      String billId,
      YearMonth month,
      String transactionId,
      LocalDate paidOn,
      long amountCents,
      String actorId) {
    var payment =
        new FixedBillPayment(
            ids.next(PAYMENT_PREFIX), billId, month.atDay(1), transactionId, paidOn, amountCents);
    bills.addPayment(payment, actorId);
    return payment;
  }

  void suspendForDeletedSource(String ledgerId, String transactionId, String actorId) {
    bills
        .findActivePaymentForTransaction(ledgerId, transactionId)
        .ifPresent(payment -> bills.suspendPayment(payment.id(), actorId));
  }

  void reconcileRestoredSource(String ledgerId, String transactionId, String actorId) {
    var suspended = bills.findSuspendedPaymentForTransaction(ledgerId, transactionId);
    if (suspended.isEmpty()) return;
    var payment = suspended.get();
    var month = YearMonth.from(payment.period());
    if (bills.findPayment(payment.billId(), month).isEmpty()) {
      // New relationship ID avoids applying old unlink confirmations to a restored payment.
      recordPayment(
          payment.billId(), month, transactionId, payment.paidOn(), payment.amountCents(), actorId);
    }
    bills.finishSourceRestore(payment.id(), actorId);
  }
}
