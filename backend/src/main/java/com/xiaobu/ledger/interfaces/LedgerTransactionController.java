package com.xiaobu.ledger.interfaces;

import com.aegisid.foundation.shared.error.ErrorCode;
import com.aegisid.foundation.shared.error.FoundationException;
import com.aegisid.foundation.web.api.ApiResponse;
import com.xiaobu.identity.interfaces.LedgerSessionContext;
import com.xiaobu.identity.interfaces.LocalPreviewAccount;
import com.xiaobu.ledger.application.ChangeLedgerTransactionStateCommand;
import com.xiaobu.ledger.application.EditLedgerTransactionCommand;
import com.xiaobu.ledger.application.LedgerTransactionApplicationService;
import com.xiaobu.ledger.application.ListLedgerTransactionsQuery;
import com.xiaobu.ledger.domain.TransactionDirection;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("local-expense | wechat-preview")
@RequestMapping("/api/v1/ledgers/{ledgerId}/transactions")
public class LedgerTransactionController {

  private static final ErrorCode INVALID_MONTH = new ErrorCode("XIAOBU_INVALID_MONTH");

  private final LedgerTransactionApplicationService transactions;
  private final LedgerTransactionWebMapper mapper;

  LedgerTransactionController(
      LedgerTransactionApplicationService transactions, LedgerTransactionWebMapper mapper) {
    this.transactions = transactions;
    this.mapper = mapper;
  }

  @GetMapping
  public ApiResponse<List<LedgerTransactionResponse>> listLedgerTransactions(
      @PathVariable("ledgerId") String ledgerId,
      @RequestParam(name = "month", required = false) String month,
      @RequestParam(name = "from", required = false) String from,
      @RequestParam(name = "through", required = false) String through,
      @RequestParam(name = "keyword", defaultValue = "") String keyword,
      HttpServletRequest request) {
    var actor = LedgerSessionContext.require(request);
    var query = toQuery(ledgerId, actor.actorId(), month, from, through, keyword);
    return ApiResponse.success(
        transactions.listLedgerTransactions(query).stream().map(mapper::toResponse).toList());
  }

  @PostMapping
  public ApiResponse<LedgerTransactionResponse> recordLedgerTransaction(
      @PathVariable("ledgerId") String ledgerId,
      @Valid @RequestBody RecordLedgerTransactionRequest request,
      HttpServletRequest httpRequest) {
    var actor = LedgerSessionContext.require(httpRequest);
    var command = mapper.toCommand(request, ledgerId, actor.actorId());
    return ApiResponse.success(mapper.toResponse(transactions.recordLedgerTransaction(command)));
  }

  @GetMapping("/{transactionId}")
  public ApiResponse<LedgerTransactionDetailResponse> getLedgerTransaction(
      @PathVariable("ledgerId") String ledgerId,
      @PathVariable("transactionId") String transactionId,
      HttpServletRequest request) {
    var actor = LedgerSessionContext.require(request);
    var result = transactions.getLedgerTransaction(ledgerId, actor.actorId(), transactionId);
    String name =
        actor.actorId().equals(result.createdBy())
            ? "我"
            : LocalPreviewAccount.displayNameForActor(result.createdBy());
    return ApiResponse.success(
        new LedgerTransactionDetailResponse(
            mapper.toResponse(result.transaction()),
            name,
            result.accountName(),
            result.version(),
            result.canModify()));
  }

  @GetMapping("/recently-deleted")
  public ApiResponse<List<DeletedLedgerTransactionResponse>> listRecentlyDeleted(
      @PathVariable("ledgerId") String ledgerId, HttpServletRequest request) {
    var actor = LedgerSessionContext.require(request);
    return ApiResponse.success(
        transactions.listRecentlyDeleted(ledgerId, actor.actorId()).stream()
            .map(result ->
                new DeletedLedgerTransactionResponse(
                    mapper.toResponse(result.transaction()), result.version()))
            .toList());
  }

  @PutMapping("/{transactionId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void editLedgerTransaction(
      @PathVariable("ledgerId") String ledgerId,
      @PathVariable("transactionId") String transactionId,
      @Valid @RequestBody EditLedgerTransactionRequest input,
      HttpServletRequest request) {
    var actor = LedgerSessionContext.require(request);
    var draft = input.draft();
    transactions.editLedgerTransaction(
        new EditLedgerTransactionCommand(
            ledgerId,
            actor.actorId(),
            transactionId,
            input.version(),
            TransactionDirection.valueOf(draft.direction()),
            draft.amountCents(),
            draft.bookedOn(),
            draft.categoryCode(),
            draft.note(),
            draft.accountId()));
  }

  @DeleteMapping("/{transactionId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteLedgerTransaction(
      @PathVariable("ledgerId") String ledgerId,
      @PathVariable("transactionId") String transactionId,
      @RequestParam(name = "version", defaultValue = "") String version,
      HttpServletRequest request) {
    var actor = LedgerSessionContext.require(request);
    transactions.deleteLedgerTransaction(
        new ChangeLedgerTransactionStateCommand(
            ledgerId, actor.actorId(), transactionId, parseVersion(version)));
  }

  @PostMapping("/{transactionId}/restore")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void restoreLedgerTransaction(
      @PathVariable("ledgerId") String ledgerId,
      @PathVariable("transactionId") String transactionId,
      @Valid @RequestBody RestoreLedgerTransactionRequest input,
      HttpServletRequest request) {
    var actor = LedgerSessionContext.require(request);
    transactions.restoreLedgerTransaction(
        new ChangeLedgerTransactionStateCommand(
            ledgerId, actor.actorId(), transactionId, input.version()));
  }

  private static long parseVersion(String value) {
    try {
      long version = Long.parseLong(value);
      if (version >= 0) return version;
    } catch (NumberFormatException ignored) {
      // Send the same request error for missing and malformed versions.
    }
    throw new FoundationException(new ErrorCode("XIAOBU_INVALID_VERSION"), "请提供有效流水版本");
  }

  private static ListLedgerTransactionsQuery toQuery(
      String ledgerId, String actorId, String month, String from, String through, String keyword) {
    if (month != null && from == null && through == null) {
      YearMonth selected = parseMonth(month);
      return new ListLedgerTransactionsQuery(
          ledgerId, actorId, selected.atDay(1), selected.atEndOfMonth(), keyword);
    }
    if (month != null || from == null || through == null) {
      throw invalidRange();
    }
    try {
      return new ListLedgerTransactionsQuery(
          ledgerId, actorId, LocalDate.parse(from), LocalDate.parse(through), keyword);
    } catch (DateTimeParseException ex) {
      throw invalidRange();
    }
  }

  private static FoundationException invalidRange() {
    return new FoundationException(new ErrorCode("XIAOBU_INVALID_DATE_RANGE"), "请选择有效的日期区间");
  }

  private static YearMonth parseMonth(String value) {
    try {
      YearMonth month = YearMonth.parse(value);
      if (month.getYear() >= 1900 && month.getYear() <= 9999) {
        return month;
      }
    } catch (DateTimeParseException e) {
      throw new FoundationException(INVALID_MONTH, "月份格式为 YYYY-MM");
    }
    throw new FoundationException(INVALID_MONTH, "月份超出有效范围");
  }
}
