package com.ethanliang.mcp.store;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本实例内存存储 —— 仅用于对照演示"无状态之后还把状态放本地"的坑。
 * 多实例下各实例互不共享，实测 save_context 在一个实例、load_context 在另一个实例会读不到。
 */
@Component
@ConditionalOnProperty(name = "store.context.type", havingValue = "memory")
public class InMemoryContextStore implements ContextStore {

    private final Map<String, String> map = new ConcurrentHashMap<>();

    @Override
    public void save(String key, String value) {
        map.put(key, value);
    }

    @Override
    public String load(String key) {
        return map.get(key);
    }

    @Override
    public String kind() {
        return "memory(本实例)";
    }
}
