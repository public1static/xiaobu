package com.xiaobu.ledger.application;

import com.aegisid.foundation.data.id.EntityIdGenerator;
import com.aegisid.foundation.data.id.IdPrefix;
import com.aegisid.foundation.shared.error.ErrorCode;
import com.aegisid.foundation.shared.error.FoundationException;
import com.xiaobu.ledger.domain.LedgerAccountTransfer;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("local-expense | wechat-preview")
public class LedgerAccountTransferApplicationService {

  private static final IdPrefix TRANSFER_PREFIX = new IdPrefix("xfer");

  private final PersonalLedgerApplicationService ledgers;
  private final LedgerAccountApplicationService accounts;
  private final LedgerAccountTransferRepository transfers;
  private final EntityIdGenerator ids;

  LedgerAccountTransferApplicationService(
      PersonalLedgerApplicationService ledgers,
      LedgerAccountApplicationService accounts,
      LedgerAccountTransferRepository transfers,
      EntityIdGenerator ids) {
    this.ledgers = ledgers;
    this.accounts = accounts;
    this.transfers = transfers;
    this.ids = ids;
  }

  @Transactional
  public LedgerAccountTransferResult recordTransfer(RecordLedgerAccountTransferCommand command) {
    ledgers.requireWriteAccess(command.ledgerId(), command.actorId());
    var transfer =
        new LedgerAccountTransfer(
            ids.next(TRANSFER_PREFIX),
            command.ledgerId(),
            command.sourceAccountId(),
            command.targetAccountId(),
            command.amountCents(),
            command.bookedOn(),
            command.note(),
            command.actorId());
    accounts.requireAccountInLedger(command.ledgerId(), transfer.sourceAccountId());
    accounts.requireAccountInLedger(command.ledgerId(), transfer.targetAccountId());
    transfers.add(transfer);
    return LedgerAccountTransferResult.from(transfer);
  }

  @Transactional(readOnly = true)
  public List<LedgerAccountTransferResult> listRecentTransfers(String ledgerId, String actorId) {
    ledgers.requireReadAccess(ledgerId, actorId);
    return transfers.findRecentByLedger(ledgerId).stream()
        .map(LedgerAccountTransferResult::from)
        .toList();
  }

  // 修改历史转账时允许保留原来使用的已停用账户。
  @Transactional
  public void updateTransfer(String id, RecordLedgerAccountTransferCommand command) {
    var previous = requireMutableTransfer(command.ledgerId(), command.actorId(), id);
    var updated =
        new LedgerAccountTransfer(
            id,
            command.ledgerId(),
            command.sourceAccountId(),
            command.targetAccountId(),
            command.amountCents(),
            command.bookedOn(),
            command.note(),
            previous.createdBy());
    if (!previous.sourceAccountId().equals(updated.sourceAccountId())) {
      accounts.requireAccountInLedger(command.ledgerId(), updated.sourceAccountId());
    }
    if (!previous.targetAccountId().equals(updated.targetAccountId())) {
      accounts.requireAccountInLedger(command.ledgerId(), updated.targetAccountId());
    }
    transfers.update(updated);
  }

  // 在同一账本事务内撤销转账。
  @Transactional
  public void cancelTransfer(String ledgerId, String actorId, String id) {
    requireMutableTransfer(ledgerId, actorId, id);
    transfers.cancel(ledgerId, id);
  }

  // 成员仅可维护自己创建的转账，账本管理者可维护全部。
  private LedgerAccountTransfer requireMutableTransfer(String ledgerId, String actorId, String id) {
    ledgers.requireWriteAccess(ledgerId, actorId);
    var previous =
        transfers
            .findByLedgerAndId(ledgerId, id)
            .orElseThrow(
                () -> new FoundationException(new ErrorCode("XIAOBU_TRANSFER_MISSING"), "转账记录不存在"));
    if (!actorId.equals(previous.createdBy())) ledgers.requireOwnership(ledgerId, actorId);
    return previous;
  }
}
