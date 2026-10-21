# 12-toolbox · MCP 企业级工具包治理（篇12 配套）

轻量 Toolbox 注册表与治理层：`com.ethanliang.mcp.toolbox`。演示复合键、两层权限拦截、版本共存+灰度、checksum、弃用状态机、tenantId 预埋、可观测看板。两种后端可切换：内存（默认）与 Redis（多实例一致）。

## 内存后端（默认，无需 Redis）

```bash
mvn spring-boot:run
# 访问 GET http://localhost:8088/api/toolboxes
```

启动即打印"治理层实测演练"日志（注册/发现层过滤/调用层拦截/灰度/checksum/弃用状态机/tenantId/看板）。

## Redis 后端（多实例一致）

```bash
docker run -d --name tb-redis -p 6379:6379 redis:7

# 实例A（种子：写入共享状态）
java -jar target/toolbox-mcp-1.0.0-SNAPSHOT.jar --server.port=8088 \
     --spring.profiles.active=redis --toolbox.demo.seed=true

# 实例B（非种子：只读 A 写入的共享状态，验证多实例一致）
java -jar target/toolbox-mcp-1.0.0-SNAPSHOT.jar --server.port=8089 \
     --spring.profiles.active=redis --toolbox.demo.seed=false

# 两实例 GET /api/toolboxes 输出逐字节一致；灰度与弃用状态跨实例同步
```

存储：hash `tb:{ownerTeamId}:{name}`(field=version)、`tb:gray:{id}`(灰度)、`tb:default:{id}`(稳定版)；注册/弃用发 pub/sub 事件 `toolbox-events`。

## 治理能力清单

- 复合键 `ToolboxId(ownerTeamId, name)`：跨团队同名不冲突
- 两层权限：`list()` 发现层过滤 + `resolve()` 调用层拦截 `requiredScope`
- 版本共存 + caller 级灰度：`setGrayRoute(id, callerId, version)`
- checksum = sha256(方法签名 | 参数schema | 返回值schema)
- 弃用状态机：DEPRECATED →(宽限≥14天)→ REMOVED(stub 30天，resolve 返回迁移指引)
- tenantId 预埋：`list()`/`resolve()` 已写隔离过滤，篇13 多租户直接消费
- 可观测：`GET /api/toolboxes` 返回脱敏 `ToolboxView`（含 successRate/p99Millis/errorRate）
