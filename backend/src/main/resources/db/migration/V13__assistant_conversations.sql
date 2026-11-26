CREATE TABLE assistant_conversations (
    id VARCHAR(48) NOT NULL PRIMARY KEY,
    ledger_id VARCHAR(48) NOT NULL,
    owner_id VARCHAR(48) NOT NULL,
    title VARCHAR(120) NOT NULL,
    revision BIGINT NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    KEY ix_assistant_history (ledger_id, owner_id, updated_at, id),
    FOREIGN KEY (ledger_id) REFERENCES personal_ledgers (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;

CREATE TABLE assistant_turns (
    conversation_id VARCHAR(48) NOT NULL,
    sequence_no BIGINT NOT NULL,
    client_id VARCHAR(36) NOT NULL,
    question VARCHAR(500) NOT NULL,
    answer_json MEDIUMTEXT NOT NULL,
    PRIMARY KEY (conversation_id, sequence_no),
    UNIQUE KEY uk_assistant_client (conversation_id, client_id),
    FOREIGN KEY (conversation_id) REFERENCES assistant_conversations (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
