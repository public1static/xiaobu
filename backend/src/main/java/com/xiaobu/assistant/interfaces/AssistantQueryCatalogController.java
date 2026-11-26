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

  // 目录沿用实际数据源的账本读取权限。
  AssistantQueryCatalogController(PersonalLedgerApplicationService ledgers) {
    this.ledgers = ledgers;
  }

  // 客户端通过逻辑名发现可查字段；具体数据入口仍逐次鉴权。
  @GetMapping("/api/v1/ledgers/{ledgerId}/assistant/query-catalog")
  public ApiResponse<List<AssistantDataset.Definition>> listDatasets(
      @PathVariable String ledgerId, HttpServletRequest request) {
    var actor = LedgerSessionContext.require(request);
    ledgers.requireReadAccess(ledgerId, actor.actorId());
    return ApiResponse.success(
        AssistantDataset.catalog(ledgers.isOwner(ledgerId, actor.actorId())));
  }
}
