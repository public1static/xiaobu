package com.xiaobu.gift.application;

public record CreateGiftContactCommand(
    String ledgerId, String actorId, String name, String distinction) {}
