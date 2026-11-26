package com.xiaobu.ledger.application;

import com.aegisid.foundation.data.id.EntityIdGenerator;
import com.aegisid.foundation.data.id.IdPrefix;
import com.aegisid.foundation.shared.error.ErrorCategory;
import com.aegisid.foundation.shared.error.ErrorCode;
import com.aegisid.foundation.shared.error.FoundationException;
import com.xiaobu.ledger.domain.RentalDeposit;
import com.xiaobu.ledger.domain.RentalDepositTimeline;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("local-expense | wechat-preview")
public class RentalDepositApplicationService {
  private static final ZoneId LEDGER_ZONE = ZoneId.of("Asia/Shanghai");
  private static final IdPrefix DEPOSIT_PREFIX = new IdPrefix("deposit");
  private final PersonalLedgerApplicationService ledgers;
  private final LedgerAccountApplicationService accounts;
  private final RentalRepository rentals;
  private final RentalDepositRepository deposits;
  private final EntityIdGenerator ids;

  RentalDepositApplicationService(
      PersonalLedgerApplicationService ledgers,
      LedgerAccountApplicationService accounts,
      RentalRepository rentals,
      RentalDepositRepository deposits,
      EntityIdGenerator ids) {
    this.ledgers = ledgers;
    this.accounts = accounts;
    this.rentals = rentals;
    this.deposits = deposits;
    this.ids = ids;
  }

  @Transactional(readOnly = true)
  public RentalDepositPageResult listRentalDeposits(
      String ledgerId, String actorId, String contractId, int page) {
    ledgers.requireReadAccess(ledgerId, actorId);
    requireContract(ledgerId, contractId);
    var rows = deposits.listDeposits(ledgerId, contractId, page);
    boolean owner = ledgers.hasOwnership(ledgerId, actorId);
    boolean canEditOwn = owner || ledgers.canModifyTransaction(ledgerId, actorId, actorId);
    var items =
        rows.entries().stream()
            .map(row ->
                RentalDepositResult.from(
                    row.deposit(),
                    row.accountName(),
                    owner || (canEditOwn && actorId.equals(row.deposit().createdBy()))))
            .toList();
    return new RentalDepositPageResult(
        items,
        deposits.sumHeld(ledgerId, contractId),
        rows.totalCount(),
        rows.page(),
        rows.pageSize());
  }

  @Transactional
  public void recordRentalDeposit(RecordRentalDepositCommand command) {
    ledgers.requireWriteAccess(command.ledgerId(), command.actorId());
    requireContract(command.ledgerId(), command.contractId());
    var entry =
        new RentalDeposit(
            ids.next(DEPOSIT_PREFIX),
            command.ledgerId(),
            command.contractId(),
            command.kind(),
            command.amountCents(),
            command.bookedOn(),
            command.accountId(),
            command.note(),
            command.actorId(),
            0);
    accounts.requireAccountInLedger(entry.ledgerId(), entry.accountId());
    requireValidTimeline(entry, null);
    deposits.addDeposit(entry);
  }

  @Transactional
  public void editRentalDeposit(EditRentalDepositCommand command) {
    ledgers.requireWriteAccess(command.ledgerId(), command.actorId());
    requireContract(command.ledgerId(), command.contractId());
    var previous =
        deposits
            .findDeposit(command.ledgerId(), command.contractId(), command.depositId())
            .orElseThrow(() ->
                failure("XIAOBU_RENTAL_DEPOSIT_NOT_FOUND", ErrorCategory.NOT_FOUND, "押金记录不存在"));
    if (!ledgers.canModifyTransaction(
        command.ledgerId(), command.actorId(), previous.createdBy())) {
      throw failure("XIAOBU_RENTAL_DEPOSIT_DENIED", ErrorCategory.FORBIDDEN, "无权修改这条押金记录");
    }
    if (previous.version() != command.expectedVersion()) {
      throw failure("XIAOBU_RENTAL_DEPOSIT_STALE", ErrorCategory.CONFLICT, "押金记录已变更，请重新打开");
    }
    var revised =
        new RentalDeposit(
            previous.id(),
            previous.ledgerId(),
            previous.contractId(),
            previous.kind(),
            command.amountCents(),
            command.bookedOn(),
            command.accountId(),
            command.note(),
            previous.createdBy(),
            previous.version());
    if (!Objects.equals(revised.accountId(), previous.accountId())) {
      accounts.requireAccountInLedger(revised.ledgerId(), revised.accountId());
    }
    requireValidTimeline(revised, previous.id());
    deposits.updateDeposit(revised, command.actorId());
  }

  private void requireValidTimeline(RentalDeposit deposit, String excludedId) {
    if (deposit.bookedOn().isAfter(LocalDate.now(LEDGER_ZONE))) {
      throw new FoundationException(new ErrorCode("XIAOBU_RENTAL_DEPOSIT_DATE"), "押金日期不能晚于今天");
    }
    var days = deposits.summarizeDailyChanges(deposit.ledgerId(), deposit.contractId(), excludedId);
    if (!RentalDepositTimeline.accepts(days, deposit)) {
      throw failure(
          "XIAOBU_RENTAL_DEPOSIT_EXCEEDS_HELD", ErrorCategory.CONFLICT, "退回金额超过当日已交押金，请核对金额和日期");
    }
  }

  private void requireContract(String ledgerId, String contractId) {
    rentals
        .findContract(ledgerId, contractId)
        .orElseThrow(
            () -> failure("XIAOBU_RENTAL_CONTRACT_INVALID", ErrorCategory.NOT_FOUND, "找不到该合同"));
  }

  private static FoundationException failure(String code, ErrorCategory category, String message) {
    return new FoundationException(new ErrorCode(code), category, message, Map.of());
  }
}
