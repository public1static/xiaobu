package com.xiaobu.ledger.application;

import com.aegisid.foundation.shared.error.ErrorCategory;
import com.aegisid.foundation.shared.error.ErrorCode;
import com.aegisid.foundation.shared.error.FoundationException;
import com.xiaobu.ledger.domain.LedgerMembership;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("local-expense")
public class LedgerMemberApplicationService {

  private static final ErrorCode MEMBER_NOT_FOUND = new ErrorCode("XIAOBU_LEDGER_MEMBER_NOT_FOUND");

  private final PersonalLedgerApplicationService ledgers;
  private final LedgerMembershipRepository memberships;

  LedgerMemberApplicationService(
      PersonalLedgerApplicationService ledgers, LedgerMembershipRepository memberships) {
    this.ledgers = ledgers;
    this.memberships = memberships;
  }

  @Transactional(readOnly = true)
  public List<LedgerMemberResult> listLedgerMembers(String ledgerId, String actorId) {
    ledgers.requireOwnership(ledgerId, actorId);
    return memberships.findActiveByLedger(ledgerId).stream().map(LedgerMemberResult::from).toList();
  }

  @Transactional
  public void changeLedgerMemberRole(ChangeLedgerMemberRoleCommand command) {
    lockOwnedLedger(command.ledgerId(), command.actorId());
    var member = requireActiveMember(command.ledgerId(), command.memberId());
    memberships.changeRole(member.id(), command.role());
  }

  @Transactional
  public void removeLedgerMember(RemoveLedgerMemberCommand command) {
    lockOwnedLedger(command.ledgerId(), command.actorId());
    var member = requireActiveMember(command.ledgerId(), command.memberId());
    memberships.remove(member.id(), Instant.now());
  }

  private void lockOwnedLedger(String ledgerId, String actorId) {
    ledgers.lockLedger(ledgerId);
    ledgers.requireOwnership(ledgerId, actorId);
  }

  private LedgerMembership requireActiveMember(String ledgerId, String memberId) {
    return memberships
        .findByLedgerAndMember(ledgerId, memberId)
        .filter(LedgerMembership::isActive)
        .orElseThrow(() ->
            new FoundationException(
                MEMBER_NOT_FOUND, ErrorCategory.NOT_FOUND, "该成员已不在这个账本", Map.of()));
  }
}
