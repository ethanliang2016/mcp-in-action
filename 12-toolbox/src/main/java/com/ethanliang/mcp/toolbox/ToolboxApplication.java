package com.ethanliang.mcp.toolbox;

import com.ethanliang.mcp.toolbox.model.Checksums;
import com.ethanliang.mcp.toolbox.model.ToolboxDefinition;
import com.ethanliang.mcp.toolbox.model.ToolboxId;
import com.ethanliang.mcp.toolbox.model.ToolboxStatus;
import com.ethanliang.mcp.toolbox.registry.ToolboxRegistry;
import com.ethanliang.mcp.toolbox.web.ToolboxController;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ConfigurableApplicationContext;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 第 12 篇 · MCP Toolboxes 治理层演示。
 *
 * 启动即跑一遍"实测演练"，打印真实日志（注册/发现层过滤/调用层拦截/灰度/弃用状态机/checksum/tenantId），
 * 服务持续运行；读者可访问 GET http://localhost:8088/api/toolboxes。
 * 后端可切换：默认内存（InMemoryToolboxRegistry）；加 --spring.profiles.active=redis 走 Redis（多实例一致）。
 */
@SpringBootApplication
public class ToolboxApplication implements CommandLineRunner {

    private final ToolboxRegistry registry;
    private final ToolboxController controller;
    private final boolean seed;

    public ToolboxApplication(ToolboxRegistry registry, ToolboxController controller,
                              @Value("${toolbox.demo.seed:true}") boolean seed) {
        this.registry = registry;
        this.controller = controller;
        this.seed = seed;
    }

    public static void main(String[] args) {
        // 启动常驻服务（端口 8088）；实测演练在 CommandLineRunner 中打印，服务持续运行。
        // 一键启动 demo：mvn spring-boot:run 后访问 GET http://localhost:8088/api/toolboxes
        SpringApplication.run(ToolboxApplication.class, args);
    }

