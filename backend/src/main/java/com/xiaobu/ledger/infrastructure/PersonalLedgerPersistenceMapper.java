package com.xiaobu.ledger.infrastructure;

import com.xiaobu.ledger.domain.PersonalLedger;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    unmappedTargetPolicy = ReportingPolicy.ERROR)
interface PersonalLedgerPersistenceMapper {

  PersonalLedger toDomain(PersonalLedgerEntity entity);

  PersonalLedgerEntity toNewEntity(PersonalLedger ledger);
}
