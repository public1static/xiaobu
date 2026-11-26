package com.xiaobu.ledger.interfaces;

import com.aegisid.foundation.web.api.ApiResponse;
import com.xiaobu.identity.interfaces.LocalPreviewAccount;
import com.xiaobu.identity.interfaces.LocalSessionContext;
import com.xiaobu.ledger.application.ChangeLedgerMemberRoleCommand;
import com.xiaobu.ledger.application.LedgerMemberApplicationService;
import com.xiaobu.ledger.application.RemoveLedgerMemberCommand;
import com.xiaobu.ledger.domain.LedgerMemberRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("local-expense")
@RequestMapping("/api/v1/ledgers/{ledgerId}/members")
public class LedgerMemberController {

  private final LedgerMemberApplicationService members;

  LedgerMemberController(LedgerMemberApplicationService members) {
    this.members = members;
  }

  @GetMapping
  public ApiResponse<List<LedgerMemberResponse>> listLedgerMembers(
      @PathVariable("ledgerId") String ledgerId, HttpServletRequest request) {
    String actorId = LocalSessionContext.require(request).actorId();
    return ApiResponse.success(
        members.listLedgerMembers(ledgerId, actorId).stream()
            .map(member ->
                new LedgerMemberResponse(
                    member.memberId(),
                    LocalPreviewAccount.displayNameForActor(member.memberId()),
                    member.role()))
            .toList());
  }

  @PutMapping("/{memberId}/role")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void changeLedgerMemberRole(
      @PathVariable("ledgerId") String ledgerId,
      @PathVariable("memberId") String memberId,
      @Valid @RequestBody ChangeLedgerMemberRoleRequest input,
      HttpServletRequest request) {
    String actorId = LocalSessionContext.require(request).actorId();
    members.changeLedgerMemberRole(
        new ChangeLedgerMemberRoleCommand(
            ledgerId, actorId, memberId, LedgerMemberRole.valueOf(input.role())));
  }

  @DeleteMapping("/{memberId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void removeLedgerMember(
      @PathVariable("ledgerId") String ledgerId,
      @PathVariable("memberId") String memberId,
      HttpServletRequest request) {
    String actorId = LocalSessionContext.require(request).actorId();
    members.removeLedgerMember(new RemoveLedgerMemberCommand(ledgerId, actorId, memberId));
  }
}
