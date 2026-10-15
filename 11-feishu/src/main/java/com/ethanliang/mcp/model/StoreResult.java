package com.ethanliang.mcp.model;

/**
 * 门店工具统一返回结果。复用篇8 业务域（零重写）。
 */
public record StoreResult(
        boolean success,
        boolean inputRequired,
        String requiredParam,
        String message
) {
    public static StoreResult ok(String message) {
        return new StoreResult(true, false, null, message);
    }

    public static StoreResult fail(String message) {
        return new StoreResult(false, false, null, message);
    }

    public static StoreResult inputRequired(String requiredParam, String message) {
        return new StoreResult(false, true, requiredParam, message);
    }
}
