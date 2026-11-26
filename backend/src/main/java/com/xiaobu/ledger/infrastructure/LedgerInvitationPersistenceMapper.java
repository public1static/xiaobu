package com.xiaobu.ledger.infrastructure;

import com.xiaobu.ledger.domain.LedgerInvitation;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    unmappedTargetPolicy = ReportingPolicy.ERROR)
interface LedgerInvitationPersistenceMapper {

  LedgerInvitation toDomain(LedgerInvitationEntity entity);

  LedgerInvitationEntity toNewEntity(LedgerInvitation invitation);
}
