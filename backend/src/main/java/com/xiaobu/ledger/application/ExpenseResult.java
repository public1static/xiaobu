package com.xiaobu.ledger.application;

import com.xiaobu.ledger.domain.Expense;
import java.time.LocalDate;

public record ExpenseResult(
    String id, long amountCents, LocalDate bookedOn, String categoryCode, String note) {

  static ExpenseResult from(Expense expense) {
    var details = expense.details();
    return new ExpenseResult(
        expense.id(),
        details.amountCents(),
        details.bookedOn(),
        details.categoryCode(),
        details.note());
  }
}
