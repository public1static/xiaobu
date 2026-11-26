package com.xiaobu.ledger.infrastructure;

import com.xiaobu.ledger.domain.LedgerRefund;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    unmappedTargetPolicy = ReportingPolicy.ERROR)
interface LedgerRefundPersistenceMapper {

  LedgerRefund toDomain(LedgerRefundEntity entity);

  LedgerRefundEntity toNewEntity(LedgerRefund refund);
}
