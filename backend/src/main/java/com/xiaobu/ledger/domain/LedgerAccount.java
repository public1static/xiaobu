package com.xiaobu.ledger.domain;

import com.aegisid.foundation.shared.error.ErrorCode;
import com.aegisid.foundation.shared.error.FoundationException;
import java.util.Set;

public record LedgerAccount(
    String id,
    String ledgerId,
    String name,
    long openingBalanceCents,
    String type,
    boolean active,
    boolean balanceKnown) {

  public static final long MAX_BALANCE_CENTS = 9_999_999_999L;

  // 支持的类型与原型类型目录保持一致。
  private static final Set<String> TYPES =
      Set.of(
          "微信", "支付宝", "银行卡", "现金", "电子钱包", "其他", "京东白条", "花呗", "信用卡", "信用账户", "基金", "股票", "其他理财",
          "饭卡", "交通卡", "会员卡", "储值卡");

  // 旧客户端没有显式类型时沿用名称推断。
  public LedgerAccount {
    type = type == null && name != null && TYPES.contains(name.strip()) ? name.strip() : type;
    if (type == null) type = "其他";
    if (!TYPES.contains(type)) {
      throw new FoundationException(new ErrorCode("XIAOBU_ACCOUNT_TYPE_INVALID"), "请选择有效账户类型");
    }
    if (openingBalanceCents < -MAX_BALANCE_CENTS || openingBalanceCents > MAX_BALANCE_CENTS) {
      throw new FoundationException(
          new ErrorCode("XIAOBU_ACCOUNT_OPENING_BALANCE_INVALID"), "期初余额超出金额范围");
    }
    name = name == null ? "" : name.strip();
    if (name.isBlank() || name.length() > 40) {
      throw new FoundationException(
          new ErrorCode("XIAOBU_ACCOUNT_NAME_INVALID"), "账户名称须为 1 至 40 个字符");
    }
  }
}