    @Override
    public void run(String... args) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.of("UTC"));
        log("=== 篇12 治理层实测演练 ===");

        // 1) 注册：同一团队同名工具多版本并存 + 复合键避免跨团队冲突（仅种子实例写；第二实例读共享 Redis）
        ToolboxId retailReport = ToolboxId.of("retail-cn", "get_report");
        ToolboxId retailStore = ToolboxId.of("retail-cn", "query_store");
        ToolboxId logiReport = ToolboxId.of("logistics", "get_report");
        if (seed) {
            registry.register(mk(retailReport, "1.0.0", List.of("report"), "alice", "report:gen",
                    "https://docs/internal/get_report", null,
                    "get_report(storeId)", "{\"storeId\":\"string\"}", "{\"text\":\"string\"}"));
            registry.register(mk(retailReport, "2.0.0", List.of("report", "beta"), "alice", "report:gen",
                    "https://docs/internal/get_report", null,
                    "get_report(storeId, withForecast)", "{\"storeId\":\"string\",\"withForecast\":\"bool\"}", "{\"text\":\"string\",\"forecast\":\"number\"}"));
            registry.register(mk(retailStore, "1.0.0", List.of("store"), "bob", "store:read",
                    "https://docs/internal/query_store", null,
                    "query_store(storeId)", "{\"storeId\":\"string\"}", "{\"text\":\"string\"}"));
            registry.register(mk(logiReport, "1.0.0", List.of("report"), "carol", "report:gen",
                    "https://docs/internal/get_report", null,
                    "get_report(storeId)", "{\"storeId\":\"string\"}", "{\"text\":\"string\"}"));
            log("1) 注册完成：retail-cn/get_report 有 v1.0.0 + v2.0.0 两版本；logistics/get_report 独立共存");
        } else {
            log("1) 非种子实例：直接读取共享 Redis 中已注册的工具（验证多实例一致性）");
        }

        // 2) 发现在层过滤：管理员可见全部；guest 看不到需 scope 的工具
        List<ToolboxDefinition> adminView = registry.list("admin", List.of("*"), null);
        List<ToolboxDefinition> guestView = registry.list("guest", List.of(), null);
        log("2) 发现层：admin 看到 " + adminView.size() + " 个版本；guest 看到 " + guestView.size() + " 个（无 scope 被过滤）");
        guestView.forEach(d -> log("   guest可见: " + d.getId() + "@" + d.getVersion()));

        // 3) 调用层拦截：guest 调需 scope 的工具被拦；admin 放行（用永不被弃用的 get_report，跨实例状态无关）
        try {
            registry.resolve("guest", List.of(), retailReport, null);
            log("   [异常] guest 竟能调用 get_report，拦截失效！");
        } catch (SecurityException e) {
            log("3) 调用层：guest 调 get_report 被拦 -> " + e.getMessage());
        }
        ToolboxDefinition ok = registry.resolve("admin", List.of("*"), retailReport, null);
        log("   admin 调 get_report 放行 -> " + ok.getId() + "@" + ok.getVersion());

        // 4) 灰度路由：canary caller 固定走 v2.0.0（灰度路由写入共享存储，跨实例生效）
        if (seed) {
            registry.setGrayRoute(retailReport, "canary", "2.0.0");
        }
        ToolboxDefinition canaryPick = registry.resolve("canary", List.of("report:gen"), retailReport, null);
        ToolboxDefinition normalPick = registry.resolve("normal", List.of("report:gen"), retailReport, null);
        log("4) 灰度：canary 命中 " + canaryPick.getVersion() + "；normal 命中 " + normalPick.getVersion());

        // 5) checksum：接口变才变（v1 vs v2 哈希不同；实现微调哈希不变）
        String c1 = registry.versions(retailReport).get("1.0.0").getChecksum();
        String c2 = registry.versions(retailReport).get("2.0.0").getChecksum();
        log("5) checksum：v1.0.0=" + c1.substring(0, 12) + "...  v2.0.0=" + c2.substring(0, 12) + "...（接口不同，哈希不同）");
        log("   伪代码：checksum = sha256(方法签名 | 参数schema | 返回值schema)");

        // 6) 弃用状态机：deprecate -> 宽限14天 -> REMOVED(stub)（仅种子实例演这一条；第二实例会读到 REMOVED 状态）
        if (seed) {
            registry.deprecate(retailStore);
            log("6) deprecate(query_store) -> status=" + registry.versions(retailStore).get("1.0.0").getStatus());
            // 模拟时间推进 15 天
            Instant base = Instant.now();
            registry.setClock(Clock.fixed(base.plus(java.time.Duration.ofDays(15)), ZoneId.of("UTC")));
            registry.advanceLifecycle();
            ToolboxStatus after = registry.versions(retailStore).get("1.0.0").getStatus();
            log("   宽限15天后 advanceLifecycle() -> status=" + after + "（stub 保留30天，期间 resolve 返回迁移指引）");
            try {
                registry.resolve("admin", List.of("*"), retailStore, null);
            } catch (IllegalStateException e) {
                log("   resolve 已移除 -> " + e.getMessage());
            }
        } else {
            ToolboxStatus after = registry.versions(retailStore).get("1.0.0").getStatus();
            log("6) 非种子实例读到 query_store 状态=" + after + "（与种子实例一致，证明弃用状态跨实例同步）");
        }

        // 7) 多租户预埋（S级#13）：tenantId 字段存在，本期留空=全局
        log("7) tenantId 预埋：当前工具 tenantId=" + registry.versions(retailReport).get("1.0.0").getTenantId()
                + "（null=全局；篇13 多租户填此字段即隔离，本篇代码不用改）");

        // 8) 最小可观测输出：GET /api/toolboxes 等价 JSON
        log("8) GET /api/toolboxes 输出：");
        controller.list().forEach(v -> log("   " + v));

        log("=== 实测演练结束 ===");
    }

    private static ToolboxDefinition mk(ToolboxId id, String version, List<String> tags, String owner,
                                         String scope, String docUrl, String tenantId,
                                         String sig, String param, String ret) {
        ToolboxDefinition d = new ToolboxDefinition(id, version, tags, owner, scope, docUrl, tenantId, sig, param, ret);
        // 演示健康度（真实场景由监控回填）
        d.setSuccessRate(99.2);
        d.setP99Millis(280);
        d.setErrorRate(0.4);
        return d;
    }

    private static void log(String s) {
        System.out.println("[实测] " + s);
    }
}
