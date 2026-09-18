package com.ethanliang.mcp.apps.service;

import com.ethanliang.mcp.apps.ui.AppOnlyUiMetaProvider;
import com.ethanliang.mcp.apps.ui.DashboardHtml;
import com.ethanliang.mcp.apps.ui.DashboardUiMetaProvider;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 机房仪表盘：一个工具 + 一份界面资源。
 *
 * MCP Apps 的落地就两处声明，靠 URI 对齐：
 *   - @McpTool(metaProvider = ...) 把 _meta.ui.resourceUri 挂到工具上
 *   - @McpResource(uri = "ui://...") 提供界面本体
 * 宿主在 tools/list 阶段就能知道这个工具有配套界面，
 * 不用等到调用完再猜。
 *
 * 数据用内存 mock，不依赖真实设备。
 */
@Service
public class RoomDashboardService {

    private static final Map<String, Boolean> DEVICE_STATE =
            new ConcurrentHashMap<>(Map.of("ac-01", true, "fan-02", false, "light-03", true));

    /**
     * 带界面的工具。
     *
     * 返回值仍然是一份正常的工具结果——这是**文本回退**：
     * 不支持 MCP Apps 的宿主拿到的就是这段结构化数据，工具照样可用。
     * 关键结论不能只藏在界面里。
     */
    @McpTool(name = "get_room_dashboard",
            description = "获取机房仪表盘数据（温湿度与设备状态），结果带一块可交互界面",
            generateOutputSchema = true,
            metaProvider = DashboardUiMetaProvider.class)
    public RoomDashboard getRoomDashboard() {
        return new RoomDashboard(23.5, 48.2, snapshot());
    }

    /**
     * 只给界面用的工具：_meta.ui.visibility = app，不交给模型。
     */
    @McpTool(name = "control_device",
            description = "启动或关闭指定设备（仅界面可用）",
            metaProvider = AppOnlyUiMetaProvider.class)
    public String controlDevice(
            @McpToolParam(description = "设备名称") String deviceName,
            @McpToolParam(description = "1 启动 / 0 关闭；不传则取反", required = false) Integer operateType) {
        if (!DEVICE_STATE.containsKey(deviceName)) {
            return "未找到设备：" + deviceName;
        }
        boolean on = operateType == null ? !DEVICE_STATE.get(deviceName) : operateType == 1;
        DEVICE_STATE.put(deviceName, on);
        return deviceName + " 已" + (on ? "启动" : "关闭");
    }

    /**
     * 界面资源本体。mimeType 是宿主识别「这是个 MCP App」的依据。
     */
    @McpResource(uri = DashboardUiMetaProvider.DASHBOARD_URI,
            name = "room-dashboard",
            description = "机房仪表盘界面",
            mimeType = "text/html;profile=mcp-app")
    public String roomDashboardUi() {
        return DashboardHtml.TEMPLATE;
    }

    private List<Device> snapshot() {
        return DEVICE_STATE.entrySet().stream()
                .map(e -> new Device(e.getKey(), e.getValue()))
                .toList();
    }

    public record RoomDashboard(double temperature, double humidity, List<Device> devices) {
    }

    public record Device(String name, boolean on) {
    }
}
