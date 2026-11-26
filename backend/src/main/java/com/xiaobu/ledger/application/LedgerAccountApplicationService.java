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
@Profile("local-expense | wechat-preview")
public class LedgerAccountApplicationService {

  private static final IdPrefix ACCOUNT_PREFIX = new IdPrefix("acct");
  private static final ErrorCode ACCOUNT_UNAVAILABLE = new ErrorCode("XIAOBU_ACCOUNT_UNAVAILABLE");

  private final PersonalLedgerApplicationService ledgers;
  private final LedgerAccountRepository accounts;
  private final LedgerAccountTransferRepository transfers;
  private final EntityIdGenerator ids;

  LedgerAccountApplicationService(
      PersonalLedgerApplicationService ledgers,
      LedgerAccountRepository accounts,
      LedgerAccountTransferRepository transfers,
      EntityIdGenerator ids) {
    this.ledgers = ledgers;
    this.accounts = accounts;
    this.transfers = transfers;
    this.ids = ids;
  }

  @Transactional
  public LedgerAccountResult createLedgerAccount(CreateLedgerAccountCommand command) {
    ledgers.lockLedger(command.ledgerId());
    ledgers.requireOwnership(command.ledgerId(), command.actorId());
    var account =
        new LedgerAccount(
            ids.next(ACCOUNT_PREFIX),
            command.ledgerId(),
            command.name(),
            command.openingBalanceCents(),
            command.type(),
            true,
            command.balanceKnown());
    accounts.add(account);
    return new LedgerAccountResult(
        account.id(),
        account.name(),
        account.openingBalanceCents(),
        0,
        account.openingBalanceCents(),
        account.type(),
        account.active(),
        account.balanceKnown());
  }

  @Transactional(readOnly = true)
  public List<LedgerAccountResult> listLedgerAccounts(String ledgerId, String actorId) {
    ledgers.requireReadAccess(ledgerId, actorId);
    var balances = accounts.findRecordedBalancesByLedger(ledgerId);
    var transferNet = transfers.findNetByLedger(ledgerId);
    return accounts.findByLedger(ledgerId).stream()
        .map(account -> {
          long recorded = balances.getOrDefault(account.id(), 0L);
          long balance =
              account.openingBalanceCents() + recorded + transferNet.getOrDefault(account.id(), 0L);
          return new LedgerAccountResult(
              account.id(),
              account.name(),
              account.openingBalanceCents(),
              recorded,
              balance,
              account.type(),
              account.active(),
              account.balanceKnown());
        })
        .toList();
  }

  public void requireAccountInLedger(String ledgerId, String accountId) {
    if (accountId != null
        && !accounts
            .findByLedgerAndId(ledgerId, accountId)
            .map(LedgerAccount::active)
            .orElse(false)) {
      throw new FoundationException(ACCOUNT_UNAVAILABLE, "请选择当前账本的账户");
    }
  }

  String findAccountName(String ledgerId, String accountId) {
    if (accountId == null) return null;
    return accounts.findByLedgerAndId(ledgerId, accountId).map(LedgerAccount::name).orElse(null);
  }

  // 按用户核对的当前余额反推期初，已发生的收支与转账仍然保留。
  @Transactional
  public void updateLedgerAccount(
      String ledgerId,
      String actorId,
      String accountId,
      String name,
      String type,
      boolean active,
      Long currentBalanceCents,
      boolean preserveBalance) {
    ledgers.lockLedger(ledgerId);
    ledgers.requireOwnership(ledgerId, actorId);
    var previous =
        accounts
            .findByLedgerAndId(ledgerId, accountId)
            .orElseThrow(() -> new FoundationException(ACCOUNT_UNAVAILABLE, "账户不存在"));
    if (preserveBalance) {
      accounts.update(
          new LedgerAccount(
              accountId,
              ledgerId,
              name,
              previous.openingBalanceCents(),
              type,
              active,
              previous.balanceKnown()));
      return;
    }
    long movement =
        accounts.findRecordedBalancesByLedger(ledgerId).getOrDefault(accountId, 0L)
            + transfers.findNetByLedger(ledgerId).getOrDefault(accountId, 0L);
    long opening =
        currentBalanceCents == null ? 0 : Math.subtractExact(currentBalanceCents, movement);
    accounts.update(
        new LedgerAccount(
            accountId, ledgerId, name, opening, type, active, currentBalanceCents != null));
  }

  // 账户流水采用账本权限，停用账户仍可读取。
  @Transactional(readOnly = true)
  public List<LedgerAccountHistoryResult> listAccountHistory(
      String ledgerId, String actorId, String accountId) {
    ledgers.requireReadAccess(ledgerId, actorId);
    requireExistingAccount(ledgerId, accountId);
    return accounts.findHistory(ledgerId, accountId, actorId);
  }

  // 将账户编号限定在当前账本内。
  private void requireExistingAccount(String ledgerId, String accountId) {
    if (!accounts.existsByLedgerAndId(ledgerId, accountId))
      throw new FoundationException(ACCOUNT_UNAVAILABLE, "账户不存在");
  }
}
