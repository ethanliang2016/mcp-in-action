package com.ethanliang.mcp.service;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 机房环境监控工具（第一个 MCP Server 的演示工具）。
 *
 * 说明：为便于直接运行，数据全部使用内存 mock，不依赖真实数据库或设备。
 * 重点看 @Tool / @ToolParam 怎么把普通方法变成 MCP 工具。
 */
@Service
public class RoomMonitorService {

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

    public record RoomEnvironment(double temperature, double humidity) {}
}
