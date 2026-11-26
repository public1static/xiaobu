package com.xiaobu.assistant.application;

import com.xiaobu.ledger.domain.TransactionDirection;
import java.time.LocalDate;

// 日期均为北京时间的闭区间；目录名称在服务端解析，模型无需接触账本 ID。
public record AssistantQueryPlan(
    LocalDate from,
    LocalDate through,
    String category,
    String account,
    TransactionDirection direction,
    boolean recordedByMe,
    String keyword) {}
