package com.xiaobu.gift.interfaces;

import com.aegisid.foundation.web.api.ApiResponse;
import com.xiaobu.gift.application.LedgerContactApplicationService;
import com.xiaobu.identity.interfaces.LedgerSessionContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("local-expense | wechat-preview")
@RequestMapping("/api/v1/ledgers/{ledgerId}/contacts")
class LedgerContactController {
  private final LedgerContactApplicationService contacts;
  private final GiftDirectoryWebMapper mapper;

  LedgerContactController(LedgerContactApplicationService contacts, GiftDirectoryWebMapper mapper) {
    this.contacts = contacts;
    this.mapper = mapper;
  }

  @GetMapping("/matches")
  public ApiResponse<ContactMatchesResponse> listContactMatches(
      @PathVariable String ledgerId,
      @RequestParam @Size(min = 1, max = 60) String name,
      @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
      HttpServletRequest request) {
    var actor = LedgerSessionContext.require(request);
    var rows = contacts.listContactMatches(ledgerId, actor.actorId(), name, page);
    return ApiResponse.success(
        new ContactMatchesResponse(
            rows.entries().stream().map(mapper::toResponse).toList(),
            rows.totalCount(),
            rows.page(),
            rows.pageSize()));
  }

  record ContactMatchesResponse(
      List<GiftContactResponse> entries, long totalCount, int page, int pageSize) {}
}
