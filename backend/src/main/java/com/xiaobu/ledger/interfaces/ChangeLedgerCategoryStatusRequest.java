package com.xiaobu.ledger.interfaces;

import jakarta.validation.constraints.NotNull;

record ChangeLedgerCategoryStatusRequest(@NotNull Boolean active) {}
