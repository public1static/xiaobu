package com.xiaobu.ledger.application;

import com.aegisid.foundation.data.id.EntityIdGenerator;
import com.aegisid.foundation.data.id.IdPrefix;
import com.xiaobu.ledger.domain.LedgerTransaction;
import com.xiaobu.ledger.domain.LedgerTransactionDetails;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("local-expense")
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
    var transaction = new LedgerTransaction(idGenerator.next(TRANSACTION_PREFIX), details);
    repository.add(command.ledgerId(), command.actorId(), transaction);
    return LedgerTransactionResult.from(transaction);
  }

  @Transactional(readOnly = true)
  public List<LedgerTransactionResult> listLedgerTransactions(ListLedgerTransactionsQuery query) {
    ledgers.requireReadAccess(query.ledgerId(), query.actorId());
    return repository.findByLedgerAndMonth(query.ledgerId(), query.month()).stream()
        .map(LedgerTransactionResult::from)
        .toList();
  }
}
