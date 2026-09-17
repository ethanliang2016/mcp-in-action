# mcp-in-action

《MCP 实战手记》系列（CSDN）配套可运行代码。

> 系列文章：<!-- TODO: 填写你的 CSDN 系列主页链接 -->

## 这是什么

跟着文章走的 MCP 实战代码仓库。**每一篇对应一个目录 + 一个 tag**，checkout 到对应 tag 即可复现文章中的状态。

所有示例均使用 **mock 数据**，不依赖任何真实数据库、MQTT Broker 或物理设备——`clone` 下来就能跑。

## 环境要求

| 模块 | JDK | Spring Boot | Spring AI |
|---|---|---|---|
| `legacy-sse`（旧版） | 17+ | 3.3.x | 1.0.0-M7 |
| `stateless`（新版） | **21+** | 4.0.x | **2.0.1** |

> 建议统一用 **JDK 21** 构建（旧版模块以 release 17 编译）。

## 快速开始

```bash
git clone https://github.com/<your-name>/mcp-in-action.git
cd mcp-in-action
git checkout v03

# 旧版：SSE + 有状态
cd 03-stateless-migration/legacy-sse && mvn spring-boot:run   # 端口 8081

# 新版：STATELESS 无状态
cd ../stateless && mvn spring-boot:run                        # 端口 8082
```

## 第 3 篇：新旧对照

| 维度 | legacy-sse（旧） | stateless（新） |
|---|---|---|
| 传输 | SSE | Streamable HTTP |
| 状态 | 有 session | **无状态** |
| 关键配置 | `sse-message-endpoint: /mcp/message` | `protocol: STATELESS` |
| 依赖 artifactId | `spring-ai-starter-mcp-server-webmvc` | **同一个** |
| 工具定义 | `@Tool` / `@ToolParam` | **不变** |
| 工具注册 | `MethodToolCallbackProvider` | **不变** |
| 补参 | elicitation | 返回结果带 `inputRequired` |

**结论**：迁移 90% 的改动在配置与依赖，业务工具代码基本不用动。

> ⚠️ 无状态服务器**不支持 elicitation / sampling / ping**（服务端不能主动向客户端发请求），也不适用 `ToolContext`。

## 目录结构

| 目录 | 篇目 | tag |
|---|---|---|
| `03-stateless-migration/legacy-sse` | 第 3 篇 · 旧版 | `v03` |
| `03-stateless-migration/stateless` | 第 3 篇 · 新版 | `v03` |
| `02-first-server/` | 第 2 篇 | 规划中 |
| `04-cimd-auth/` | 第 4 篇 | 规划中 |
| `05-mcp-apps/` | 第 5 篇 | 规划中 |
| `06-mcp-gateway/` | 第 6 篇 | 规划中 |
| `07-security/` | 第 7 篇 | 规划中 |

## 说明

- 仓库根 POM 仅作**聚合器**，不继承 `spring-boot-starter-parent`——因为新旧模块使用不同的 Boot 大版本，各模块自行声明 parent
- 旧版模块使用 `1.0.0-M7` 里程碑版，仅为演示旧写法，**勿用于生产**
- `input_required` 语义在示例中用结果对象演示；Spring AI 是否提供一等 API 以官方文档为准
- 代码仅用于技术演示

## 包名规范

统一使用 `com.ethanliang.mcp.*`。

## License

[MIT](./LICENSE)
