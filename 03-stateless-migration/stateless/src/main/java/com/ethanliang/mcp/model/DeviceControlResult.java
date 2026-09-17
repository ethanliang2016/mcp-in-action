package com.ethanliang.mcp.model;

/**
 * 工具统一返回结果。
 *
 * 背景：无状态（STATELESS）服务器**不支持 elicitation / sampling / ping**——
 * 即服务端不能主动向客户端发请求。所以当工具缺少必要参数时，
 * 无法"回头问客户端要"，只能把"我缺参数"作为**工具返回结果**的一部分带回去。
 * 这正是新规范引入 input_required 语义的原因。
 *
 * 注：此处用结果对象演示该语义；Spring AI 是否已提供 input_required 的
 * 一等 API，需以官方最新文档为准。
 */
public record DeviceControlResult(
        boolean success,
        boolean inputRequired,
        String requiredParam,
        String message
) {
    public static DeviceControlResult ok(String message) {
        return new DeviceControlResult(true, false, null, message);
    }

    public static DeviceControlResult fail(String message) {
        return new DeviceControlResult(false, false, null, message);
    }

    public static DeviceControlResult inputRequired(String requiredParam, String message) {
        return new DeviceControlResult(false, true, requiredParam, message);
    }
}
