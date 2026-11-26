package com.xiaobu.gift.application;

public record ListGiftActivitiesQuery(
    String ledgerId,
    String actorId,
    String bookId,
    String contactId,
    String keyword,
    int page,
    int pageSize) {}
