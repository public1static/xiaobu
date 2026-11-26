package com.xiaobu.ledger.interfaces;

import com.xiaobu.ledger.application.LedgerAccountTransferResult;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    unmappedTargetPolicy = ReportingPolicy.ERROR)
interface LedgerAccountTransferWebMapper {

  LedgerAccountTransferResponse toResponse(LedgerAccountTransferResult result);
}
