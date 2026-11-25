package com.xiaobu.ledger.interfaces;

import com.xiaobu.ledger.application.LedgerRefundResult;
import com.xiaobu.ledger.application.LedgerTransactionResult;
import com.xiaobu.ledger.application.RecordLedgerTransactionCommand;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    unmappedTargetPolicy = ReportingPolicy.ERROR)
interface LedgerTransactionWebMapper {

  RecordLedgerTransactionCommand toCommand(
      RecordLedgerTransactionRequest request, String ledgerId, String actorId);

  LedgerTransactionResponse toResponse(LedgerTransactionResult result);

  LedgerRefundResponse toResponse(LedgerRefundResult result);
}
