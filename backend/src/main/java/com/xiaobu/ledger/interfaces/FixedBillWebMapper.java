package com.xiaobu.ledger.interfaces;

import com.xiaobu.ledger.application.CreateFixedBillCommand;
import com.xiaobu.ledger.application.FixedBillPageResult;
import com.xiaobu.ledger.application.FixedBillPeriodResult;
import com.xiaobu.ledger.application.PayFixedBillCommand;
import java.time.YearMonth;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
interface FixedBillWebMapper {
  CreateFixedBillCommand toCommand(String ledgerId, String actorId, CreateFixedBillRequest body);

  @Mapping(target = "month", source = "month")
  PayFixedBillCommand toCommand(
      String ledgerId, String actorId, String billId, YearMonth month, PayFixedBillRequest body);

  FixedBillPeriodResponse toResponse(FixedBillPeriodResult result);

  FixedBillPageResponse toResponse(FixedBillPageResult result);
}
