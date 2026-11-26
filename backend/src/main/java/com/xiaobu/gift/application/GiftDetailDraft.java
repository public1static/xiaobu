package com.xiaobu.gift.application;

import com.xiaobu.ledger.application.TransactionExtensionData;

public record GiftDetailDraft(String personName, String reason)
    implements TransactionExtensionData {}
