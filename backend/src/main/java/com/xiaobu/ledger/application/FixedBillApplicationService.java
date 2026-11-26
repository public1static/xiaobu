package com.xiaobu.ledger.application;

import com.aegisid.foundation.data.id.EntityIdGenerator;
import com.aegisid.foundation.data.id.IdPrefix;
import com.aegisid.foundation.shared.error.ErrorCode;
import com.aegisid.foundation.shared.error.FoundationException;
import com.xiaobu.ledger.domain.FixedBill;
import com.xiaobu.ledger.domain.FixedBillPayment;
import com.xiaobu.ledger.domain.TransactionDirection;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("local-expense | wechat-preview")
public class FixedBillApplicationService {
  private static final IdPrefix BILL_PREFIX = new IdPrefix("bill");
  private static final IdPrefix PAYMENT_PREFIX = new IdPrefix("bpmt");
  private static final ZoneId LEDGER_ZONE = ZoneId.of("Asia/Shanghai");
  private final FixedBillRepository bills;
  private final PersonalLedgerApplicationService ledgers;
  private final LedgerAccountApplicationService accounts;
  private final LedgerCategoryApplicationService categories;
  private final LedgerTransactionApplicationService transactions;
  private final EntityIdGenerator ids;
  private final TransactionExtensionRegistry extensions;

  FixedBillApplicationService(
      FixedBillRepository bills,
      PersonalLedgerApplicationService ledgers,
      LedgerAccountApplicationService accounts,
      LedgerCategoryApplicationService categories,
      LedgerTransactionApplicationService transactions,
      EntityIdGenerator ids,
      TransactionExtensionRegistry extensions) {
    this.bills = bills;
    this.ledgers = ledgers;
    this.accounts = accounts;
    this.categories = categories;
    this.transactions = transactions;
    this.ids = ids;
    this.extensions = extensions;
  }

  @Transactional
  public FixedBillPeriodResult createFixedBill(CreateFixedBillCommand command) {
    ledgers.requireWriteAccess(command.ledgerId(), command.actorId());
    accounts.requireAccountInLedger(command.ledgerId(), command.accountId());
    categories.requireActiveCategory(
        command.ledgerId(), TransactionDirection.EXPENSE, command.categoryCode());
    if (extensions.capabilities(TransactionDirection.EXPENSE, command.categoryCode()).stream()
        .anyMatch(TransactionExtensionCapability::required)) {
      throw invalid("当前分类需要专用业务明细，不能用于固定账单");
    }
    var bill =
        new FixedBill(
            ids.next(BILL_PREFIX),
            command.ledgerId(),
            command.name(),
            command.firstDueOn(),
            command.amountCents(),
            command.categoryCode(),
            command.accountId());
    bills.addBill(bill, command.actorId());
    return FixedBillPeriodResult.from(bill, YearMonth.from(bill.firstDueOn()), null);
  }

  @Transactional(readOnly = true)
  public FixedBillPageResult listFixedBillPeriods(
      String ledgerId, String actorId, YearMonth month, boolean paid, int page) {
    ledgers.requireReadAccess(ledgerId, actorId);
    requirePeriod(month);
    return bills.listPeriods(ledgerId, month, paid, page);
  }

  @Transactional
  public FixedBillPeriodResult payFixedBill(PayFixedBillCommand command) {
    // 和记账、成员权限变更共用账本锁，同时串行化同一账期的确认。
    ledgers.requireWriteAccess(command.ledgerId(), command.actorId());
    requirePeriod(command.month());
    var bill =
        bills
            .findBill(command.ledgerId(), command.billId())
            .filter(row -> !row.firstDueOn().isAfter(command.month().atEndOfMonth()))
            .orElseThrow(() -> invalid("找不到该账期"));
    var existing = bills.findPayment(bill.id(), command.month());
    if (existing.isPresent())
      return FixedBillPeriodResult.from(bill, command.month(), existing.get());
    if (command.paidOn().isAfter(LocalDate.now(LEDGER_ZONE))) throw invalid("付款日期不能晚于今天");
    var transaction =
        transactions.recordLedgerTransaction(
            new RecordLedgerTransactionCommand(
                bill.ledgerId(),
                command.actorId(),
                TransactionDirection.EXPENSE,
                command.amountCents(),
                command.paidOn(),
                bill.categoryCode(),
                bill.name(),
                command.accountId()));
    var payment =
        new FixedBillPayment(
            ids.next(PAYMENT_PREFIX),
            bill.id(),
            command.month().atDay(1),
            transaction.id(),
            command.paidOn(),
            command.amountCents());
    bills.addPayment(payment, command.actorId());
    return FixedBillPeriodResult.from(bill, command.month(), payment);
  }

  private static void requirePeriod(YearMonth month) {
    if (month == null || month.getYear() < 1900 || month.getYear() > 9999) throw invalid("请选择有效账期");
  }

  private static FoundationException invalid(String message) {
    return new FoundationException(new ErrorCode("XIAOBU_FIXED_BILL_INVALID"), message);
  }
}
