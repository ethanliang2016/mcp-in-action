package com.ethanliang.mcp.service;

import com.ethanliang.mcp.model.StoreResult;
import com.ethanliang.mcp.store.ContextStore;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 门店运营工具集（第 10 篇高可用版）。
 *
 * 相对篇八的变化：
 *   1. get_device_status 接 Resilience4j 熔断：下游"慢"也算失败（slowCallDurationThreshold=1s），
 *      打开后走 fallback 返回"暂不可用"，不影响其他工具。
 *   2. get_store_sales 接 Redis 缓存做软降级：缓存命中直接返回；Redis 不可用时降级返回源数据并标注。
 *   3. suggest_restock 的"规则表"外置到 Redis（key=rule:restock-multiplier），多实例读到同一份。
 *   4. 上下文 ContextStore 默认 redis（见 application.yml store.context.type=redis）。
 *
 * 说明：文章给出的是 @CircuitBreaker 注解写法；此处用 Resilience4j 纯库 API 手动构建，
 * 语义完全等价（failureRate/slowCallRate 50%、slowCallDuration 1s、waitDuration 10s），
 * 且不依赖 spring-boot 自动配置，便于在 Boot 4 下独立运行。
 */
@Slf4j
@Service
public class StoreService {

    private static final Map<String, String> SALES = Map.of(
            "ST001", "天河城店 今日销售 12,480 元，客流 431",
            "ST002", "北京路店 今日销售 9,860 元，客流 372",
            "ST003", "深圳华强北店 今日销售 15,230 元，客流 508"
    );

    private static final Map<String, String> INVENTORY = Map.of(
            "ST001", "SKU-1001 库存 23（安全线 50）",
            "ST002", "SKU-1001 库存 61（安全线 50）",
            "ST003", "SKU-1002 库存 8（安全线 30）"
    );

    private static final Map<String, String> DEVICE = Map.of(
            "ST001", "空调 ac-01 运行中，照明 light-03 运行中",
            "ST002", "空调 ac-01 已关闭，风扇 fan-02 运行中",
            "ST003", "空调 ac-01 运行中，风扇 fan-02 已关闭"
    );

    private static final Duration SALES_CACHE_TTL = Duration.ofMinutes(5);
    private static final String RESTOCK_RULE_KEY = "rule:restock-multiplier";

    private final ContextStore contextStore;
    private final StringRedisTemplate redis;
    private final String port;
    private final long deviceLatencyMs;
    private final CircuitBreaker deviceCb;

