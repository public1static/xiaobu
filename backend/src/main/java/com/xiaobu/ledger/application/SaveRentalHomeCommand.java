package com.xiaobu.ledger.application;

public record SaveRentalHomeCommand(
    String ledgerId, String actorId, String homeId, String name, String address, String note) {}
