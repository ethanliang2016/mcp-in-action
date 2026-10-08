package com.ethanliang.mcp.model;

/**
 * 门店工具统一返回结果（与篇八一致）。
 *
 * 无状态（STATELESS）服务器不支持 elicitation / sampling / ping，
 * 工具缺参数时只能把"我缺参数"作为返回结果的一部分带回去（input_required 语义）。
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
