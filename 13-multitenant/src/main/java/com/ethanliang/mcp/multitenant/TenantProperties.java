package com.ethanliang.mcp.multitenant;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 租户定义。生产环境这份配置应来自控制面（DB/配置中心），此处为可一键复现写在 yml。
 */
@ConfigurationProperties(prefix = "multitenant")
public class TenantProperties {

    /** 固定窗口长度（秒），配额计数窗口 */
    private long quotaWindowSeconds = 60;

    /** 网关聚合后的全量工具表 */
    private List<String> allTools = new ArrayList<>();

    /** 租户列表 */
    private List<TenantDef> tenants = new ArrayList<>();

    public long getQuotaWindowSeconds() {
        return quotaWindowSeconds;
    }

    public void setQuotaWindowSeconds(long quotaWindowSeconds) {
        this.quotaWindowSeconds = quotaWindowSeconds;
    }

    public List<String> getAllTools() {
        return allTools;
    }

    public void setAllTools(List<String> allTools) {
        this.allTools = allTools;
    }

    public List<TenantDef> getTenants() {
        return tenants;
    }

    public void setTenants(List<TenantDef> tenants) {
        this.tenants = tenants;
    }

    public static class TenantDef {
        private String id;
        private String key;
        /** 该租户可见的工具子集（逗号分隔） */
        private List<String> allowedTools = new ArrayList<>();
        /** 窗口内配额上限 */
        private long quotaLimit = 20;
        /** Redis 不可用时的降级策略：closed=全拒（保隔离）/ open=放行（保可用） */
        private String failover = "closed";

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public List<String> getAllowedTools() {
            return allowedTools;
        }

        public void setAllowedTools(List<String> allowedTools) {
            this.allowedTools = allowedTools;
        }

        public long getQuotaLimit() {
            return quotaLimit;
        }

        public void setQuotaLimit(long quotaLimit) {
            this.quotaLimit = quotaLimit;
        }

        public String getFailover() {
            return failover;
        }

        public void setFailover(String failover) {
            this.failover = failover;
        }
    }
}
