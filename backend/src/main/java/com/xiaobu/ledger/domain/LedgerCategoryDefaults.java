package com.xiaobu.ledger.domain;

import java.util.ArrayList;
import java.util.List;

public enum LedgerCategoryDefaults {
  DINING("餐饮"),
  GROCERIES("买菜"),
  TRANSIT("交通"),
  DAILY("日用品"),
  HOUSING("住房"),
  SHOPPING("购物"),
  ENTERTAINMENT("娱乐"),
  TRAVEL("旅行"),
  HEALTH("医疗"),
  EDUCATION("教育"),
  OTHER("其他支出"),
  SALARY("工资", TransactionDirection.INCOME),
  BONUS("奖金", TransactionDirection.INCOME),
  SIDE_INCOME("兼职", TransactionDirection.INCOME),
  OTHER_INCOME("其他收入", TransactionDirection.INCOME);

  private final String label;
  private final TransactionDirection direction;

  LedgerCategoryDefaults(String label) {
    this(label, TransactionDirection.EXPENSE);
  }

  LedgerCategoryDefaults(String label, TransactionDirection direction) {
    this.label = label;
    this.direction = direction;
  }

  public static List<LedgerCategory> forLedger(String ledgerId) {
    List<LedgerCategory> catalog = new ArrayList<>();
    int expenseOrder = 0;
    int incomeOrder = 0;
    for (var item : values()) {
      int order = item.direction == TransactionDirection.EXPENSE ? expenseOrder++ : incomeOrder++;
      catalog.add(
          new LedgerCategory(null, ledgerId, item.name(), item.direction, item.label, order, true));
    }
    return List.copyOf(catalog);
  }

  public static boolean matchesDefault(String code, TransactionDirection direction) {
    if (code == null) return false;
    try {
      return valueOf(code).direction == direction;
    } catch (IllegalArgumentException ex) {
      return false;
    }
  }

  public static boolean isCustomCode(String code) {
    return code != null && code.matches("CUS_[A-F0-9]{16}");
  }
}
