package com.ethanliang.mcp.model;

/**
 * 门店工具统一返回结果。
 *
 * 无状态（STATELESS）服务器不支持 elicitation / sampling / ping，
 * 服务端不能主动向客户端发请求。工具缺参数时只能把"我缺参数"
 * 作为返回结果的一部分带回去，即 input_required 语义。
 *
 * 注：此处用结果对象演示该语义；Spring AI 是否已提供 input_required
 * 的一等 API，以官方最新文档为准。
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
