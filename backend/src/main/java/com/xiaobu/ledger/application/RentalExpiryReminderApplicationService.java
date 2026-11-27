package com.xiaobu.ledger.application;

import com.aegisid.foundation.shared.error.ErrorCategory;
import com.aegisid.foundation.shared.error.ErrorCode;
import com.aegisid.foundation.shared.error.FoundationException;
import com.xiaobu.ledger.domain.RentalContract;
import com.xiaobu.ledger.domain.RentalExpiryReminder;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("local-expense | wechat-preview")
public class RentalExpiryReminderApplicationService {
  // 按账本业务时区确定当前日期。
  private final Clock clock;
  private final PersonalLedgerApplicationService ledgers;
  private final RentalRepository rentals;

  RentalExpiryReminderApplicationService(
      PersonalLedgerApplicationService ledgers, RentalRepository rentals, Clock clock) {
    this.clock = clock;
    this.ledgers = ledgers;
    this.rentals = rentals;
  }

  @Transactional(readOnly = true)
  public RentalExpiryReminderPageResult listRentalExpiryReminders(
      String ledgerId, String actorId, boolean completed, int page) {
    ledgers.requireReadAccess(ledgerId, actorId);
    var today = LocalDate.now(clock);
    var rows = rentals.listExpiryReminders(ledgerId, today, completed, page);
    return new RentalExpiryReminderPageResult(
        rows.entries(),
        rows.totalCount(),
        rentals.countPendingExpiryReminders(ledgerId, today),
        rows.page(),
        rows.pageSize(),
        today);
  }

  @Transactional(readOnly = true)
  public long countRentalExpiryReminders(String ledgerId, String actorId) {
    ledgers.requireReadAccess(ledgerId, actorId);
    return rentals.countPendingExpiryReminders(ledgerId, LocalDate.now(clock));
  }

  @Transactional
  public RentalContractResult configureRentalExpiryReminder(
      ConfigureRentalExpiryReminderCommand command) {
    ledgers.requireWriteAccess(command.ledgerId(), command.actorId());
    var contract = requireContract(command.ledgerId(), command.contractId());
    rentals.configureExpiryReminder(
        contract.ledgerId(),
        contract.id(),
        RentalExpiryReminder.requireLeadDays(command.remindDays()),
        command.actorId());
    return rentals.findContractDetails(contract.ledgerId(), contract.id()).orElseThrow();
  }

  @Transactional
  public RentalContractResult completeRentalExpiryReminder(
      CompleteRentalExpiryReminderCommand command) {
    ledgers.requireWriteAccess(command.ledgerId(), command.actorId());
    var contract = requireContract(command.ledgerId(), command.contractId());
    if (!contract.endsOn().equals(command.expectedEndsOn())) {
      throw conflict("合同到期日已变更，请重新查看");
    }
    if (!contract.endsOn().equals(contract.handledEnd())) {
      if (!RentalExpiryReminder.isPending(
          contract.endsOn(), contract.remindDays(), contract.handledEnd(), LocalDate.now(clock))) {
        throw conflict("还未进入合同提醒日期");
      }
      rentals.completeExpiryReminder(
          contract.ledgerId(), contract.id(), contract.endsOn(), command.actorId());
    }
    return rentals.findContractDetails(contract.ledgerId(), contract.id()).orElseThrow();
  }

  private RentalContract requireContract(String ledgerId, String contractId) {
    return rentals
        .findContract(ledgerId, contractId)
        .orElseThrow(() ->
            new FoundationException(
                new ErrorCode("XIAOBU_RENTAL_CONTRACT_INVALID"),
                ErrorCategory.NOT_FOUND,
                "找不到该合同",
                Map.of()));
  }

  private static FoundationException conflict(String message) {
    return new FoundationException(
        new ErrorCode("XIAOBU_RENTAL_REMINDER_CHANGED"), ErrorCategory.CONFLICT, message, Map.of());
  }
}
