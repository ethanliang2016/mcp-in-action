package com.ethanliang.mcp.store;

/**
 * 上下文存储抽象（与篇八一致）。
 *
 * 通过 store.context.type=memory|redis 切换；高可用篇固定用 redis（外置共享，
 * 多实例之间上下文一致，且 Redis 本身走哨兵防单点）。
 */
public interface ContextStore {

    void save(String key, String value);

    String load(String key);

    /** 用于日志/输出中标识当前用的是哪种存储 */
    String kind();
}
