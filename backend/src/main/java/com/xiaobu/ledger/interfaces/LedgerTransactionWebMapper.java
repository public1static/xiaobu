package com.xiaobu.ledger.interfaces;

import com.xiaobu.ledger.application.LedgerRefundResult;
import com.xiaobu.ledger.application.LedgerTransactionResult;
import com.xiaobu.ledger.application.RecordLedgerTransactionCommand;
import com.xiaobu.ledger.application.TransactionExtensionChange;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    unmappedTargetPolicy = ReportingPolicy.ERROR)
interface LedgerTransactionWebMapper {

  @Mapping(target = "extensions", source = "changes")
  RecordLedgerTransactionCommand toCommand(
      RecordLedgerTransactionRequest request,
      String ledgerId,
      String actorId,
      List<TransactionExtensionChange> changes);

  LedgerTransactionResponse toResponse(LedgerTransactionResult result);

  LedgerRefundResponse toResponse(LedgerRefundResult result);
}
