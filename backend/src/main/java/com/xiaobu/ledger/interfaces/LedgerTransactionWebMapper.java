package com.xiaobu.ledger.interfaces;

import com.xiaobu.ledger.application.LedgerRefundResult;
import com.xiaobu.ledger.application.LedgerStatementDayResult;
import com.xiaobu.ledger.application.LedgerStatementGroupResult;
import com.xiaobu.ledger.application.LedgerStatementPageResult;
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
public interface LedgerTransactionWebMapper {

  @Mapping(target = "extensions", source = "changes")
  RecordLedgerTransactionCommand toCommand(
      RecordLedgerTransactionRequest request,
      String ledgerId,
      String actorId,
      List<TransactionExtensionChange> changes);

  LedgerTransactionResponse toResponse(LedgerTransactionResult result);

  LedgerStatementDayResponse toResponse(LedgerStatementDayResult result);

  // 显式映射分组维度及各金额口径，新增响应字段会触发编译检查。
  LedgerStatementGroupResponse toResponse(LedgerStatementGroupResult result);

  LedgerStatementPageResponse toResponse(LedgerStatementPageResult result);

  LedgerRefundResponse toResponse(LedgerRefundResult result);
}
