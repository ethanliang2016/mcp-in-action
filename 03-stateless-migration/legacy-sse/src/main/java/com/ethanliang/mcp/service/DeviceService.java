package com.ethanliang.mcp.service;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 设备工具定义（旧版写法）。
 *
 * 说明：为便于直接运行，数据全部使用内存 mock，不依赖真实数据库或设备。
 * 工具定义部分（@Tool / @ToolParam）在新旧规范间保持稳定。
 */
@Service
public class DeviceService {

    /** mock 设备状态：设备名 -> 是否开启 */
    private static final Map<String, Boolean> DEVICE_STATE =
            new ConcurrentHashMap<>(Map.of("ac-01", true, "fan-02", false, "light-03", true));

    @Tool(name = "get_room_environment", description = "获取机房环境数据（温度、湿度）")
    public RoomEnvironment getRoomEnvironment() {
        // 真实项目中这里查数据库 / 时序库
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

    @Tool(name = "control_device", description = "启动或关闭指定设备")
    public String controlDevice(
            @ToolParam(description = "设备名称") String deviceName,
            @ToolParam(description = "1:启动  0:关闭") int operateType) {
        if (!DEVICE_STATE.containsKey(deviceName)) {
            return "未找到设备：" + deviceName;
        }
        DEVICE_STATE.put(deviceName, operateType == 1);
        return deviceName + " 已" + (operateType == 1 ? "启动" : "关闭");
    }

    public record RoomEnvironment(double temperature, double humidity) {}
}
