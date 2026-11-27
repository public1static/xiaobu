package com.xiaobu.ledger.application;

import com.aegisid.foundation.data.id.EntityIdGenerator;
import com.aegisid.foundation.data.id.IdPrefix;
import com.aegisid.foundation.shared.error.ErrorCategory;
import com.aegisid.foundation.shared.error.ErrorCode;
import com.aegisid.foundation.shared.error.FoundationException;
import com.xiaobu.ledger.domain.FixedBill;
import com.xiaobu.ledger.domain.FixedBillPayment;
import com.xiaobu.ledger.domain.TransactionDirection;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
  private final LedgerTransactionRepository transactionRepository;
  private final EntityIdGenerator ids;
  private final TransactionExtensionRegistry extensions;

  FixedBillApplicationService(
      FixedBillRepository bills,
      PersonalLedgerApplicationService ledgers,
      LedgerAccountApplicationService accounts,
      LedgerCategoryApplicationService categories,
      LedgerTransactionApplicationService transactions,
      LedgerTransactionRepository transactionRepository,
      EntityIdGenerator ids,
      TransactionExtensionRegistry extensions) {
    this.bills = bills;
    this.ledgers = ledgers;
    this.accounts = accounts;
    this.categories = categories;
    this.transactions = transactions;
    this.transactionRepository = transactionRepository;
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
    var bill = requireBillPeriod(command.ledgerId(), command.billId(), command.month());
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
    return recordPayment(
        bill,
        command.month(),
        transaction.id(),
        command.paidOn(),
        command.amountCents(),
        command.actorId());
  }

  @Transactional(readOnly = true)
  public FixedBillCandidatePageResult listFixedBillCandidates(ListFixedBillCandidatesQuery query) {
    ledgers.requireReadAccess(query.ledgerId(), query.actorId());
    var bill = requireBillPeriod(query.ledgerId(), query.billId(), query.month());
    if (bills.findPayment(bill.id(), query.month()).isPresent()) throw conflict("本期账单已付");
    String term = query.keyword().strip().toLowerCase(Locale.ROOT);
    var matchingCategories =
        categories.listLedgerCategories(query.ledgerId(), query.actorId()).stream()
            .filter(category -> category.direction() == TransactionDirection.EXPENSE)
            .filter(category -> categoryLabel(category).toLowerCase(Locale.ROOT).contains(term))
            .map(LedgerCategoryResult::code)
            .toList();
    return bills.listCandidates(
        bill,
        query.month(),
        LocalDate.now(LEDGER_ZONE),
        term,
        toSearchCents(term),
        matchingCategories.isEmpty() ? List.of("") : matchingCategories,
        query.page());
  }

  @Transactional
  public FixedBillPeriodResult linkFixedBillPayment(LinkFixedBillPaymentCommand command) {
    ledgers.requireWriteAccess(command.ledgerId(), command.actorId());
    var bill = requireBillPeriod(command.ledgerId(), command.billId(), command.month());
    var existing = bills.findPayment(bill.id(), command.month());
    if (existing.isPresent()) {
      if (existing.get().transactionId().equals(command.transactionId()))
        return FixedBillPeriodResult.from(bill, command.month(), existing.get());
      throw conflict("本期账单已付");
    }
    var transaction =
        transactionRepository
            .findActiveByLedgerAndId(bill.ledgerId(), command.transactionId())
            .filter(item -> item.details().direction() == TransactionDirection.EXPENSE)
            .filter(item -> !item.details().bookedOn().isAfter(LocalDate.now(LEDGER_ZONE)))
            .orElseThrow(() -> conflict("该支出已不可关联"));
    if (transaction.version() != command.version()
        || bills.hasPaymentForTransaction(transaction.id())) throw conflict("该支出已变更或被其它账期关联，请重新选择");
    return recordPayment(
        bill,
        command.month(),
        transaction.id(),
        transaction.details().bookedOn(),
        transaction.details().amountCents(),
        command.actorId());
  }

  @Transactional(readOnly = true)
  public Optional<FixedBillPaymentReferenceResult> findFixedBillPaymentReference(
      String ledgerId, String actorId, String transactionId) {
    ledgers.requireReadAccess(ledgerId, actorId);
    return bills.findPaymentReference(ledgerId, transactionId);
  }

  @Transactional
  public void unlinkFixedBillPayment(UnlinkFixedBillPaymentCommand command) {
    ledgers.requireWriteAccess(command.ledgerId(), command.actorId());
    var payment =
        bills
            .findPaymentReference(command.ledgerId(), command.transactionId())
            .filter(reference -> reference.paymentId().equals(command.paymentId()))
            .orElseThrow(() -> conflict("账期关联已变更，请重新查看"));
    bills.unlinkPayment(payment.paymentId(), command.actorId());
  }

  private FixedBillPeriodResult recordPayment(
      FixedBill bill,
      YearMonth month,
      String transactionId,
      LocalDate paidOn,
      long amountCents,
      String actorId) {
    var payment =
        new FixedBillPayment(
            ids.next(PAYMENT_PREFIX),
            bill.id(),
            month.atDay(1),
            transactionId,
            paidOn,
            amountCents);
    bills.addPayment(payment, actorId);
    return FixedBillPeriodResult.from(bill, month, payment);
  }

  private FixedBill requireBillPeriod(String ledgerId, String billId, YearMonth month) {
    requirePeriod(month);
    return bills
        .findBill(ledgerId, billId)
        .filter(row -> !row.firstDueOn().isAfter(month.atEndOfMonth()))
        .orElseThrow(() -> invalid("找不到该账期"));
  }

  private static String categoryLabel(LedgerCategoryResult category) {
    return category.code().equals("OTHER") ? "其它" : category.name();
  }

  private static Long toSearchCents(String term) {
    if (!term.matches("[0-9]{1,8}(?:[.][0-9]{1,2})?")) return null;
    return new BigDecimal(term).movePointRight(2).longValueExact();
  }

  private static FoundationException conflict(String message) {
    return new FoundationException(
        new ErrorCode("XIAOBU_FIXED_BILL_LINK_CONFLICT"),
        ErrorCategory.CONFLICT,
        message,
        Map.of());
  }

  private static void requirePeriod(YearMonth month) {
    if (month == null || month.getYear() < 1900 || month.getYear() > 9999) throw invalid("请选择有效账期");
  }

  private static FoundationException invalid(String message) {
    return new FoundationException(new ErrorCode("XIAOBU_FIXED_BILL_INVALID"), message);
  }
}
