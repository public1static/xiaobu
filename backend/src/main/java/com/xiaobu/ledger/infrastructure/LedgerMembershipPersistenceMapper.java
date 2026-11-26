package com.xiaobu.ledger.infrastructure;

import com.xiaobu.ledger.domain.LedgerMembership;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    unmappedTargetPolicy = ReportingPolicy.ERROR)
interface LedgerMembershipPersistenceMapper {

  LedgerMembership toDomain(LedgerMembershipEntity entity);

  LedgerMembershipEntity toNewEntity(LedgerMembership membership);
}
