package com.ethanliang.mcp.store;

/**
 * 上下文存储抽象。
 *
 * 第 8 篇问题 2：无状态化之后服务端不存会话，
 * 原来放会话里的数据必须显式外置。
 *
 * 本模块提供两种实现，用于对照演示：
 *   - InMemoryContextStore：存本实例内存 —— 多实例下会丢（演示"问题"）
 *   - RedisContextStore   ：存外部 Redis —— 多实例下仍在（演示"解法"）
 *
 * 通过 store.context.type=memory|redis 切换。
 */
public interface ContextStore {

    void save(String key, String value);

    String load(String key);

    /** 用于日志/输出中标识当前用的是哪种存储 */
    String kind();
}
