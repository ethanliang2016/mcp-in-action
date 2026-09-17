package com.ethanliang.mcp.service;

import com.ethanliang.mcp.model.DeviceControlResult;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 设备工具定义（新版写法）。
 *
 * 与旧版（legacy-sse 模块）对比：
 *   - @Tool / @ToolParam 写法**完全一致**，这是迁移中最省心的一点
 *   - 差异只在需要"补参数"的工具：无法用 elicitation，改为返回 inputRequired
 *
 * 数据使用内存 mock，不依赖真实数据库或设备。
 */
@Service
public class DeviceService {

    /** mock 设备状态：设备名 -> 是否开启 */
    private static final Map<String, Boolean> DEVICE_STATE =
            new ConcurrentHashMap<>(Map.of("ac-01", true, "fan-02", false, "light-03", true));

    @Tool(name = "get_room_environment", description = "获取机房环境数据（温度、湿度）")
    public RoomEnvironment getRoomEnvironment() {
        return new RoomEnvironment(23.5, 48.2);
    }

    @Tool(name = "get_device_status", description = "获取指定设备的运行状态")
    public String getDeviceStatus(
            @ToolParam(description = "设备名称，如 ac-01、fan-02、light-03") String deviceName) {
        Boolean on = DEVICE_STATE.get(deviceName);
        if (on == null) {
            return "未找到设备：" + deviceName;
        }
        return deviceName + (on ? " 运行中" : " 已关闭");
    }

    /**
     * 与旧版唯一有实质差异的工具：
     * 参数可为空，缺失时返回 inputRequired 让客户端补充，而不是抛错或用 elicitation。
     */
    @Tool(name = "control_device", description = "启动或关闭指定设备")
    public DeviceControlResult controlDevice(
            @ToolParam(description = "设备名称") String deviceName,
            // 关键：required = false。
            // 否则 @ToolParam 生成的 JSON Schema 会把它标为必填，
            // 框架在调用方法前就拦截（报 schema 校验错误），
            // 方法体里的 inputRequired 分支永远走不到。
            @ToolParam(description = "1:启动  0:关闭；可不传，由客户端补充", required = false) Integer operateType) {

        if (operateType == null) {
            return DeviceControlResult.inputRequired("operateType", "需要确认：1 启动 / 0 关闭");
        }
        if (!DEVICE_STATE.containsKey(deviceName)) {
            return DeviceControlResult.fail("未找到设备：" + deviceName);
        }
        DEVICE_STATE.put(deviceName, operateType == 1);
        return DeviceControlResult.ok(deviceName + " 已" + (operateType == 1 ? "启动" : "关闭"));
    }

    public record RoomEnvironment(double temperature, double humidity) {}
}
