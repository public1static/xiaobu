package com.xiaobu.ledger.infrastructure;

import com.xiaobu.ledger.domain.LedgerAccountTransfer;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    unmappedTargetPolicy = ReportingPolicy.ERROR)
interface LedgerAccountTransferPersistenceMapper {

  LedgerAccountTransfer toDomain(LedgerAccountTransferEntity entity);

  LedgerAccountTransferEntity toNewEntity(LedgerAccountTransfer transfer);
}
