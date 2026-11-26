package com.xiaobu.assistant.interfaces;

import com.xiaobu.assistant.application.AssistantConversationApplicationService;
import com.xiaobu.assistant.application.AssistantConversationApplicationService.Message;
import com.xiaobu.assistant.application.AssistantConversationRepository.Conversation;
import com.xiaobu.assistant.application.AssistantDataset;
import com.xiaobu.assistant.application.AssistantExpenseDraftRepository.Draft;
import com.xiaobu.assistant.application.AssistantExpenseDraftRepository.Fields;
import com.xiaobu.assistant.application.AssistantQueryPlan;
import com.xiaobu.ledger.interfaces.LedgerTransactionWebMapper;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    uses = LedgerTransactionWebMapper.class,
    unmappedTargetPolicy = ReportingPolicy.ERROR,
    disableSubMappingMethodsGeneration = true)
interface AssistantWebMapper {
  // 只公开目录协议声明的筛选、指标和操作。
  List<AssistantQueryCatalogController.DatasetResponse> toDatasetResponses(
      List<AssistantDataset.Definition> datasets);

  // 保留单个数据集在公开目录中的稳定编码。
  AssistantQueryCatalogController.DatasetResponse toResponse(AssistantDataset.Definition dataset);

  // 聊天列表只返回会话编号、标题和版本。
  AssistantConversationController.ConversationResponse toResponse(Conversation conversation);

  // 保持会话列表的数据库排序。
  List<AssistantConversationController.ConversationResponse> toConversationResponses(
      List<Conversation> conversations);

  // 复用流水页映射，历史回答维持原有 JSON 结构。
  AssistantMessageResponse toResponse(Message message);

  // 保留历史页内的消息顺序。
  List<AssistantMessageResponse> toMessageResponses(List<Message> messages);

  // 将回答内的来源、查询范围和候选项一并输出。
  AssistantMessageResponse.AnswerResponse toResponse(
      AssistantConversationApplicationService.Answer answer);

  // 保留所选账本对象和当时的查询条件。
  AssistantMessageResponse.ScopeResponse toResponse(
      AssistantConversationApplicationService.Scope scope);

  // 返回澄清选项及其对应的计划。
  AssistantMessageResponse.ChoiceResponse toResponse(
      AssistantConversationApplicationService.Choice choice);

  // 兼容普通流水查询和带分析条件的计划。
  AssistantMessageResponse.QueryPlanResponse toResponse(AssistantQueryPlan plan);

  // 保留分析操作、指标和比较基期。
  AssistantMessageResponse.AnalysisResponse toResponse(AssistantQueryPlan.Analysis analysis);

  // 草稿输入和返回值各自转换，服务端状态不参与输入。
  Fields toFields(AssistantExpenseDraftController.DraftFieldsRequest request);

  // 输出最新版本及已确认的流水引用。
  AssistantExpenseDraftController.DraftResponse toResponse(Draft draft);

  // 将待补金额和可选账户原样返回。
  AssistantExpenseDraftController.DraftFieldsResponse toResponse(Fields fields);
}
