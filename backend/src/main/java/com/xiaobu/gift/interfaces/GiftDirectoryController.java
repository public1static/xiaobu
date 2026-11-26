package com.xiaobu.gift.interfaces;

import com.aegisid.foundation.web.api.ApiResponse;
import com.xiaobu.gift.application.GiftDirectoryApplicationService;
import com.xiaobu.gift.application.ListGiftActivitiesQuery;
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
  private final GiftDirectoryWebMapper mapper;

  GiftDirectoryController(GiftDirectoryApplicationService gifts, GiftDirectoryWebMapper mapper) {
    this.gifts = gifts;
    this.mapper = mapper;
  }

  @GetMapping("/books")
  ApiResponse<List<GiftBookResponse>> listGiftBooks(
      @PathVariable String ledgerId, HttpServletRequest request) {
    return ApiResponse.success(
        gifts.listGiftBooks(ledgerId, actor(request)).stream().map(mapper::toResponse).toList());
  }

  @PostMapping("/books")
  @ResponseStatus(HttpStatus.CREATED)
  ApiResponse<GiftBookResponse> createGiftBook(
      @PathVariable String ledgerId,
      @Valid @RequestBody CreateGiftBookRequest input,
      HttpServletRequest request) {
    return ApiResponse.success(
        mapper.toResponse(gifts.createGiftBook(mapper.toCommand(input, ledgerId, actor(request)))));
  }

  @PutMapping("/books/{id}/archive")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void changeGiftBookArchive(
      @PathVariable String ledgerId,
      @PathVariable("id") String bookId,
      @Valid @RequestBody ChangeGiftBookArchiveRequest input,
      HttpServletRequest request) {
    gifts.changeGiftBookArchive(mapper.toCommand(input, ledgerId, actor(request), bookId));
  }

  @GetMapping("/contacts")
  ApiResponse<List<GiftContactResponse>> listGiftContacts(
      @PathVariable String ledgerId, HttpServletRequest request) {
    return ApiResponse.success(
        gifts.listGiftContacts(ledgerId, actor(request)).stream().map(mapper::toResponse).toList());
  }

  @PostMapping("/contacts")
  @ResponseStatus(HttpStatus.CREATED)
  ApiResponse<GiftContactResponse> createGiftContact(
      @PathVariable String ledgerId,
      @Valid @RequestBody CreateGiftContactRequest input,
      HttpServletRequest request) {
    return ApiResponse.success(
        mapper.toResponse(
            gifts.createGiftContact(mapper.toCommand(input, ledgerId, actor(request)))));
  }

  @GetMapping("/activity")
  ApiResponse<GiftActivityPageResponse> listGiftActivities(
      @PathVariable String ledgerId,
      @RequestParam(required = false) String bookId,
      @RequestParam(required = false) String contactId,
      @RequestParam(defaultValue = "") String keyword,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int pageSize,
      HttpServletRequest request) {
    return ApiResponse.success(
        mapper.toResponse(
            gifts.listGiftActivities(
                new ListGiftActivitiesQuery(
                    ledgerId, actor(request), bookId, contactId, keyword, page, pageSize))));
  }

  private static String actor(HttpServletRequest request) {
    return LedgerSessionContext.require(request).actorId();
  }

  record CreateGiftBookRequest(
      @NotBlank @Size(max = 60) String name,
      @NotNull TransactionDirection direction,
      LocalDate defaultDate,
      @Size(max = 60) String defaultReason) {}

  record ChangeGiftBookArchiveRequest(@NotNull Boolean archived) {}

  record CreateGiftContactRequest(
      @NotBlank @Size(max = 60) String name, @Size(max = 60) String distinction) {}
}
