package com.xiaobu.ledger.infrastructure;

import com.xiaobu.ledger.domain.Expense;
import com.xiaobu.ledger.domain.ExpenseDetails;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    unmappedTargetPolicy = ReportingPolicy.ERROR)
interface ExpensePersistenceMapper {

  @Mapping(target = "id", source = "expense.id")
  @Mapping(target = "ledgerId", source = "ledgerId")
  @Mapping(target = "direction", constant = "EXPENSE")
  @Mapping(target = "amountCents", source = "expense.details.amountCents")
  @Mapping(target = "bookedOn", source = "expense.details.bookedOn")
  @Mapping(target = "categoryCode", source = "expense.details.categoryCode")
  @Mapping(target = "note", source = "expense.details.note")
  @Mapping(target = "createdBy", source = "actorId")
  @Mapping(target = "updatedBy", source = "actorId")
  @Mapping(target = "deleted", constant = "false")
  LedgerTransactionEntity toNewEntity(Expense expense, String ledgerId, String actorId);

  @Mapping(target = "details", source = "entity")
  Expense toDomain(LedgerTransactionEntity entity);

  ExpenseDetails toDetails(LedgerTransactionEntity entity);
}
