package com.xiaobu.gift.application;

public record ChangeGiftBookArchiveCommand(
    String ledgerId, String actorId, String bookId, boolean archived) {}
