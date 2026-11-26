package com.xiaobu.gift.application;

import com.xiaobu.ledger.application.TransactionExtensionData;

public record GiftDetailDraft(
    String personName,
    String reason,
    String bookId,
    String contactId,
    Boolean createContact,
    String distinction)
    implements TransactionExtensionData {}
