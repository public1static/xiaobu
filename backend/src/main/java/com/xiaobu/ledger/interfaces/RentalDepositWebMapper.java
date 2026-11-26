package com.xiaobu.ledger.interfaces;

import com.xiaobu.ledger.application.EditRentalDepositCommand;
import com.xiaobu.ledger.application.RecordRentalDepositCommand;
import com.xiaobu.ledger.application.RentalDepositPageResult;
import com.xiaobu.ledger.application.RentalDepositResult;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
interface RentalDepositWebMapper {
  RecordRentalDepositCommand toCommand(
      String ledgerId, String actorId, String contractId, RecordRentalDepositRequest body);

  EditRentalDepositCommand toCommand(
      String ledgerId,
      String actorId,
      String contractId,
      String depositId,
      EditRentalDepositRequest body);

  RentalDepositResponse toResponse(RentalDepositResult row);

  RentalDepositPageResponse toResponse(RentalDepositPageResult page);
}
