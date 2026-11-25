# Foundation

小生活记账簿的公共 Java 库。本目录可独立构建，源自 IAM 提交 `72826f9c728e9945d7f5e18da5e3d6293aa754a9` 的 `backend/foundation`。13 个子模块保留原包名、源码、资源及测试。本目录的父 POM 接管版本与插件配置，源码维护入口在此处。

## 本地构建与消费

在本目录用 JDK 21 运行 `mvn clean install`。执行单元测试、已声明的集成测试、Spotless、Checkstyle 与覆盖率报告，然后将父 POM 和各子模块 JAR 安装到本机 Maven 仓库。库面向 Java 21 字节码；新服务在 JDK 27 上消费。产物使用独立版本 `0.1.0-xiaobu.1`。

业务服务导入 Spring Boot 4.2.0 BOM，按需声明依赖。例如：

```xml
<dependency>
  <groupId>com.aegisid</groupId>
  <artifactId>foundation-common-web</artifactId>
  <version>0.1.0-xiaobu.1</version>
</dependency>
```

## 维护边界

shared-kernel 提供通用类型与契约。web、data、security、cache、audit、events、excel、i18n、observability、tenant 及 human-verification 按能力组织，test-support 供测试使用。业务用例、成员权限和助手工具归后端业务上下文。扩展或升级公共模块后应运行整个反应堆，并从独立消费工程验证 Maven 解析。版本基线与项目布局见[一期技术方案](../docs/design/双人共记一期技术方案与迭代计划.md)。
