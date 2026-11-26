package com.xiaobu.gift.application;

import com.xiaobu.gift.domain.GiftBook;

public record GiftActivityFilter(
    String ledgerId, String activityBookId, GiftBook dailyBook, String contactId, String keyword) {}
