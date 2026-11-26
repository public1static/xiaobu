package com.xiaobu.assistant.application;

import java.util.Arrays;
import java.util.List;

public enum AssistantDataset {
  statement(
      "流水",
      "transactions",
      true,
      "CATEGORY ACCOUNT RECORDER DAY WEEK MONTH",
      "incomeCents grossExpenseCents refundCents netExpenseCents entryCount"),
  accounts(
      "账户",
      "accounts",
      false,
      "name",
      "openingBalanceCents recordedBalanceCents balanceCents entryCount"),
  categories("分类", "categories", false, "name direction status", "entryCount"),
  transfers("账户互转", "account-transfers", true, "source target day month", "amountCents entryCount"),
  giftActivities(
      "礼金往来",
      "gifts/activity",
      true,
      "person book direction day month",
      "incomeCents expenseCents amountCents entryCount"),
  giftBooks("礼簿", "gifts/books", false, "name direction status", "entryCount"),
  giftContacts("礼金亲友", "gifts/contacts", false, "name distinction", "entryCount"),
  members("有效成员", "members", false, "member role", "entryCount");

  // 描述公开目录、模型协议和执行器共同使用的能力。
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

  AssistantDataset(String label, String resource, boolean dated, String groups, String metrics) {
    var dimensions = List.of(groups.split(" "));
    var filters =
        name().equals("statement")
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
            name(),
            label,
            resource,
            dated,
            filters,
            dimensions,
            List.of(metrics.split(" ")),
            dated
                ? (name().equals("statement")
                    ? List.of("total", "rank", "compare")
                    : List.of("list", "total", "rank", "compare"))
                : List.of("list", "total", "rank"));
  }

  // 仅在账本所有者的目录中列出成员数据。
  public static List<Definition> catalog(boolean owner) {
    return Arrays.stream(values())
        .filter(d -> owner || d != members)
        .map(AssistantDataset::definition)
        .toList();
  }

  // 返回当前数据集支持的查询参数。
  public Definition definition() {
    return definition;
  }
}
