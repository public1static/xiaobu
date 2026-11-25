package com.xiaobu.ledger.domain;

/** A membership granted by an invitation. This slice grants read access only. */
public record LedgerMembership(String id, String ledgerId, String memberId, String inviterName) {}
