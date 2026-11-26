package com.xiaobu.ledger.interfaces;

import com.xiaobu.ledger.application.LedgerMonthlyBudgetResult;
import com.xiaobu.ledger.application.SetLedgerMonthlyBudgetCommand;
import java.time.YearMonth;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    unmappedTargetPolicy = ReportingPolicy.ERROR)
interface LedgerMonthlyBudgetWebMapper {

  @Mapping(target = "month", source = "month")
  SetLedgerMonthlyBudgetCommand toCommand(
      String ledgerId, String actorId, YearMonth month, SetLedgerMonthlyBudgetRequest request);

  LedgerMonthlyBudgetResponse toResponse(LedgerMonthlyBudgetResult result);
}
