package com.xiaobu.gift.application;

import java.util.List;

public record SaveGiftReasonsCommand(
    String ledgerId, String actorId, List<String> previous, List<String> names) {}
