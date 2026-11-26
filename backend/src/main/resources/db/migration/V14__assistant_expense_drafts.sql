CREATE TABLE assistant_expense_drafts (
  id VARCHAR(48) NOT NULL PRIMARY KEY,
  conversation_id VARCHAR(48) NOT NULL,
  version BIGINT NOT NULL,
  status VARCHAR(16) NOT NULL,
  booked_on DATE NOT NULL,
  amount_cents BIGINT NULL,
  category_code VARCHAR(20) NULL,
  account_id VARCHAR(48) NULL,
  note VARCHAR(100) NOT NULL,
  transaction_id VARCHAR(48) NULL,
  KEY ix_assistant_pending (conversation_id,status),
  FOREIGN KEY (conversation_id) REFERENCES assistant_conversations (id),
  FOREIGN KEY (transaction_id) REFERENCES ledger_transactions (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
