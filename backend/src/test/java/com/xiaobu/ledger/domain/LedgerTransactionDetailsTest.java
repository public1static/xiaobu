package com.xiaobu.ledger.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aegisid.foundation.shared.error.FoundationException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class LedgerTransactionDetailsTest {

  private static final LocalDate DAY = LocalDate.of(2026, 11, 25);

  @Test
  void trimsNoteWithoutChangingExactCents() {
    var expense =
        new LedgerTransactionDetails(TransactionDirection.EXPENSE, 1230, DAY, "DINING", " 午饭 ");
    assertEquals(1230, expense.amountCents());
    assertEquals("午饭", expense.note());
    assertEquals(
        "",
        new LedgerTransactionDetails(TransactionDirection.EXPENSE, 1, DAY, "DINING", null).note());
  }

  @Test
  void rejectsInvalidAmountDateAndCategory() {
    assertThrows(
        FoundationException.class,
        () -> new LedgerTransactionDetails(TransactionDirection.EXPENSE, 0, DAY, "DINING", ""));
    assertThrows(
        FoundationException.class,
        () ->
            new LedgerTransactionDetails(
                TransactionDirection.EXPENSE, 10_000_000_000L, DAY, "DINING", ""));
    assertThrows(
        FoundationException.class,
        () -> new LedgerTransactionDetails(TransactionDirection.EXPENSE, 1, null, "DINING", ""));
    assertThrows(
        FoundationException.class,
        () -> new LedgerTransactionDetails(TransactionDirection.EXPENSE, 1, DAY, "SALARY", ""));
    assertThrows(
        FoundationException.class,
        () ->
            new LedgerTransactionDetails(
                TransactionDirection.EXPENSE, 1, DAY, "DINING", "a".repeat(101)));
  }

  @Test
  void incomeHasItsOwnCategoriesAndUsesPositiveCents() {
    var salary =
        new LedgerTransactionDetails(TransactionDirection.INCOME, 500000, DAY, "SALARY", "工资");
    assertEquals(500000, salary.amountCents());
    assertThrows(
        FoundationException.class,
        () -> new LedgerTransactionDetails(TransactionDirection.INCOME, 500000, DAY, "DINING", ""));
    assertThrows(
        FoundationException.class, () -> new LedgerTransactionDetails(null, 1, DAY, "DINING", ""));
  }
}
