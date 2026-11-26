package com.xiaobu.ledger.interfaces;

import com.xiaobu.ledger.application.CreateFixedBillCommand;
import com.xiaobu.ledger.application.FixedBillCandidatePageResult;
import com.xiaobu.ledger.application.FixedBillCandidateResult;
import com.xiaobu.ledger.application.FixedBillPageResult;
import com.xiaobu.ledger.application.FixedBillPaymentReferenceResult;
import com.xiaobu.ledger.application.FixedBillPeriodResult;
import com.xiaobu.ledger.application.FixedBillPlanPageResult;
import com.xiaobu.ledger.application.FixedBillPlanResult;
import com.xiaobu.ledger.application.LinkFixedBillPaymentCommand;
import com.xiaobu.ledger.application.PayFixedBillCommand;
import com.xiaobu.ledger.application.ReviseFixedBillPlanCommand;
import com.xiaobu.ledger.application.StopFixedBillPlanCommand;
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

  @Mapping(target = "month", source = "month")
  LinkFixedBillPaymentCommand toCommand(
      String ledgerId,
      String actorId,
      String billId,
      YearMonth month,
      LinkFixedBillPaymentRequest body);

  FixedBillCandidateResponse toResponse(FixedBillCandidateResult result);

  FixedBillCandidatePageResponse toResponse(FixedBillCandidatePageResult result);

  FixedBillPaymentReferenceResponse toResponse(FixedBillPaymentReferenceResult result);

  ReviseFixedBillPlanCommand toCommand(
      String ledgerId, String actorId, String billId, ReviseFixedBillPlanRequest body);

  StopFixedBillPlanCommand toCommand(
      String ledgerId, String actorId, String billId, StopFixedBillPlanRequest body);

  FixedBillPlanResponse toResponse(FixedBillPlanResult result);

  FixedBillPlanPageResponse toResponse(FixedBillPlanPageResult result);

  FixedBillPeriodResponse toResponse(FixedBillPeriodResult result);

  FixedBillPageResponse toResponse(FixedBillPageResult result);
}
