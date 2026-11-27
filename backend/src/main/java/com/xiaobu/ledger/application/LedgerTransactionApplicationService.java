package com.xiaobu.ledger.application;

import com.aegisid.foundation.data.id.EntityIdGenerator;
import com.aegisid.foundation.data.id.IdPrefix;
import com.aegisid.foundation.shared.error.ErrorCategory;
import com.aegisid.foundation.shared.error.ErrorCode;
import com.aegisid.foundation.shared.error.FoundationException;
import com.xiaobu.ledger.domain.LedgerRefund;
import com.xiaobu.ledger.domain.LedgerTransaction;
import com.xiaobu.ledger.domain.LedgerTransactionDetails;
import com.xiaobu.ledger.domain.TransactionDirection;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("local-expense | wechat-preview")
public class LedgerTransactionApplicationService {

  private static final IdPrefix TRANSACTION_PREFIX = new IdPrefix("txn");

  private static final IdPrefix REFUND_PREFIX = new IdPrefix("rfnd");
  private static final ZoneId LEDGER_ZONE = ZoneId.of("Asia/Shanghai");

  private final LedgerTransactionRepository repository;
  private final LedgerStatementQueryRepository statements;
  private final LedgerRefundRepository refunds;
  private final EntityIdGenerator idGenerator;
  private final PersonalLedgerApplicationService ledgers;
  private final LedgerAccountApplicationService accounts;
  private final LedgerCategoryApplicationService categories;
  private final TransactionExtensionRegistry extensions;
  private final FixedBillPaymentLifecycle fixedBillPayments;

  public LedgerTransactionApplicationService(
      LedgerTransactionRepository repository,
      LedgerStatementQueryRepository statements,
      LedgerRefundRepository refunds,
      EntityIdGenerator idGenerator,
      PersonalLedgerApplicationService ledgers,
      LedgerAccountApplicationService accounts,
      LedgerCategoryApplicationService categories,
      TransactionExtensionRegistry extensions,
      FixedBillPaymentLifecycle fixedBillPayments) {
    this.repository = repository;
    this.statements = statements;
    this.refunds = refunds;
    this.idGenerator = idGenerator;
    this.ledgers = ledgers;
    this.accounts = accounts;
    this.categories = categories;
    this.extensions = extensions;
    this.fixedBillPayments = fixedBillPayments;
  }

  @Transactional
  public LedgerTransactionResult recordLedgerTransaction(RecordLedgerTransactionCommand command) {
    ledgers.requireWriteAccess(command.ledgerId(), command.actorId());
    accounts.requireAccountInLedger(command.ledgerId(), command.accountId());
    var details =
        new LedgerTransactionDetails(
            command.direction(),
            command.amountCents(),
            command.bookedOn(),
            command.categoryCode(),
            command.note(),
            command.accountId());
    categories.requireActiveCategory(
        command.ledgerId(), details.direction(), details.categoryCode());
    var transaction =
        new LedgerTransaction(
            idGenerator.next(TRANSACTION_PREFIX), command.actorId(), details, 0, false);
    repository.add(command.ledgerId(), command.actorId(), transaction);
    extensions.apply(
        extensionContext(command.ledgerId(), command.actorId(), transaction.id(), details),
        command.extensions(),
        true);
    return LedgerTransactionResult.from(transaction);
  }

  // 在同一只读事务内取明细与完整汇总，申请分组时才执行排行查询。
  @Transactional(readOnly = true)
  public LedgerStatementPageResult listLedgerTransactions(ListLedgerTransactionsQuery query) {
    ledgers.requireReadAccess(query.ledgerId(), query.actorId());
    var days = statements.summarizeByDay(query);
    long income = 0;
    long expense = 0;
    long count = 0;
    for (var day : days) {
      income += day.incomeCents();
      expense += day.expenseCents();
      count += day.entryCount();
    }
    var entries =
        (long) query.page() * query.pageSize() < count
            ? statements.findPage(query)
            : List.<LedgerTransactionResult>of();
    return new LedgerStatementPageResult(
        entries,
        days,
        income,
        expense,
        income - expense,
        count,
        query.page(),
        query.pageSize(),
        query.groupBy() == null ? List.of() : statements.summarizeByGroup(query));
  }

