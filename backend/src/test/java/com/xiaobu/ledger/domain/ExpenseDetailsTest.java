package com.xiaobu.ledger.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aegisid.foundation.shared.error.FoundationException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ExpenseDetailsTest {

  private static final LocalDate DAY = LocalDate.of(2026, 11, 25);

  @Test
  void trimsNoteWithoutChangingExactCents() {
    var expense = new ExpenseDetails(1230, DAY, "DINING", " 午饭 ");
    assertEquals(1230, expense.amountCents());
    assertEquals("午饭", expense.note());
    assertEquals("", new ExpenseDetails(1, DAY, "DINING", null).note());
  }

  @Test
  void rejectsInvalidAmountDateAndCategory() {
    assertThrows(FoundationException.class, () -> new ExpenseDetails(0, DAY, "DINING", ""));
    assertThrows(
        FoundationException.class, () -> new ExpenseDetails(10_000_000_000L, DAY, "DINING", ""));
    assertThrows(FoundationException.class, () -> new ExpenseDetails(1, null, "DINING", ""));
    assertThrows(FoundationException.class, () -> new ExpenseDetails(1, DAY, "SALARY", ""));
    assertThrows(
        FoundationException.class, () -> new ExpenseDetails(1, DAY, "DINING", "a".repeat(101)));
  }
}
