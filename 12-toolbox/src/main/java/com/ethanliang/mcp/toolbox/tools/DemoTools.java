package com.ethanliang.mcp.toolbox.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 演示工具：被 Toolbox 治理层包裹的 @Tool 方法（对应篇8 门店运营助手业务域）。
 * 真实场景里这些工具由 ToolboxRegistry 管理元数据（scope/版本/弃用），而非散落各处。
 */
@Component
public class DemoTools {

    @Tool(description = "查询门店销售概览（需 store:read scope）")
    public String getStoreSales(@ToolParam(description = "门店ID") String storeId) {
        return "store " + storeId + " 销售概览";
    }

    @Tool(description = "生成门店补货报告（需 report:gen scope）")
    public String getReport(@ToolParam(description = "门店ID") String storeId) {
        return "store " + storeId + " 补货报告";
    }
}
