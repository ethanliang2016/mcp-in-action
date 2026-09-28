package com.ethanliang.mcp.service;

import com.ethanliang.mcp.model.StoreResult;
import com.ethanliang.mcp.store.ContextStore;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 门店运营工具集（第 8 篇示例业务域，全 mock 数据）。
 *
 * 覆盖：门店销售 / 商品库存 / 门店设备 / 补货建议，
 * 以及一组用于演示"上下文外置"的 save_context / load_context。
 */
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

    private final ContextStore contextStore;

    @Value("${server.port}")
    private String port;

    public StoreService(ContextStore contextStore) {
        this.contextStore = contextStore;
    }

    /**
     * 补参演示：缺 storeCode 时返回 inputRequired，而不是抛错。
     *
     * 关键：@ToolParam 必须 required = false。
     * 否则生成的 JSON Schema 会把它标为必填，框架在调用方法前就拦截
     * （报 schema 校验错误），方法体里的 inputRequired 分支永远走不到。
     */
    @Tool(name = "get_store_sales", description = "查询指定门店的今日销售")
    public StoreResult getStoreSales(
            @ToolParam(description = "门店编码，如 ST001、ST002、ST003；可不传，由客户端补充",
                       required = false) String storeCode) {

        if (storeCode == null || storeCode.isBlank()) {
            return StoreResult.inputRequired("storeCode", "要查哪个门店？可用：ST001 / ST002 / ST003");
        }
        String sales = SALES.get(storeCode);
        if (sales == null) {
            return StoreResult.fail("未找到门店：" + storeCode);
        }
        return StoreResult.ok(sales);
    }

    @Tool(name = "get_inventory", description = "查询指定门店的商品库存")
    public StoreResult getInventory(
            @ToolParam(description = "门店编码，如 ST001") String storeCode) {
        String inv = INVENTORY.get(storeCode);
        return inv == null ? StoreResult.fail("未找到门店：" + storeCode) : StoreResult.ok(inv);
    }

    @Tool(name = "get_device_status", description = "查询指定门店的设备运行状态")
    public StoreResult getDeviceStatus(
            @ToolParam(description = "门店编码，如 ST001") String storeCode) {
        String dev = DEVICE.get(storeCode);
        return dev == null ? StoreResult.fail("未找到门店：" + storeCode) : StoreResult.ok(dev);
    }

    @Tool(name = "suggest_restock", description = "根据库存生成补货建议")
    public StoreResult suggestRestock(
            @ToolParam(description = "门店编码，如 ST001") String storeCode) {
        String inv = INVENTORY.get(storeCode);
        if (inv == null) {
            return StoreResult.fail("未找到门店：" + storeCode);
        }
        return StoreResult.ok(inv + " → 建议补货至安全线的 1.2 倍");
    }

    /** 写上下文。响应里带上实例端口，用于验证"请求打到哪个实例"。 */
    @Tool(name = "save_context", description = "保存一段对话上下文")
    public StoreResult saveContext(
            @ToolParam(description = "上下文 key") String key,
            @ToolParam(description = "上下文内容") String value) {
        contextStore.save(key, value);
        return StoreResult.ok("已保存 key=" + key + " | 存储=" + contextStore.kind() + " | 处理实例=" + port);
    }

    /** 读上下文。响应里带上实例端口，用于验证多实例下是否还能读到。 */
    @Tool(name = "load_context", description = "读取一段对话上下文")
    public StoreResult loadContext(
            @ToolParam(description = "上下文 key") String key) {
        String value = contextStore.load(key);
        if (value == null) {
            return StoreResult.fail("未读到 key=" + key + " | 存储=" + contextStore.kind() + " | 处理实例=" + port);
        }
        return StoreResult.ok("读到 " + key + "=" + value + " | 存储=" + contextStore.kind() + " | 处理实例=" + port);
    }
}
