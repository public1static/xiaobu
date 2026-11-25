package com.xiaobu.ledger.interfaces;

import com.aegisid.foundation.shared.error.ErrorCode;
import com.aegisid.foundation.shared.error.FoundationException;
import com.aegisid.foundation.web.api.ApiResponse;
import com.xiaobu.ledger.application.ExpenseLedger;
import com.xiaobu.ledger.domain.Expense;
import com.xiaobu.ledger.domain.ExpenseDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** First vertical slice, available only with an explicit local preview profile. */
@RestController
@Profile("local-expense")
@RequestMapping("/api/v1/expenses")
public class ExpenseController {

  private static final String PREVIEW_BOOK = "book_local_primary";
  private static final String PREVIEW_ACTOR = "user_local_primary";

  private final ExpenseLedger ledger;

  public ExpenseController(ExpenseLedger ledger) {
    this.ledger = ledger;
  }

  @GetMapping
  public ApiResponse<List<ExpenseResponse>> list(@RequestParam("month") String month) {
    return ApiResponse.success(
        ledger.list(PREVIEW_BOOK, PREVIEW_ACTOR, parseMonth(month)).stream()
            .map(ExpenseResponse::from)
            .toList());
  }

  private static YearMonth parseMonth(String value) {
    try {
      YearMonth month = YearMonth.parse(value);
      if (month.getYear() >= 1900 && month.getYear() <= 9999) {
        return month;
      }
    } catch (DateTimeParseException e) {
      throw new FoundationException(new ErrorCode("XIAOBU_INVALID_MONTH"), "月份格式为 YYYY-MM");
    }
    throw new FoundationException(new ErrorCode("XIAOBU_INVALID_MONTH"), "月份超出有效范围");
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  public ApiResponse<ExpenseResponse> record(@Valid @RequestBody ExpenseRequest request) {
    return ApiResponse.success(
        ExpenseResponse.from(
            ledger.record(
                PREVIEW_BOOK,
                PREVIEW_ACTOR,
                new ExpenseDetails(
                    request.amountCents(),
                    request.bookedOn(),
                    request.categoryCode(),
                    request.note()))));
  }

  public record ExpenseRequest(
      @NotNull Long amountCents, @NotNull LocalDate bookedOn, String categoryCode, String note) {}

  public record ExpenseResponse(
      String id, long amountCents, LocalDate bookedOn, String categoryCode, String note) {

    static ExpenseResponse from(Expense expense) {
      var details = expense.details();
      return new ExpenseResponse(
          expense.id(),
          details.amountCents(),
          details.bookedOn(),
          details.categoryCode(),
          details.note());
    }
  }
}
