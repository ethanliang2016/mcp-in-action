package com.ethanliang.mcp.gateway;

import java.io.IOException;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 端点鉴权：拦在 /mcp 之前，只放带正确 Bearer 令牌的请求进网关。
 *
 * 令牌来自环境变量 ${mcp.gateway.token}（启动时由 GATEWAY_MCP_TOKEN 注入），
 * 绝不写死在代码或配置里。没带 / 带错 → 401；带对 → 放行。
 */
@Component
@Order(1)
public class GatewayAuthFilter implements Filter {

    @Value("${mcp.gateway.token:}")
    private String expected;

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        var http = (HttpServletRequest) req;
        var auth = http.getHeader("Authorization");
        boolean ok = !expected.isEmpty()
                && auth != null && auth.startsWith("Bearer ")
                && expected.equals(auth.substring(7));
        if (!ok) {
            ((HttpServletResponse) res).sendError(401, "unauthorized");
            return;
        }
        chain.doFilter(req, res);
    }
}
