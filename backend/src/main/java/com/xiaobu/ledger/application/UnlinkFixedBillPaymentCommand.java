package com.xiaobu.ledger.application;

public record UnlinkFixedBillPaymentCommand(
    String ledgerId, String actorId, String transactionId, String paymentId) {}
