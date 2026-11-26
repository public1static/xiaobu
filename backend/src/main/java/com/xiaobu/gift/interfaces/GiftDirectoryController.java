package com.xiaobu.gift.interfaces;

import com.aegisid.foundation.web.api.ApiResponse;
import com.xiaobu.gift.application.GiftActivityResult;
import com.xiaobu.gift.application.GiftDirectoryApplicationService;
import com.xiaobu.gift.domain.GiftActivityBook;
import com.xiaobu.gift.domain.GiftContact;
import com.xiaobu.identity.interfaces.LedgerSessionContext;
import com.xiaobu.ledger.domain.TransactionDirection;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/v1/ledgers/{ledgerId}/gifts")
class GiftDirectoryController {
  private final GiftDirectoryApplicationService gifts;

  GiftDirectoryController(GiftDirectoryApplicationService gifts) {
    this.gifts = gifts;
  }

  @GetMapping("/books")
  ApiResponse<List<GiftActivityBook>> listBooks(
      @PathVariable String ledgerId, HttpServletRequest request) {
    return ApiResponse.success(gifts.listBooks(ledgerId, actor(request)));
  }

  @PostMapping("/books")
  @ResponseStatus(HttpStatus.CREATED)
  ApiResponse<GiftActivityBook> createBook(
      @PathVariable String ledgerId,
      @Valid @RequestBody CreateBookRequest input,
      HttpServletRequest request) {
    return ApiResponse.success(
        gifts.createBook(
            ledgerId,
            actor(request),
            input.name(),
            input.direction(),
            input.defaultDate(),
            input.defaultReason()));
  }

  @PutMapping("/books/{id}/archive")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void changeArchive(
      @PathVariable String ledgerId,
      @PathVariable String id,
      @Valid @RequestBody ArchiveBookRequest input,
      HttpServletRequest request) {
    gifts.changeBookArchive(ledgerId, actor(request), id, input.archived());
  }

  @GetMapping("/contacts")
  ApiResponse<List<GiftContact>> listContacts(
      @PathVariable String ledgerId, HttpServletRequest request) {
    return ApiResponse.success(gifts.listContacts(ledgerId, actor(request)));
  }

  @PostMapping("/contacts")
  @ResponseStatus(HttpStatus.CREATED)
  ApiResponse<GiftContact> createContact(
      @PathVariable String ledgerId,
      @Valid @RequestBody CreateContactRequest input,
      HttpServletRequest request) {
    return ApiResponse.success(
        gifts.createContact(ledgerId, actor(request), input.name(), input.distinction()));
  }

  @GetMapping("/activity")
  ApiResponse<List<GiftActivityResult>> listActivity(
      @PathVariable String ledgerId,
      @RequestParam(required = false) String bookId,
      @RequestParam(required = false) String contactId,
      HttpServletRequest request) {
    return ApiResponse.success(gifts.listActivities(ledgerId, actor(request), bookId, contactId));
  }

  private static String actor(HttpServletRequest request) {
    return LedgerSessionContext.require(request).actorId();
  }

  record CreateBookRequest(
      @NotBlank @Size(max = 60) String name,
      @NotNull TransactionDirection direction,
      LocalDate defaultDate,
      @Size(max = 60) String defaultReason) {}

  record ArchiveBookRequest(@NotNull Boolean archived) {}

  record CreateContactRequest(
      @NotBlank @Size(max = 60) String name, @Size(max = 60) String distinction) {}
}
