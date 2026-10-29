package com.ethanliang.mcp.multitenant;

/**
 * 当前请求的租户上下文（ThreadLocal）。
 * 由 TenantFilter 在传输层写入，Controller / 工具实现读取，请求结束时清理。
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(String tenantId) {
        CURRENT.set(tenantId);
    }

    public static String get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
