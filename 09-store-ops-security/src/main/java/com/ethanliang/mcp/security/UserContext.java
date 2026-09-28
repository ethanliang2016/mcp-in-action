package com.ethanliang.mcp.security;

/**
 * L3 用户身份透传（过渡方案演示）。
 *
 * ⚠️ 边界声明（正文中必须如实写）：
 * 这是「身份断言」，不是「身份验证」——请求头里的 X-User-Id 是客户端声明的，
 * 服务端选择信任，没有密码学校验。真实难点（伪装、委托链、凭证轮换、
 * audience/issuer 校验）不在本演示范围。
 *
 * 生产正确姿势：标准正在成形（DPoP / ID-JAG / RFC 8693 / SEP-1933），
 * 标准落地前，此方案仅适用于「内部可信网络」。
 */
public final class UserContext {

    private static final ThreadLocal<String> CURRENT_USER = new ThreadLocal<>();

    public static void set(String userId) {
        CURRENT_USER.set(userId);
    }

    public static String get() {
        return CURRENT_USER.get();
    }

    public static void clear() {
        CURRENT_USER.remove();
    }

    private UserContext() {}
}
