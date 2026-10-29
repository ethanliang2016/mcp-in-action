# 13-multitenant · MCP 多租户隔离

> 《MCP 实战手记（十三）：MCP 多租户隔离，一个网关服务多个团队》配套代码。
> 技术基线：Spring Boot 4.0.0 + Java 21 + Redis 7（与篇六网关、篇十二同基线）。

一个网关服务多个团队，把"多租户"这层补在已有网关上，落地**四件套**：

| 件套 | 做法 | 代码 |
|---|---|---|
| 1 租户路由 | 请求进来先认租户：`X-Api-Key` 查表 / `X-Tenant-Id` 覆盖；缺失或非法直接 401 | `TenantFilter`、`TenantStore` |
| 2 凭证隔离 | 每租户独立 Key，`tenant:key:{hash}` → tenantId 存 Redis，删键即吊销、无需重启 | `TenantStore` |
| 3 配额管理 | Redis + Lua 原子自增 + TTL（固定窗口），跨副本一致，超限 429；Redis 不可用时按租户 `fail-closed` / `fail-open` 分级降级 | `QuotaService` |
| 4 目录/数据隔离 | `tools/list` 只返回本租户 `allowed_tools`（看不见就点不到）；日志 MDC 全链路带 `tenant`；缓存键统一 `tenant:{id}:` 前缀 | `ToolController`、`TenantFilter` |

## 目录结构

```
13-multitenant/
├── pom.xml
├── Dockerfile
├── docker-compose.yml          # redis + gw-1(8080) + gw-2(8081)
├── verify-multitenant.sh       # 四件套一键验证
└── src/main/java/com/ethanliang/mcp/multitenant/
    ├── MultitenantApplication.java
    ├── TenantProperties.java       # 租户定义（allowed_tools / quota-limit / failover）
    ├── TenantContext.java          # 租户上下文（ThreadLocal）
    ├── TenantStore.java            # 凭证：Key→tenant 映射，种子写入 Redis
    ├── QuotaService.java           # 配额：Lua 固定窗口 + 按租户降级
    ├── TenantFilter.java           # 传输层拦截：401 / 429 / MDC
    └── ToolController.java         # /tools/list（目录过滤）· POST /mcp（调用鉴权）
```

## 快速开始

```bash
mvn clean package -DskipTests      # 产出 target/multitenant-mcp.jar
docker compose up -d --build       # redis + 两个无状态副本
./verify-multitenant.sh            # 跑四件套验证
docker compose down
```

> 端口冲突：若本机 8080 已被占用，改 `docker-compose.yml` 的 ports 为 `18080:8080` / `18081:8080`，
> 并用 `GW1=http://localhost:18080 GW2=http://localhost:18081 ./verify-multitenant.sh` 运行。

## 实测输出（本机已验证）

环境：Windows 11 + JDK 21.0.11 + Maven 3.9.15，两个副本 `18080/18081` + Redis 7 容器（`mcp13-redis`）。

```
==== 1) 租户路由 ====
  no-key  -> 401        bad-key -> 401        store -> 200

==== 2) 目录隔离 ====
  store   目录: {"tools":["get_store_sales","get_inventory"],"tenant":"store"}
  finance 目录: {"tools":["gen_finance_report"],"tenant":"finance"}
  PASS: 门店目录已过滤（不含 gen_finance_report）

==== 3) 凭证隔离 ====
  supply->get_store_sales -> 403

==== 4) 配额跨副本一致性（store limit=20 / 窗口60s）====
  GW1 hit  1~20 -> 200
  GW1 hit 21~23 -> 429
  GW2 store     -> 429          # 同一租户，计数在 Redis，换副本同样被限
  GW2 supply    -> 200          # 未超限租户不受影响（证明是租户级而非全局限流）

==== 5) 故障演练（Redis 不可用，按租户分级）====
  finance(closed) -> 429        # 核心租户全拒，保隔离
  supply(open)    -> 200        # 长尾租户放行，保可用
  恢复 Redis 后   -> 200        # 自愈，无需重启
```

## 实现说明（对应正文的"待实测"）

正文假设的 `ServerTransportInterceptor` 在 **MCP Java SDK 2.0.0 中并不存在**——
篇六网关（`06-mcp-gateway`）实际用的是 `HttpServletStatelessServerTransport` + `McpServer.sync(...)`
（见 `GatewayServerConfiguration`），SDK 暴露的是 transport provider 而非拦截器接口。

因此按正文预案"若为 provider 装饰器则逻辑等价迁移"，把拦截落在 **Servlet Filter**：
位置同样在传输层，逻辑完全等价（解析租户 → 拒绝非法 → 配额计数 → 写入上下文/日志）。
若后续 SDK 提供装饰器式扩展点，只需把 `TenantFilter` 的四步搬过去，业务代码零改动。

## 本机已验证 vs 待生产验证

- **本机已验证**：租户路由 401、目录按租户过滤、跨租户调用 403、固定窗口配额跨副本一致（429）、
  Redis 故障下按租户 `fail-closed`/`fail-open` 分级降级与恢复自愈。
- **待生产验证**：真实 IdP 签发带 `tenant` claim 的 Token（本文用 Key 映射演示）、
  mTLS 传输加密（依赖篇九 L1，本机未跑真实证书体系）、
  实例级强隔离的部署成本、双副本下的配额吞吐基线（P95）压测。
