# 小生活记账簿

小簿是一款支持个人记账和多人共记的生活账本。当前可体验 Vant H5 交互原型。正式客户端采用 uni-app，后端采用 Spring Boot、Foundation 和 MySQL。

| 目录 | 用途 |
| --- | --- |
| [frontend/](./frontend/) | 正式客户端的页面、共享组件、API 和平台适配 |
| [backend/](./backend/) | 业务服务、数据库迁移、助手提示词与测试 |
| [foundation/](./foundation/README.md) | 可独立构建的 Java 公共模块 |
| [docs/product/](./docs/product/) | 产品范围、功能总表与一期 PRD |
| [docs/development/](./docs/development/) | 开发工作规范、本地运行与接力记录 |
| [docs/ui-design/vant-pilot/](./docs/ui-design/vant-pilot/README.md) | 可在本地运行的交互原型 |
| [docs/specs/](./docs/specs/) · [docs/design/](./docs/design/) | 功能规格与技术设计 |
| [docs/ai/](./docs/ai/) · [docs/verification/](./docs/verification/) | AI 评测样例与版本验收记录 |

## 项目入口

- 查看产品范围：[功能总表](docs/product/产品功能总表.md)、[一期 PRD](docs/product/微信小程序一期PRD.md)。
- 了解首条流程：[双人共记规格](docs/specs/双人共记首条流程规格.md)、[技术方案与迭代计划](docs/design/双人共记一期技术方案与迭代计划.md)。
- 开始本地工作：[接力与预览说明](docs/development/本地继续开发.md)、[项目约定](./AGENTS.md)、[开发工作规范](docs/development/新系统从立项到交付工作规范.md)。

## 提交与格式检查

后端分层、命名、JPA 与验收规则见[后端编码规范](docs/development/后端编码规范.md)。

首次克隆后在项目根目录运行 `npm ci --prefix frontend` 和 `git config --local core.hooksPath .githooks`，启用当前仓库的 Git 钩子。推送检查需要 Node.js、JDK 和 Maven，Java 版本见 [Foundation 说明](foundation/README.md)。

- 提交标题采用 `type(scope): 简述`，范围可省略。例如 `feat(ledger): 支持关联退款流水`。允许的类型见 [commit-msg](.githooks/commit-msg)。
- 提交时检查暂存改动的空白错误和提交标题。推送时运行前端 Prettier 和 Foundation 的 Spotless、Checkstyle。
- 根目录运行 `npm --prefix frontend run format:check` 检查正式前端和 Vant 原型，`npm --prefix frontend run format:write` 自动排版。在 `foundation/` 运行 `mvn spotless:apply` 修正 Java 排版，`mvn spotless:check checkstyle:check` 复查。
