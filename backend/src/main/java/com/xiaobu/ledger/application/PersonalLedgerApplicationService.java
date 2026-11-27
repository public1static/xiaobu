package com.xiaobu.ledger.application;

import com.aegisid.foundation.data.id.EntityIdGenerator;
import com.aegisid.foundation.data.id.IdPrefix;
import com.aegisid.foundation.shared.error.ErrorCategory;
import com.aegisid.foundation.shared.error.ErrorCode;
import com.aegisid.foundation.shared.error.FoundationException;
import com.xiaobu.ledger.domain.LedgerMembership;
import com.xiaobu.ledger.domain.PersonalLedger;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("local-expense | wechat-preview")
public class PersonalLedgerApplicationService {

  private static final IdPrefix LEDGER_PREFIX = new IdPrefix("book");
  private static final ErrorCode LEDGER_ACCESS_DENIED =
      new ErrorCode("XIAOBU_LEDGER_ACCESS_DENIED");

  private static final ErrorCode LEDGER_READ_ONLY = new ErrorCode("XIAOBU_LEDGER_READ_ONLY");

  private final PersonalLedgerRepository ledgers;
  private final EntityIdGenerator ids;
  private final LedgerMembershipRepository memberships;

  PersonalLedgerApplicationService(
      PersonalLedgerRepository ledgers,
      EntityIdGenerator ids,
      LedgerMembershipRepository memberships) {
    this.ledgers = ledgers;
    this.ids = ids;
    this.memberships = memberships;
  }

  public PersonalLedger openPersonalLedger(String ownerId) {
    return ledgers
        .findByOwnerId(ownerId)
        .orElseGet(() -> ledgers.addIfAbsent(new PersonalLedger(ids.next(LEDGER_PREFIX), ownerId)));
  }

  // 目录按当前身份决定是否公开成员管理数据。
  public boolean isOwner(String ledgerId, String actorId) {
    return ledgers.existsByIdAndOwnerId(ledgerId, actorId);
  }

  public void requireOwnership(String ledgerId, String actorId) {
    if (!ledgers.existsByIdAndOwnerId(ledgerId, actorId)) {
      throw denied();
    }
  }

  public void requireReadAccess(String ledgerId, String actorId) {
    if (!ledgers.existsByIdAndOwnerId(ledgerId, actorId)
        && !memberships.existsActiveByLedgerAndMember(ledgerId, actorId)) {
      throw denied();
    }
  }

  void lockLedger(String ledgerId) {
    if (!memberships.lockLedgerMembershipChanges(ledgerId)) {
      throw denied();
    }
  }

  public void requireWriteAccess(String ledgerId, String actorId) {
    lockLedger(ledgerId);
    if (ledgers.existsByIdAndOwnerId(ledgerId, actorId)) {
      return;
    }
    var member =
        memberships
            .findByLedgerAndMember(ledgerId, actorId)
            .filter(LedgerMembership::isActive)
            .orElseThrow(this::denied);
    if (!member.canRecord()) {
      throw new FoundationException(
          LEDGER_READ_ONLY, ErrorCategory.FORBIDDEN, "当前权限为只读，这笔账未保存", Map.of());
    }
  }

  boolean hasOwnership(String ledgerId, String actorId) {
    return ledgers.existsByIdAndOwnerId(ledgerId, actorId);
  }

  boolean canModifyTransaction(String ledgerId, String actorId, String creatorId) {
    return hasOwnership(ledgerId, actorId)
        || (actorId.equals(creatorId)
            && memberships
                .findByLedgerAndMember(ledgerId, actorId)
                .filter(LedgerMembership::canRecord)
                .isPresent());
  }

  private FoundationException denied() {
    return new FoundationException(
        LEDGER_ACCESS_DENIED, ErrorCategory.FORBIDDEN, "无权访问这个账本", Map.of());
  }
}
