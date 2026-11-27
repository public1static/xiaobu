package com.xiaobu.ledger.interfaces;

import com.xiaobu.ledger.application.CreateRentalContractCommand;
import com.xiaobu.ledger.application.GenerateRentalBillCommand;
import com.xiaobu.ledger.application.RentalContractResult;
import com.xiaobu.ledger.application.RentalHomeResult;
import com.xiaobu.ledger.application.SaveRentalHomeCommand;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
interface RentalWebMapper {
  SaveRentalHomeCommand toCommand(
      String ledgerId, String actorId, String homeId, SaveRentalHomeRequest body);

  CreateRentalContractCommand toCommand(
      String ledgerId, String actorId, CreateRentalContractRequest body);

  GenerateRentalBillCommand toCommand(
      String ledgerId, String actorId, String contractId, GenerateRentalBillRequest body);

  RentalHomeResponse toResponse(RentalHomeResult result);

  RentalContractResponse toResponse(RentalContractResult result);
}
