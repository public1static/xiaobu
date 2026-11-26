package com.xiaobu.ledger.interfaces;

import com.xiaobu.ledger.application.CreateLedgerInvitationResult;
import com.xiaobu.ledger.application.JoinedLedgerResult;
import com.xiaobu.ledger.application.LedgerInvitationPreviewResult;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    unmappedTargetPolicy = ReportingPolicy.ERROR)
interface LedgerInvitationWebMapper {

  CreateLedgerInvitationResponse toResponse(CreateLedgerInvitationResult result);

  LedgerInvitationPreviewResponse toResponse(LedgerInvitationPreviewResult result);

  JoinedLedgerResponse toResponse(JoinedLedgerResult result);
}
