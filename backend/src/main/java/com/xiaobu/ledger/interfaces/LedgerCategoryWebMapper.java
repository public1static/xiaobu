package com.xiaobu.ledger.interfaces;

import com.xiaobu.ledger.application.LedgerCategoryResult;
import com.xiaobu.ledger.application.TransactionExtensionCapability;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    unmappedTargetPolicy = ReportingPolicy.ERROR)
interface LedgerCategoryWebMapper {
  @Mapping(target = "capabilities", source = "capabilities")
  LedgerCategoryResponse toResponse(
      LedgerCategoryResult result, List<TransactionExtensionCapability> capabilities);
}
