ALTER TABLE ledger_accounts
  MODIFY name VARCHAR(40) NOT NULL,
  ADD COLUMN account_type VARCHAR(16) NOT NULL DEFAULT '其他',
  ADD COLUMN active TINYINT(1) NOT NULL DEFAULT 1,
  ADD COLUMN balance_known TINYINT(1) NOT NULL DEFAULT 1;
UPDATE ledger_accounts SET account_type = name WHERE name IN ('微信', '支付宝', '银行卡', '现金', '电子钱包', '其他', '京东白条', '花呗', '信用卡', '信用账户', '基金', '股票', '其他理财', '饭卡', '交通卡', '会员卡', '储值卡');
