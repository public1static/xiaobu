package com.xiaobu.ledger.interfaces;

public record RentalHomeResponse(
    String id, String name, String address, String note, long contractCount) {}