  @Transactional(readOnly = true)
  public LedgerTransactionDetailResult getLedgerTransaction(
      String ledgerId, String actorId, String transactionId) {
    ledgers.requireReadAccess(ledgerId, actorId);
    var entry =
        repository
            .findActiveByLedgerAndId(ledgerId, transactionId)
            .orElseThrow(() ->
                new FoundationException(
                    new ErrorCode("XIAOBU_TRANSACTION_NOT_FOUND"),
                    ErrorCategory.NOT_FOUND,
                    "未找到这笔流水",
                    Map.of()));
    var recordedRefunds =
        refunds.findByTransaction(ledgerId, transactionId).stream()
            .map(refund ->
                LedgerRefundResult.from(
                    refund, accounts.findAccountName(ledgerId, refund.accountId())))
            .toList();
    return new LedgerTransactionDetailResult(
        LedgerTransactionResult.from(entry),
        entry.createdBy(),
        accounts.findAccountName(ledgerId, entry.details().accountId()),
        entry.version(),
        ledgers.canModifyTransaction(ledgerId, actorId, entry.createdBy())
            && extensions.allowsModification(
                extensionContext(ledgerId, actorId, entry.id(), entry.details())),
        recordedRefunds,
        extensions.read(extensionContext(ledgerId, actorId, entry.id(), entry.details())));
  }

  @Transactional(readOnly = true)
  public List<DeletedLedgerTransactionResult> listRecentlyDeleted(String ledgerId, String actorId) {
    ledgers.requireReadAccess(ledgerId, actorId);
    if (!ledgers.canModifyTransaction(ledgerId, actorId, actorId)) {
      throw denied();
    }
    String creator = ledgers.hasOwnership(ledgerId, actorId) ? null : actorId;
    return repository.findRecentlyDeleted(ledgerId, creator).stream()
        .map(entry ->
            new DeletedLedgerTransactionResult(
                LedgerTransactionResult.from(entry), entry.version()))
        .toList();
  }

  @Transactional
  public void editLedgerTransaction(EditLedgerTransactionCommand command) {
    var original =
        requireEditable(
            command.ledgerId(),
            command.actorId(),
            command.transactionId(),
            command.expectedVersion(),
            false);
    var recordedRefunds = refunds.findByTransaction(command.ledgerId(), command.transactionId());
    if (!recordedRefunds.isEmpty()
        && (command.direction() != TransactionDirection.EXPENSE
            || command.amountCents() < refundedCents(recordedRefunds)
            || recordedRefunds.stream()
                .anyMatch(refund -> refund.receivedOn().isBefore(command.bookedOn())))) {
      throw new FoundationException(
          new ErrorCode("XIAOBU_TRANSACTION_HAS_REFUNDS"),
          ErrorCategory.CONFLICT,
          "这笔支出已有退款，不能改为收入、缩减至已退款金额以下，或把付款日期改到退款之后",
          Map.of());
    }
    if (!Objects.equals(command.accountId(), original.details().accountId()))
      accounts.requireAccountInLedger(command.ledgerId(), command.accountId());
    var details =
        new LedgerTransactionDetails(
            command.direction(),
            command.amountCents(),
            command.bookedOn(),
            command.categoryCode(),
            command.note(),
            command.accountId());
    if (original.details().direction() != details.direction()
        || !original.details().categoryCode().equals(details.categoryCode())) {
      categories.requireActiveCategory(
          command.ledgerId(), details.direction(), details.categoryCode());
    }
    extensions.apply(
        extensionContext(command.ledgerId(), command.actorId(), command.transactionId(), details),
        command.extensions(),
        false);
    repository.edit(command.ledgerId(), command.transactionId(), command.actorId(), details);
  }

  private static TransactionExtensionContext extensionContext(
      String ledgerId, String actorId, String transactionId, LedgerTransactionDetails details) {
    return new TransactionExtensionContext(
        ledgerId, actorId, transactionId, details.direction(), details.categoryCode());
  }

  @Transactional
  public void deleteLedgerTransaction(ChangeLedgerTransactionStateCommand command) {
    changeLedgerTransactionDeletion(command, true);
  }

  @Transactional
  public void restoreLedgerTransaction(ChangeLedgerTransactionStateCommand command) {
    changeLedgerTransactionDeletion(command, false);
  }

