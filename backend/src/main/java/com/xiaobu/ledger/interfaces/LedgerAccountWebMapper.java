package com.xiaobu.ledger.interfaces;

import com.xiaobu.ledger.application.LedgerAccountResult;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    unmappedTargetPolicy = ReportingPolicy.ERROR)
interface LedgerAccountWebMapper {

  LedgerAccountResponse toResponse(LedgerAccountResult result);
}
