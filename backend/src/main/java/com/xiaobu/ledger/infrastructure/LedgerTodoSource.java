package com.xiaobu.ledger.infrastructure;

// 只允许受信的代码注册 SQL。每个查询绑定账本，返回同一组字段，不接收客户端 SQL。
interface LedgerTodoSource {

  String openItemsSql();

  String completedItemsSql();
}
