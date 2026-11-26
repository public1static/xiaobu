package com.xiaobu.ledger.interfaces;

import com.xiaobu.ledger.application.LedgerTodoPageResult;
import com.xiaobu.ledger.application.LedgerTodoResult;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    unmappedTargetPolicy = ReportingPolicy.ERROR)
interface LedgerTodoWebMapper {

  LedgerTodoPageResponse toResponse(LedgerTodoPageResult result);

  LedgerTodoResponse toResponse(LedgerTodoResult result);
}
