package com.xiaobu.ledger.interfaces;

import com.xiaobu.ledger.application.SaveLedgerManualReminderCommand;
import com.xiaobu.ledger.domain.LedgerManualReminder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
interface LedgerManualReminderWebMapper {

  SaveLedgerManualReminderCommand toCommand(
      String ledgerId, String actorId, String reminderId, SaveLedgerManualReminderRequest body);

  @Mapping(target = ".", source = "reminder")
  @Mapping(target = "leadDays", expression = "java(reminder.leadDays())")
  LedgerManualReminderResponse toResponse(LedgerManualReminder reminder, boolean canUndo);
}
