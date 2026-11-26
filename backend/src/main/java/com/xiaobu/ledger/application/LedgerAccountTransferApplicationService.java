package com.xiaobu.ledger.application;

import com.aegisid.foundation.data.id.EntityIdGenerator;
import com.aegisid.foundation.data.id.IdPrefix;
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
}
