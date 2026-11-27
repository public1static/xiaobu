package com.xiaobu.ledger.application;

import com.aegisid.foundation.shared.error.ErrorCategory;
import com.aegisid.foundation.shared.error.ErrorCode;
import com.aegisid.foundation.shared.error.FoundationException;
import com.xiaobu.ledger.domain.FixedBill;
import com.xiaobu.ledger.domain.FixedBillPayment;
import com.xiaobu.ledger.domain.LedgerCategoryDefaults;
import com.xiaobu.ledger.domain.TransactionDirection;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local-expense | wechat-preview")
class RentalPaymentExtensionHandler implements TransactionExtensionHandler<RentalPaymentDraft> {
  private static final ZoneId LEDGER_ZONE = ZoneId.of("Asia/Shanghai");
  private static final String TYPE = "RENTAL_PAYMENT";
  private final FixedBillRepository bills;
  private final FixedBillPaymentLifecycle lifecycle;

  RentalPaymentExtensionHandler(FixedBillRepository bills, FixedBillPaymentLifecycle lifecycle) {
    this.bills = bills;
    this.lifecycle = lifecycle;
  }

  @Override
  public String type() {
    return TYPE;
  }

  @Override
  public Class<RentalPaymentDraft> draftType() {
    return RentalPaymentDraft.class;
  }

  @Override
  public Optional<TransactionExtensionCapability> capability(
      TransactionDirection direction, String categoryCode) {
    return direction == TransactionDirection.EXPENSE
            && LedgerCategoryDefaults.HOUSING.name().equals(categoryCode)
        ? Optional.of(new TransactionExtensionCapability(TYPE, false))
        : Optional.empty();
  }

  @Override
  public Optional<RentalPaymentDetail> read(TransactionExtensionContext context) {
    if (capability(context.direction(), context.categoryCode()).isEmpty()) return Optional.empty();
    return readRentalPayment(context);
  }

  @Override
  public Optional<RentalPaymentDetail> readForMutation(TransactionExtensionContext context) {
    if (!LedgerCategoryDefaults.HOUSING.name().equals(context.originalCategoryCode()))
      return Optional.empty();
    return readRentalPayment(context);
  }

  private Optional<RentalPaymentDetail> readRentalPayment(TransactionExtensionContext context) {
    return currentPayment(context)
        .flatMap(payment ->
            findRentalBill(context.ledgerId(), payment.billId())
                .map(bill ->
                    new RentalPaymentDetail(
                        bill.id(), YearMonth.from(payment.period()), bill.name())));
  }

  @Override
  public void upsert(TransactionExtensionContext context, RentalPaymentDraft draft) {
    if (draft.billId() == null
        || draft.billId().isBlank()
        || draft.billId().length() > 48
        || draft.month() == null
        || draft.month().getYear() < 1900
        || draft.month().getYear() > 2100) {
      throw conflict("请选择有效的房租账期");
    }
    requireValidDate(context);
    var current = currentPayment(context).orElse(null);
    if (current != null && findRentalBill(context.ledgerId(), current.billId()).isEmpty()) {
      throw conflict("这笔流水已关联其他固定账单");
    }
    if (current != null
        && current.billId().equals(draft.billId())
        && current.period().equals(draft.month().atDay(1))) {
      refreshActualPayment(context, current);
      return;
    }
    var bill =
        findRentalBill(context.ledgerId(), draft.billId())
            .filter(row -> row.covers(draft.month()))
            .orElseThrow(() -> conflict("该房租账期已不可用"));
    if (bills.findPayment(bill.id(), draft.month()).isPresent()) {
      throw conflict("该房租账期已付，请重新选择");
    }
    if (current != null) bills.unlinkPayment(current.id(), context.actorId());
    lifecycle.recordPayment(
        bill.id(),
        draft.month(),
        context.transactionId(),
        context.bookedOn(),
        context.amountCents(),
        context.actorId());
  }

  @Override
  public void retain(TransactionExtensionContext context, TransactionExtensionData detail) {
    requireValidDate(context);
    currentPayment(context).ifPresent(payment -> refreshActualPayment(context, payment));
  }

  @Override
  public void remove(TransactionExtensionContext context) {
    currentPayment(context)
        .filter(payment -> findRentalBill(context.ledgerId(), payment.billId()).isPresent())
        .ifPresent(payment -> bills.unlinkPayment(payment.id(), context.actorId()));
  }

  private Optional<FixedBillPayment> currentPayment(TransactionExtensionContext context) {
    return bills.findActivePaymentForTransaction(context.ledgerId(), context.transactionId());
  }

  private Optional<FixedBill> findRentalBill(String ledgerId, String billId) {
    return bills.findBill(ledgerId, billId).filter(bill -> bill.lastDueOn() != null);
  }

  private void refreshActualPayment(TransactionExtensionContext context, FixedBillPayment payment) {
    if (!payment.paidOn().equals(context.bookedOn())
        || payment.amountCents() != context.amountCents()) {
      bills.updatePayment(
          payment.id(), context.bookedOn(), context.amountCents(), context.actorId());
    }
  }

  private static void requireValidDate(TransactionExtensionContext context) {
    if (context.bookedOn().isAfter(LocalDate.now(LEDGER_ZONE))) {
      throw new FoundationException(new ErrorCode("XIAOBU_RENTAL_PAYMENT_DATE"), "付款日期不能晚于今天");
    }
  }

  private static FoundationException conflict(String message) {
    return new FoundationException(
        new ErrorCode("XIAOBU_RENTAL_PAYMENT_CONFLICT"), ErrorCategory.CONFLICT, message, Map.of());
  }
}
