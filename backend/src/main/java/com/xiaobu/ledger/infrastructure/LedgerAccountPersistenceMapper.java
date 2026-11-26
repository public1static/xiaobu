package com.xiaobu.ledger.infrastructure;

import com.xiaobu.ledger.domain.LedgerAccount;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    unmappedTargetPolicy = ReportingPolicy.ERROR)
interface LedgerAccountPersistenceMapper {

  LedgerAccount toDomain(LedgerAccountEntity entity);

  LedgerAccountEntity toNewEntity(LedgerAccount account);
}
