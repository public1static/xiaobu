CREATE TABLE wechat_preview_sessions (
    token_digest CHAR(64) NOT NULL,
    actor_id VARCHAR(48) NOT NULL,
    display_name VARCHAR(32) NOT NULL,
    ledger_id VARCHAR(48) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    PRIMARY KEY (token_digest),
    KEY ix_wechat_session_expiry (expires_at),
    CONSTRAINT fk_wechat_session_ledger FOREIGN KEY (ledger_id) REFERENCES personal_ledgers (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