    public StoreService(ContextStore contextStore,
                        StringRedisTemplate redis,
                        @Value("${server.port}") String port,
                        @Value("${ha.device.latency-ms:0}") long deviceLatencyMs) {
        this.contextStore = contextStore;
        this.redis = redis;
        this.port = port;
        this.deviceLatencyMs = deviceLatencyMs;

        // 熔断配置：与文章一致——失败率或慢调用率超 50% 即打开，10s 后半开试探
        this.deviceCb = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                        .failureRateThreshold(50)
                        .slowCallDurationThreshold(Duration.ofSeconds(1))
                        .slowCallRateThreshold(50)
                        .waitDurationInOpenState(Duration.ofSeconds(10))
                        .permittedNumberOfCallsInHalfOpenState(1)
                        .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                        .slidingWindowSize(10)
                        .minimumNumberOfCalls(5)
                        .build())
                .circuitBreaker("deviceStatus");
        log.info("deviceStatus circuit breaker ready: slowCallDuration=1s, failureRate=50%, slowCallRate=50%, waitDuration=10s");
    }

    @Tool(name = "get_store_sales", description = "查询指定门店的今日销售（带 Redis 缓存软降级）")
    public StoreResult getStoreSales(
            @ToolParam(description = "门店编码，如 ST001、ST002、ST003；可不传，由客户端补充",
                       required = false) String storeCode) {

        if (storeCode == null || storeCode.isBlank()) {
            return StoreResult.inputRequired("storeCode", "要查哪个门店？可用：ST001 / ST002 / ST003");
        }

        // 软降级：优先读缓存；命中直接返回（标注来源）；未命中查源并回填
        String cacheKey = "sales:" + storeCode;
        try {
            String cached = redis.opsForValue().get(cacheKey);
            if (cached != null) {
                return StoreResult.ok(cached + " | 来源=缓存(软降级)");
            }
        } catch (Exception e) {
            log.warn("读销售缓存失败，降级返回源数据: {}", e.getMessage());
        }

        String sales = SALES.get(storeCode);
        if (sales == null) {
            return StoreResult.fail("未找到门店：" + storeCode);
        }
        try {
            redis.opsForValue().set(cacheKey, sales, SALES_CACHE_TTL);
        } catch (Exception ignored) {
            // 写缓存失败不影响主流程
        }
        return StoreResult.ok(sales + " | 来源=实时");
    }

    @Tool(name = "get_inventory", description = "查询指定门店的商品库存")
    public StoreResult getInventory(
            @ToolParam(description = "门店编码，如 ST001") String storeCode) {
        String inv = INVENTORY.get(storeCode);
        return inv == null ? StoreResult.fail("未找到门店：" + storeCode) : StoreResult.ok(inv);
    }

    @Tool(name = "get_device_status", description = "查询指定门店的设备运行状态（带熔断降级）")
    public StoreResult getDeviceStatus(
            @ToolParam(description = "门店编码，如 ST001") String storeCode) {

        Supplier<StoreResult> supplier = () -> {
            // 故障演练开关：ha.device.latency-ms>0 时模拟"下游慢但不报错"
            if (deviceLatencyMs > 0) {
                try {
                    Thread.sleep(deviceLatencyMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            String dev = DEVICE.get(storeCode);
            return dev == null ? StoreResult.fail("未找到门店：" + storeCode) : StoreResult.ok(dev);
        };

        // 等价于文章 @CircuitBreaker(name="deviceStatus", fallbackMethod="fallback")：
        // 下游慢（>1s）计为慢调用，慢调用率超 50% 即打开熔断；打开后走 fallback。
        Supplier<StoreResult> decorated = CircuitBreaker.decorateSupplier(deviceCb, supplier);
        try {
            return decorated.get();
        } catch (CallNotPermittedException e) {
            // 熔断已打开：走中降级，返回"暂不可用"，不影响其他工具
            log.warn("deviceStatus 熔断已打开，返回 fallback");
            return deviceStatusFallback();
        }
    }

    /** 熔断打开后的降级返回（中降级）。 */
    private StoreResult deviceStatusFallback() {
        return StoreResult.ok("设备状态暂不可用（熔断降级）");
    }

    @Tool(name = "suggest_restock", description = "根据库存生成补货建议（规则表外置 Redis）")
    public StoreResult suggestRestock(
            @ToolParam(description = "门店编码，如 ST001") String storeCode) {
        String inv = INVENTORY.get(storeCode);
        if (inv == null) {
            return StoreResult.fail("未找到门店：" + storeCode);
        }
        // 规则表（安全线的倍数）外置 Redis，所有实例读同一份，避免多实例算出不同建议
        double multiplier = 1.2;
        try {
            String v = redis.opsForValue().get(RESTOCK_RULE_KEY);
            if (v != null) {
                multiplier = Double.parseDouble(v);
            }
        } catch (Exception e) {
            log.warn("读补货规则失败，用默认倍数 1.2: {}", e.getMessage());
        }
        return StoreResult.ok(inv + " → 建议补货至安全线的 " + multiplier + " 倍");
    }

    /** 写上下文（带处理实例端口，便于验证请求落到哪个实例）。 */
    @Tool(name = "save_context", description = "保存一段对话上下文")
    public StoreResult saveContext(
            @ToolParam(description = "上下文 key") String key,
            @ToolParam(description = "上下文内容") String value) {
        contextStore.save(key, value);
        return StoreResult.ok("已保存 key=" + key + " | 存储=" + contextStore.kind() + " | 处理实例=" + resolveInstance());
    }

    /** 返回可区分实例的标识（用于验证 nginx 加权轮询落到哪个实例）。 */
    private String resolveInstance() {
        String id = System.getenv("INSTANCE_ID");
        if (id != null && !id.isBlank()) {
            return id;
        }
        return String.valueOf(port);
    }

    /** 读上下文（带处理实例端口，验证多实例下仍能读到）。 */
    @Tool(name = "load_context", description = "读取一段对话上下文")
    public StoreResult loadContext(
            @ToolParam(description = "上下文 key") String key) {
        String value = contextStore.load(key);
        if (value == null) {
            return StoreResult.fail("未读到 key=" + key + " | 存储=" + contextStore.kind() + " | 处理实例=" + resolveInstance());
        }
        return StoreResult.ok("读到 " + key + "=" + value + " | 存储=" + contextStore.kind() + " | 处理实例=" + resolveInstance());
    }
}
