package com.ethanliang.mcp.toolbox.model;

/**
 * 弃用状态机：ACTIVE -> DEPRECATED(宽限14天) -> REMOVED(stub保留30天) -> 彻底删除。
 */
public enum ToolboxStatus {
    ACTIVE,
    DEPRECATED,
    REMOVED
}
