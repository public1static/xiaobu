package com.xiaobu.ledger.interfaces;

import com.xiaobu.ledger.application.CompleteRentalExpiryReminderCommand;
import com.xiaobu.ledger.application.ConfigureRentalExpiryReminderCommand;
import com.xiaobu.ledger.application.RentalExpiryReminderPageResult;
import com.xiaobu.ledger.application.RentalExpiryReminderResult;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
interface RentalExpiryReminderWebMapper {
  ConfigureRentalExpiryReminderCommand toCommand(
      String ledgerId,
      String actorId,
      String contractId,
      ConfigureRentalExpiryReminderRequest body);

  CompleteRentalExpiryReminderCommand toCommand(
      String ledgerId, String actorId, String contractId, CompleteRentalExpiryReminderRequest body);

  RentalExpiryReminderResponse toResponse(RentalExpiryReminderResult row, boolean pending);

  default RentalExpiryReminderPageResponse toResponse(RentalExpiryReminderPageResult page) {
    var items =
        page.entries().stream().map(row -> toResponse(row, row.isPending(page.today()))).toList();
    return new RentalExpiryReminderPageResponse(
        items, page.totalCount(), page.pendingCount(), page.page(), page.pageSize(), page.today());
  }
}
