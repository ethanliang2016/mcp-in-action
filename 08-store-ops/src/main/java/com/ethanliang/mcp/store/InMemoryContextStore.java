package com.ethanliang.mcp.store;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本实例内存存储 —— 多实例下会丢，用于演示"外置之前"的问题。
 */
@Component
@ConditionalOnProperty(name = "store.context.type", havingValue = "memory", matchIfMissing = true)
public class InMemoryContextStore implements ContextStore {

    private final Map<String, String> store = new ConcurrentHashMap<>();

    @Override
    public void save(String key, String value) {
        store.put(key, value);
    }

    @Override
    public String load(String key) {
        return store.get(key);
    }

    @Override
    public String kind() {
        return "memory(本实例)";
    }
}
