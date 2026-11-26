package com.xiaobu.assistant.application;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public enum AssistantDataset {
  STATEMENT(
      "statement",
      "流水",
      "transactions",
      true,
      "CATEGORY ACCOUNT RECORDER DAY WEEK MONTH",
      "incomeCents grossExpenseCents refundCents netExpenseCents entryCount"),
  ACCOUNTS(
      "accounts",
      "账户",
      "accounts",
      false,
      "name",
      "openingBalanceCents recordedBalanceCents balanceCents entryCount"),
  CATEGORIES("categories", "分类", "categories", false, "name direction status", "entryCount"),
  TRANSFERS(
      "transfers",
      "账户互转",
      "account-transfers",
      true,
      "source target day month",
      "amountCents entryCount"),
  GIFT_ACTIVITIES(
      "giftActivities",
      "礼金往来",
      "gifts/activity",
      true,
      "person book direction day month",
      "incomeCents expenseCents amountCents entryCount"),
  GIFT_BOOKS("giftBooks", "礼簿", "gifts/books", false, "name direction status", "entryCount"),
  GIFT_CONTACTS("giftContacts", "礼金亲友", "gifts/contacts", false, "name distinction", "entryCount"),
  MEMBERS("members", "有效成员", "members", false, "member role", "entryCount");

  /**
   * 描述公开目录、模型协议和执行器共同使用的能力。
   *
   * @param operations 当前数据集允许的操作编码
   */
  public record Definition(
      String name,
      String description,
      String resource,
      boolean dated,
      List<String> filters,
      List<String> groupBy,
      List<String> metrics,
      List<String> operations) {}

  // 同一份能力说明用于发布目录和校验计划。
  private final Definition definition;

  AssistantDataset(
      String code, String label, String resource, boolean dated, String groups, String metrics) {
    var dimensions = List.of(groups.split(" "));
    var filters =
        code.equals("statement")
            ? List.of(
                "categoryCode",
                "accountId",
                "unassignedAccount",
                "recorderId",
                "direction",
                "recordedByMe",
                "from",
                "through",
                "keyword")
            : dimensions;
    definition =
        new Definition(
            code,
            label,
            resource,
            dated,
            filters,
            dimensions,
            List.of(metrics.split(" ")),
            dated
                ? (code.equals("statement")
                    ? List.of("total", "rank", "compare")
                    : List.of("list", "total", "rank", "compare"))
                : List.of("list", "total", "rank"));
  }

  // 按稳定协议编码查找本次身份可见的数据集。
  public static Optional<AssistantDataset> findVisible(String code, boolean owner) {
    return visibleDatasets(owner).filter(data -> data.definition.name().equals(code)).findFirst();
  }

  // 成员目录依照成员管理入口的所有者权限开放。
  private static Stream<AssistantDataset> visibleDatasets(boolean owner) {
    return Arrays.stream(values()).filter(data -> owner || data != MEMBERS);
  }

  // 仅在账本所有者的目录中列出成员数据。
  public static List<Definition> catalog(boolean owner) {
    return visibleDatasets(owner).map(AssistantDataset::definition).toList();
  }

  // 返回当前数据集支持的查询参数。
  public Definition definition() {
    return definition;
  }
}
