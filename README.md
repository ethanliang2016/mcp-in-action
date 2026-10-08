# mcp-in-action

《MCP 实战手记》系列（CSDN）配套可运行代码。

> 系列文章：[CSDN 主页 · 《MCP 实战手记》](https://blog.csdn.net/weixin_39885962)

## 这是什么

跟着文章走的 MCP 实战代码仓库。**每一篇对应一个目录 + 一个 tag**，checkout 到对应 tag 即可复现文章中的状态。

所有示例均使用 **mock 数据**，不依赖任何真实数据库、MQTT Broker 或物理设备——`clone` 下来就能跑。

## 环境要求

| 模块 | JDK | Spring Boot | Spring AI |
|---|---|---|---|
| `legacy-sse`（旧版） | 17+ | 3.3.x | 1.0.0-M7 |
| `first-server`（第 2 篇） | 17+ | 3.3.x | 1.0.0-M7 |
| `stateless`（新版） | **21+** | 4.0.x | **2.0.1** |
| `mcp-apps`（第 5 篇） | **21+** | 4.0.x | **2.0.1** |
| `mcp-gateway`（第 6 篇） | **21+** | 4.0.x | 不用 Spring AI，用 **MCP Java SDK 2.0.0** |
| `store-ops`（第 8 篇） | **21+** | 4.0.x | **2.0.1** |
| `store-ops-security`（第 9 篇） | **21+** | 4.0.x | **2.0.1**（+ Spring Security / Bucket4j） |
| `store-ha`（第 10 篇） | **21+** | 4.0.x | **2.0.1**（+ Resilience4j / Redis 哨兵 / Nginx） |

> 建议统一用 **JDK 21** 构建（旧版模块以 release 17 编译）。

## 快速开始

```bash
# 国内推荐（Gitee，秒 clone）
git clone https://gitee.com/ethanliang2016/mcp-in-action.git
# GitHub 镜像（海外网络）
# git clone https://github.com/ethanliang2016/mcp-in-action.git
cd mcp-in-action
# 第 2 篇：跑通第一个 MCP Server（SSE + 有状态）
git checkout v02
cd 02-first-server && mvn spring-boot:run                     # 端口 8080

# 第 3 篇：新旧对照
git checkout v03
cd 03-stateless-migration/legacy-sse && mvn spring-boot:run   # 端口 8081（旧版 SSE + 有状态）
cd ../stateless && mvn spring-boot:run                        # 端口 8082（新版 STATELESS 无状态）

# 第 5 篇：MCP Apps（工具返回一块可交互界面）
git checkout v05
cd 05-mcp-apps && mvn spring-boot:run                        # 端口 8085

# 第 6 篇：自建 MCP 网关（多后端聚合 + 命名空间隔离）
git checkout v06
cd 05-mcp-apps && mvn package -DskipTests
java -jar target/mcp-apps-1.0.0-SNAPSHOT.jar --server.port=8085   # site-a 后端
java -jar target/mcp-apps-1.0.0-SNAPSHOT.jar --server.port=8087   # site-b 后端（同代码换端口）
cd ../06-mcp-gateway && mvn package -DskipTests
java -jar target/mcp-gateway-1.0.0-SNAPSHOT.jar                   # 网关，端口 8086

# 第 8 篇：门店运营助手——补参 / 会话数据外置 / 客户端兼容
git checkout v08
cd 08-store-ops && mvn spring-boot:run                            # 端口 8088

# 第 10 篇：高可用——多实例 / 熔断降级 / Redis 哨兵
git checkout v10
cd 10-store-ha && mvn -q package -DskipTests
docker compose build && docker compose up                         # Nginx :8080 -> 3× app :8088
```

> 第 2/3/5/6/8 篇：`cd` 到模块目录直接 `mvn spring-boot:run`（端口见各篇小节）。
> 第 10 篇：默认是**容器多实例**形态，单机直连见下方第 10 篇小节。

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

## 第 5 篇：MCP Apps（工具返回界面）

工具在**自己的元数据**里写 `_meta.ui.resourceUri` 指向 `ui://` 界面资源，宿主取资源后在沙箱 iframe 渲染；
不支持该扩展的宿主拿文本回退（`content[0].text`），工具照样可用。

> Spring AI 的 `ToolDefinition` 只有 name / description / inputSchema，**没有 meta**，
> 所以绑定要走 `@McpTool(metaProvider = ...)`，`@Tool` 那条路挂不上。

## 第 6 篇：自建 MCP 网关

网关对下游用 MCP client 拉各后端的工具，加 `前缀__原名` 做命名空间隔离；对上游用一个无状态 MCP Server 统一暴露，`tools/call` 按前缀路由转发。

工具是运行时拉来的，注解声明不出来，所以这一篇**不用** Spring AI 的注解式 starter，直接走底层 MCP Java SDK（`io.modelcontextprotocol.sdk:mcp-core`）。

> 两个要点：
> - **后端必须先于网关启动**——启动时连不上的后端会被跳过（记一条 WARN），本次不暴露它的工具
> - **工具表是启动时的快照**——后端挂掉后 `tools/list` 照常列出它的工具，网关不探活、不刷新

已知未覆盖：`resources/list` 与 `resources/read` 未代理，因此 `_meta.ui` 透传之后 MCP Apps 的界面在网关后面会断链。

## 第 10 篇：MCP Server 高可用

在篇八（无状态化）基础上叠 HA：**工具代码不用改**，改的是部署形态与容错。实测拓扑：

```
客户端 → Nginx :8080（加权轮询 3:2:1 + 被动健康检查）
            ├─ store-ha-app-1 :8088  ×3 实例
            └─ Redis：1 主 2 从 + 3 哨兵（mymaster 自动故障转移）
```

| 手段 | 作用 | 关键配置/代码 |
|---|---|---|
| 多实例 + LB | 任一实例挂了流量自动切走 | `nginx/nginx.conf`（`weight` / `max_fails` / `fail_timeout`） |
| 健康检查 | LB 与 K8s 探针据此摘除坏实例 | `/actuator/health`（`liveness` / `readiness`） |
| 熔断降级 | 下游慢也算失败，打开后返回"暂不可用"，不拖垮其他工具 | `StoreService#getDeviceStatus`（Resilience4j） |
| 软降级 | 销售查询走 Redis 缓存，Redis 挂了降级返回源数据 | `StoreService#getStoreSales` |
| 状态/规则外置 | 上下文与补货规则放 Redis，多实例读到同一份 | `RedisContextStore` / `rule:restock-multiplier` |

```bash
cd 10-store-ha
mvn -q package -DskipTests && docker compose build && docker compose up

# 实测脚本（走 Nginx :8080 验证完整链路）
pip install mcp
python verify/ha_check.py poll       # 轮询：看请求是否按权重分散到 app-1/2/3
python verify/ha_check.py normal     # 正常路径
python verify/ha_check.py circuit    # 熔断演练（需先 HA_DEVICE_LATENCY_MS=5000 docker compose up）
python verify/ha_check.py sentinel   # 哨兵切换后仍能读上下文

# 故障演练：模拟下游慢 5s → 慢调用率超 50% → 熔断打开
HA_DEVICE_LATENCY_MS=5000 docker compose up
```

> 四个坑（都是实测踩出来的）：
> 1. Nginx 必须显式 `proxy_set_header Host $host`，否则 upstream 名 `mcp_backend` 带下划线会被 Tomcat 判 400
> 2. 无状态化之后若把上下文放本实例内存，多实例下 `save_context`/`load_context` 落在不同实例就读不到——HA 最常见的翻车点
> 3. 哨兵要写配置文件持久化状态，宿主机挂载的 `sentinel*.conf` 属主不对会一直报 `Permission denied`，compose 里已改为挂到 `/mnt` 再拷进 `/data` 运行
> 4. 主库故障演练要用 `docker compose pause redis-master` 而不是 `stop`：`stop` 会让 DNS 别名注销，哨兵解析失败进入 TILT 模式而**不发起 failover**（实测 sdown 被拖到 30s 后且始终不切换）；`pause` 下 5s sdown、7s 完成 switch-master
>
> 本篇样例未叠加篇九的鉴权/限流/审计，生产请把两层合并。

📋 **完整实测过程**（逐条命令、原始输出、问题定位与修复）：[`10-store-ha/verify/实测记录.md`](10-store-ha/verify/实测记录.md)

## 目录结构

| 目录 | 篇目 | tag |
|---|---|---|
| `03-stateless-migration/legacy-sse` | 第 3 篇 · 旧版 | `v03` |
| `03-stateless-migration/stateless` | 第 3 篇 · 新版 | `v03` |
| `02-first-server/` | 第 2 篇 | `v02` |
| `04-cimd-auth/` | 第 4 篇 | `v04` |
| `05-mcp-apps/` | 第 5 篇 | `v05` |
| `06-mcp-gateway/` | 第 6 篇 | `v06` |
| `07-mcp-gateway-auth/` | 第 7 篇 | `v07` |
| `08-store-ops/` | 第 8 篇 · 门店运营助手（补参/会话外置/兼容） | `v08` |
| `09-store-ops-security/` | 第 9 篇 · 6 层安全防护（mTLS/CIMD/RBAC/限流/审计） | `v09` |
| `10-store-ha/` | 第 10 篇 · 门店运营助手高可用（多实例/熔断/软降级/Redis 哨兵） | `v10` |

## 说明

- 仓库根 POM 仅作**聚合器**，不继承 `spring-boot-starter-parent`——因为新旧模块使用不同的 Boot 大版本，各模块自行声明 parent
- 旧版模块使用 `1.0.0-M7` 里程碑版，仅为演示旧写法，**勿用于生产**
- `input_required` 语义在示例中用结果对象演示；Spring AI 是否提供一等 API 以官方文档为准
- 代码仅用于技术演示

## 包名规范

统一使用 `com.ethanliang.mcp.*`。

## License

[MIT](./LICENSE)
