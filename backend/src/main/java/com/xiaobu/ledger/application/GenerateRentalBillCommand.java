package com.xiaobu.ledger.application;

import java.time.LocalDate;

public record GenerateRentalBillCommand(
    String ledgerId, String actorId, String contractId, LocalDate firstDueOn) {}
