package com.xiaobu.ledger.infrastructure;

import com.xiaobu.ledger.domain.LedgerTransaction;
import com.xiaobu.ledger.domain.LedgerTransactionDetails;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    unmappedTargetPolicy = ReportingPolicy.ERROR)
interface LedgerTransactionPersistenceMapper {

  @Mapping(target = "id", source = "transaction.id")
  @Mapping(target = "ledgerId", source = "ledgerId")
  @Mapping(target = "direction", source = "transaction.details.direction")
  @Mapping(target = "amountCents", source = "transaction.details.amountCents")
  @Mapping(target = "bookedOn", source = "transaction.details.bookedOn")
  @Mapping(target = "categoryCode", source = "transaction.details.categoryCode")
  @Mapping(target = "note", source = "transaction.details.note")
  @Mapping(target = "createdBy", source = "actorId")
  @Mapping(target = "updatedBy", source = "actorId")
  @Mapping(target = "deleted", constant = "false")
  LedgerTransactionEntity toNewEntity(
      LedgerTransaction transaction, String ledgerId, String actorId);

  @Mapping(target = "details", source = "entity")
  LedgerTransaction toDomain(LedgerTransactionEntity entity);

  LedgerTransactionDetails toDetails(LedgerTransactionEntity entity);
}
