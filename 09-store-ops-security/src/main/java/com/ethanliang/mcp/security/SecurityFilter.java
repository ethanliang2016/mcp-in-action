package com.ethanliang.mcp.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 安全过滤器：L2 客户端身份 → L5 限流 → L3 提取用户身份。
 *
 * 对 /mcp 的每个请求依次过：
 *   1. Bearer Token 校验（客户端身份）——不对就 401
 *   2. 按 token 主体限流——超了就 429
 *   3. 提取 X-User-Id 放进 UserContext（用户身份透传，过渡方案）
 *   4. 全程写审计日志（L6）
 */
@Slf4j
@Component
public class SecurityFilter extends OncePerRequestFilter {

    /** 令牌来自配置（配置里再取环境变量），代码里不出现任何凭据 */
    @Value("${store.security.token:}")
    private String validToken;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.startsWith("/mcp")) {
            chain.doFilter(request, response);
            return;
        }

        long start = System.currentTimeMillis();

        // L2 客户端身份
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.equals("Bearer " + validToken)) {
            audit(request, "REJECT_CLIENT_AUTH", "-", start);
            response.setStatus(401);
            response.getWriter().write("{\"error\":\"invalid client token\"}");
            return;
        }
        String clientTokenSubject = "agent-client";

        // L5 限流（按客户端维度；真实实现可按用户维度）
        if (!RateLimiter.tryConsume(clientTokenSubject)) {
            audit(request, "REJECT_RATE_LIMIT", "-", start);
            response.setStatus(429);
            response.getWriter().write("{\"error\":\"rate limited\"}");
            return;
        }

        // L3 用户身份透传（提取客户端声明的用户）
        String userId = request.getHeader("X-User-Id");
        if (userId != null) {
            UserContext.set(userId);
        }

        audit(request, "PASS", userId == null ? "-" : userId, start);
        try {
            chain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }

    /** L6 审计日志：结构化、每次调用一条 */
    private void audit(HttpServletRequest req, String result, String userId, long start) {
        log.info("AUDIT | path={} | method={} | result={} | user={} | costMs={}",
                req.getRequestURI(), req.getMethod(), result, userId,
                System.currentTimeMillis() - start);
    }
}
