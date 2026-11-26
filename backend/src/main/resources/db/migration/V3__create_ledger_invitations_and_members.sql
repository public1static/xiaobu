CREATE TABLE ledger_invitations (
    token_digest CHAR(64) NOT NULL,
    ledger_id VARCHAR(48) NOT NULL,
    inviter_id VARCHAR(48) NOT NULL,
    inviter_name VARCHAR(32) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    accepted_by VARCHAR(48),
    PRIMARY KEY (token_digest),
    CONSTRAINT fk_invitation_ledger FOREIGN KEY (ledger_id) REFERENCES personal_ledgers (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;

CREATE TABLE ledger_members (
    id VARCHAR(48) NOT NULL,
    ledger_id VARCHAR(48) NOT NULL,
    member_id VARCHAR(48) NOT NULL,
    inviter_name VARCHAR(32) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ledger_member (ledger_id, member_id),
    KEY ix_member_ledger (member_id),
    CONSTRAINT fk_membership_ledger FOREIGN KEY (ledger_id) REFERENCES personal_ledgers (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
