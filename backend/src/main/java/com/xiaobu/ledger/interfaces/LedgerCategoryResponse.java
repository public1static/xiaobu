package com.xiaobu.ledger.interfaces;

import com.xiaobu.ledger.application.TransactionExtensionCapability;
import com.xiaobu.ledger.domain.TransactionDirection;
import java.util.List;

record LedgerCategoryResponse(
    String code,
    TransactionDirection direction,
    String name,
    boolean active,
    List<TransactionExtensionCapability> capabilities) {}
