package com.xiaobu.assistant.interfaces;

import com.aegisid.foundation.web.api.ApiResponse;
import com.xiaobu.identity.interfaces.LedgerSessionContext;
import com.xiaobu.ledger.application.ListLedgerTransactionsQuery;
import com.xiaobu.ledger.application.PersonalLedgerApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("local-expense | wechat-preview")
public class AssistantQueryCatalogController {
  private final PersonalLedgerApplicationService ledgers;

  // 目录沿用实际数据源的账本读取权限。
  AssistantQueryCatalogController(PersonalLedgerApplicationService ledgers) {
    this.ledgers = ledgers;
  }

  // 客户端通过逻辑名发现可查字段；具体数据入口仍逐次鉴权。
  @GetMapping("/api/v1/ledgers/{ledgerId}/assistant/query-catalog")
  public ApiResponse<List<DatasetResponse>> listDatasets(
      @PathVariable String ledgerId, HttpServletRequest request) {
    var actor = LedgerSessionContext.require(request);
    ledgers.requireReadAccess(ledgerId, actor.actorId());
    return ApiResponse.success(
        List.of(
            new DatasetResponse(
                "statement",
                "一笔收支或一笔已到账退款",
                "transactions",
                List.of(
                    "categoryCode",
                    "accountId",
                    "unassignedAccount",
                    "recorderId",
                    "direction",
                    "recordedByMe",
                    "from",
                    "through",
                    "keyword"),
                java.util.Arrays.stream(ListLedgerTransactionsQuery.GroupBy.values())
                    .map(Enum::name)
                    .toList(),
                List.of(
                    "incomeCents",
                    "grossExpenseCents",
                    "refundCents",
                    "netExpenseCents",
                    "entryCount")),
            new DatasetResponse(
                "accounts",
                "当前账本账户及当前余额",
                "accounts",
                List.of(),
                List.of(),
                List.of("openingBalanceCents", "recordedBalanceCents", "balanceCents")),
            new DatasetResponse(
                "categories", "当前账本分类及启停状态", "categories", List.of(), List.of(), List.of())));
  }

  record DatasetResponse(
      String name,
      String description,
      String resource,
      List<String> filters,
      List<String> groupBy,
      List<String> metrics) {}
}
