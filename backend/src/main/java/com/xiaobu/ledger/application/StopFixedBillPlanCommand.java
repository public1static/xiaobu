package com.xiaobu.ledger.application;

import java.time.LocalDate;

public record StopFixedBillPlanCommand(
    String ledgerId, String actorId, String billId, LocalDate expectedStopOn) {}
