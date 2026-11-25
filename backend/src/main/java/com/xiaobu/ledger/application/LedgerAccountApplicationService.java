package com.xiaobu.ledger.application;

import com.aegisid.foundation.data.id.EntityIdGenerator;
import com.aegisid.foundation.data.id.IdPrefix;
import com.aegisid.foundation.shared.error.ErrorCode;
import com.aegisid.foundation.shared.error.FoundationException;
import com.xiaobu.ledger.domain.LedgerAccount;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("local-expense")
public class LedgerAccountApplicationService {

  private static final IdPrefix ACCOUNT_PREFIX = new IdPrefix("acct");
  private static final ErrorCode ACCOUNT_UNAVAILABLE = new ErrorCode("XIAOBU_ACCOUNT_UNAVAILABLE");

  private final PersonalLedgerApplicationService ledgers;
  private final LedgerAccountRepository accounts;
  private final EntityIdGenerator ids;

  LedgerAccountApplicationService(
      PersonalLedgerApplicationService ledgers,
      LedgerAccountRepository accounts,
      EntityIdGenerator ids) {
    this.ledgers = ledgers;
    this.accounts = accounts;
    this.ids = ids;
  }

  @Transactional
  public LedgerAccountResult createLedgerAccount(CreateLedgerAccountCommand command) {
    ledgers.lockLedger(command.ledgerId());
    ledgers.requireOwnership(command.ledgerId(), command.actorId());
    var account = new LedgerAccount(ids.next(ACCOUNT_PREFIX), command.ledgerId(), command.name());
    accounts.add(account);
    return new LedgerAccountResult(account.id(), account.name(), 0);
  }

  @Transactional(readOnly = true)
  public List<LedgerAccountResult> listLedgerAccounts(String ledgerId, String actorId) {
    ledgers.requireReadAccess(ledgerId, actorId);
    var balances = accounts.findRecordedBalancesByLedger(ledgerId);
    return accounts.findByLedger(ledgerId).stream()
        .map(account ->
            new LedgerAccountResult(
                account.id(), account.name(), balances.getOrDefault(account.id(), 0L)))
        .toList();
  }

  public void requireAccountInLedger(String ledgerId, String accountId) {
    if (accountId != null && !accounts.existsByLedgerAndId(ledgerId, accountId)) {
      throw new FoundationException(ACCOUNT_UNAVAILABLE, "请选择当前账本的账户");
    }
  }

  String findAccountName(String ledgerId, String accountId) {
    if (accountId == null) return null;
    return accounts.findByLedgerAndId(ledgerId, accountId).map(LedgerAccount::name).orElse(null);
  }
}
