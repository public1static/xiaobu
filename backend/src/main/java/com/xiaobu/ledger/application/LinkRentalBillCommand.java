package com.xiaobu.ledger.application;

public record LinkRentalBillCommand(
    String ledgerId, String actorId, String contractId, String billId) {}
