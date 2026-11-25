package com.xiaobu.ledger.application;

import com.aegisid.foundation.data.id.EntityIdGenerator;
import com.aegisid.foundation.data.id.IdPrefix;
import com.aegisid.foundation.shared.error.ErrorCategory;
import com.aegisid.foundation.shared.error.ErrorCode;
import com.aegisid.foundation.shared.error.FoundationException;
import com.xiaobu.ledger.domain.LedgerTransaction;
import com.xiaobu.ledger.domain.LedgerTransactionDetails;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("local-expense | wechat-preview")
public class LedgerTransactionApplicationService {

  private static final IdPrefix TRANSACTION_PREFIX = new IdPrefix("txn");

  private final LedgerTransactionRepository repository;
  private final EntityIdGenerator idGenerator;
  private final PersonalLedgerApplicationService ledgers;
  private final LedgerAccountApplicationService accounts;

  public LedgerTransactionApplicationService(
      LedgerTransactionRepository repository,
      EntityIdGenerator idGenerator,
      PersonalLedgerApplicationService ledgers,
      LedgerAccountApplicationService accounts) {
    this.repository = repository;
    this.idGenerator = idGenerator;
    this.ledgers = ledgers;
    this.accounts = accounts;
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
    var transaction =
        new LedgerTransaction(
            idGenerator.next(TRANSACTION_PREFIX), command.actorId(), details, 0, false);
    repository.add(command.ledgerId(), command.actorId(), transaction);
    return LedgerTransactionResult.from(transaction);
  }

  @Transactional(readOnly = true)
  public List<LedgerTransactionResult> listLedgerTransactions(ListLedgerTransactionsQuery query) {
    ledgers.requireReadAccess(query.ledgerId(), query.actorId());
    return repository
        .findByLedgerAndDateRange(query.ledgerId(), query.from(), query.through(), query.keyword())
        .stream()
        .map(LedgerTransactionResult::from)
        .toList();
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
    return new LedgerTransactionDetailResult(
        LedgerTransactionResult.from(entry),
        entry.createdBy(),
        accounts.findAccountName(ledgerId, entry.details().accountId()),
        entry.version(),
        ledgers.canModifyTransaction(ledgerId, actorId, entry.createdBy()));
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
    requireEditable(
        command.ledgerId(),
        command.actorId(),
        command.transactionId(),
        command.expectedVersion(),
        false);
    accounts.requireAccountInLedger(command.ledgerId(), command.accountId());
    var details =
        new LedgerTransactionDetails(
            command.direction(),
            command.amountCents(),
            command.bookedOn(),
            command.categoryCode(),
            command.note(),
            command.accountId());
    repository.edit(command.ledgerId(), command.transactionId(), command.actorId(), details);
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
    requireEditable(
        command.ledgerId(),
        command.actorId(),
        command.transactionId(),
        command.expectedVersion(),
        !deleted);
    repository.changeDeletion(
        command.ledgerId(), command.transactionId(), command.actorId(), deleted);
  }

  private void requireEditable(
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
  }

  private FoundationException denied() {
    return new FoundationException(
        new ErrorCode("XIAOBU_TRANSACTION_MODIFY_DENIED"),
        ErrorCategory.FORBIDDEN,
        "无权维护这笔流水",
        Map.of());
  }
}