  private void changeLedgerTransactionDeletion(
      ChangeLedgerTransactionStateCommand command, boolean deleted) {
    var entry =
        requireEditable(
            command.ledgerId(),
            command.actorId(),
            command.transactionId(),
            command.expectedVersion(),
            !deleted);
    repository.changeDeletion(
        command.ledgerId(), command.transactionId(), command.actorId(), deleted);
    if (entry.details().direction() != TransactionDirection.EXPENSE) return;
    // Same ledger lock/transaction: the source state and bill status cannot diverge.
    if (deleted) {
      fixedBillPayments.suspendForDeletedSource(command.ledgerId(), entry.id(), command.actorId());
    } else {
      fixedBillPayments.reconcileRestoredSource(command.ledgerId(), entry.id(), command.actorId());
    }
  }

  private LedgerTransaction requireEditable(
      String ledgerId,
      String actorId,
      String transactionId,
      long expectedVersion,
      boolean deleted) {
    ledgers.requireWriteAccess(ledgerId, actorId);
    var entry =
        repository
            .findByLedgerAndId(ledgerId, transactionId)
            .orElseThrow(() ->
                new FoundationException(
                    new ErrorCode("XIAOBU_TRANSACTION_NOT_FOUND"),
                    ErrorCategory.NOT_FOUND,
                    "未找到这笔流水",
                    Map.of()));
    if (!ledgers.canModifyTransaction(ledgerId, actorId, entry.createdBy())) {
      throw denied();
    }
    if (entry.version() != expectedVersion || entry.deleted() != deleted) {
      throw new FoundationException(
          new ErrorCode("XIAOBU_TRANSACTION_CHANGED"),
          ErrorCategory.CONFLICT,
          "流水已变更，请重新查看后再操作",
          Map.of());
    }
    if (!extensions.allowsModification(
        extensionContext(ledgerId, actorId, entry.id(), entry.details()))) {
      throw new FoundationException(
          new ErrorCode("XIAOBU_TRANSACTION_EXTENSION_LOCKED"),
          ErrorCategory.CONFLICT,
          "关联业务已归档，请重新开放后再修改",
          Map.of());
    }
    return entry;
  }

  @Transactional
  public LedgerRefundResult recordLedgerRefund(RecordLedgerRefundCommand command) {
    var original =
        requireEditable(
            command.ledgerId(),
            command.actorId(),
            command.transactionId(),
            command.expectedVersion(),
            false);
    if (original.details().direction() != TransactionDirection.EXPENSE) {
      throw invalidRefund("只有支出可以登记退款");
    }
    long remaining =
        original.details().amountCents()
            - refundedCents(refunds.findByTransaction(command.ledgerId(), command.transactionId()));
    if (command.amountCents() <= 0 || command.amountCents() > remaining) {
      throw invalidRefund("退款金额必须大于 0 且不能超过剩余可退金额");
    }
    if (command.receivedOn().isBefore(original.details().bookedOn())
        || command.receivedOn().isAfter(LocalDate.now(LEDGER_ZONE))) {
      throw invalidRefund("退款到账日期须在原付款日期至今天之间");
    }
    accounts.requireAccountInLedger(command.ledgerId(), command.accountId());
    var refund =
        new LedgerRefund(
            idGenerator.next(REFUND_PREFIX),
            command.ledgerId(),
            original.id(),
            command.amountCents(),
            command.receivedOn(),
            command.accountId(),
            command.actorId());
    refunds.add(refund);
    return LedgerRefundResult.from(
        refund, accounts.findAccountName(command.ledgerId(), command.accountId()));
  }

  private static long refundedCents(List<LedgerRefund> refunds) {
    return refunds.stream().mapToLong(LedgerRefund::amountCents).sum();
  }

  private static FoundationException invalidRefund(String message) {
    return new FoundationException(new ErrorCode("XIAOBU_INVALID_REFUND"), message);
  }

  private FoundationException denied() {
    return new FoundationException(
        new ErrorCode("XIAOBU_TRANSACTION_MODIFY_DENIED"),
        ErrorCategory.FORBIDDEN,
        "无权维护这笔流水",
        Map.of());
  }
}
