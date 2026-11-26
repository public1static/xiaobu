package com.xiaobu.assistant.interfaces;

import com.aegisid.foundation.web.api.ApiResponse;
import com.xiaobu.assistant.application.AssistantDataset;
import com.xiaobu.identity.interfaces.LedgerSessionContext;
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

  // 将内部目录定义转换为稳定的 HTTP 契约。
  private final AssistantWebMapper mapper;

  // 目录沿用实际数据源的账本读取权限。
  AssistantQueryCatalogController(
      PersonalLedgerApplicationService ledgers, AssistantWebMapper mapper) {
    this.mapper = mapper;
    this.ledgers = ledgers;
  }

  // 客户端通过逻辑名发现可查字段；具体数据入口仍逐次鉴权。
  @GetMapping("/api/v1/ledgers/{ledgerId}/assistant/query-catalog")
  public ApiResponse<List<DatasetResponse>> listDatasets(
      @PathVariable String ledgerId, HttpServletRequest request) {
    var actor = LedgerSessionContext.require(request);
    ledgers.requireReadAccess(ledgerId, actor.actorId());
    return ApiResponse.success(
        mapper.toDatasetResponses(
            AssistantDataset.catalog(ledgers.isOwner(ledgerId, actor.actorId()))));
  }

  record DatasetResponse(
      String name,
      String description,
      String resource,
      boolean dated,
      List<String> filters,
      List<String> groupBy,
      List<String> metrics,
      List<String> operations) {}
}
