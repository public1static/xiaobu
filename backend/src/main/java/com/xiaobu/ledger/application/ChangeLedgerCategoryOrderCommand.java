package com.xiaobu.ledger.application;

import com.xiaobu.ledger.domain.TransactionDirection;
import java.util.List;

public record ChangeLedgerCategoryOrderCommand(
    String ledgerId, String actorId, TransactionDirection direction, List<String> codes) {}
