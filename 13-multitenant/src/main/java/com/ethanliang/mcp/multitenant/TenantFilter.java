package com.ethanliang.mcp.multitenant;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 传输层拦截：每个请求先解析租户，缺失/非法直接拒；随后做配额计数。
 *
 * 实现说明（对应正文的"待实测"）：MCP Java SDK 2.0.0 实际提供的是
 * HttpServletStatelessServerTransport + McpServer.sync（见 06-mcp-gateway），
 * 并没有正文假设的 ServerTransportInterceptor 接口。
 * 因此按正文预案"若为 provider 装饰器则逻辑等价迁移"，把拦截落在 Servlet Filter——
 * 位置同样是传输层、逻辑完全等价（解析租户 → 拒非法 → 配额 → 写上下文）。
 */
@Component
public class TenantFilter extends OncePerRequestFilter {

    private final TenantStore store;
    private final QuotaService quota;

    public TenantFilter(TenantStore store, QuotaService quota) {
        this.store = store;
        this.quota = quota;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        if (path.startsWith("/actuator") || "/health".equals(path)) {
            chain.doFilter(request, response);
            return;
        }

        // 1) 租户路由：优先 X-Tenant-Id（服务间调用覆盖），否则 X-Api-Key（Key 查表得租户）
        String apiKey = firstNonBlank(request.getHeader("X-Api-Key"), request.getHeader("X-Tenant-Id"));
        TenantProperties.TenantDef tenant = store.resolve(apiKey);
        if (tenant == null) {
            reject(response, 401, "unknown tenant");
            return;
        }

        // 2) 配额：只对"调用类"请求计数（GET 目录/探活不计，避免干扰目录隔离验证）
        if ("POST".equalsIgnoreCase(request.getMethod()) && !quota.allow(tenant)) {
            reject(response, 429, "tenant quota exceeded");
            return;
        }

        // 3) 数据隔离：日志全链路带租户；下游可取当前租户
        MDC.put("tenant", tenant.getId());
        TenantContext.set(tenant.getId());
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            MDC.remove("tenant");
        }
    }

    private void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
