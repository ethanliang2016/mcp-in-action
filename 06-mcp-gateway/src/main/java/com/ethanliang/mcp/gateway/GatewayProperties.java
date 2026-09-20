package com.ethanliang.mcp.gateway;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 下游后端清单。prefix 是网关给这个后端分配的命名空间，
 * 最终暴露出去的工具名是 {@code <prefix>__<原工具名>}。
 */
@ConfigurationProperties(prefix = "mcp.gateway")
public class GatewayProperties {

    private List<BackendConfig> backends = new ArrayList<>();

    public List<BackendConfig> getBackends() {
        return backends;
    }

    public void setBackends(List<BackendConfig> backends) {
        this.backends = backends;
    }

    public static class BackendConfig {

        private String name;

        private String url;

        private String prefix;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getPrefix() {
            return prefix;
        }

        public void setPrefix(String prefix) {
            this.prefix = prefix;
        }
    }
}
