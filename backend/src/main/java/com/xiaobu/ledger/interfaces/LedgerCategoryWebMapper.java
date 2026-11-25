package com.xiaobu.ledger.interfaces;

import com.xiaobu.ledger.application.LedgerCategoryResult;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    unmappedTargetPolicy = ReportingPolicy.ERROR)
interface LedgerCategoryWebMapper {
  LedgerCategoryResponse toResponse(LedgerCategoryResult result);
}
