package com.ethanliang.mcp.store;

/**
 * 上下文存储抽象。复用篇8 业务域（零重写）。
 */
public interface ContextStore {

    void save(String key, String value);

    String load(String key);

    /** 用于日志/输出中标识当前用的是哪种存储 */
    String kind();
}
