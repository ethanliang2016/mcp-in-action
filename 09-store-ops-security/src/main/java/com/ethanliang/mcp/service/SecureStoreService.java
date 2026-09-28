package com.ethanliang.mcp.service;

import com.ethanliang.mcp.security.StoreRbac;
import com.ethanliang.mcp.security.UserContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 带安全层的门店工具（L4 RBAC 在工具层生效）。
 *
 * 与 08-store-ops 的裸版对照：同样的工具，这里多了
 * 「你是谁 → 你能看哪个店」的判断。
 */
@Service
public class SecureStoreService {

    private static final Map<String, String> SALES = Map.of(
            "ST001", "天河城店 今日销售 12,480 元，客流 431",
            "ST002", "北京路店 今日销售 9,860 元，客流 372",
            "ST003", "深圳华强北店 今日销售 15,230 元，客流 508"
    );

    @Tool(name = "get_store_sales", description = "查询指定门店的今日销售（受 RBAC 控制）")
    public String getStoreSales(
            @ToolParam(description = "门店编码，如 ST001") String storeCode) {

        String userId = UserContext.get();
        if (userId == null) {
            return "拒绝：未携带用户身份（缺 X-User-Id）";
        }
        if (!StoreRbac.canAccessStore(userId, storeCode)) {
            return "拒绝：用户 " + userId + "（" + StoreRbac.roleOf(userId)
                    + "）无权访问门店 " + storeCode;
        }
        return SALES.getOrDefault(storeCode, "未找到门店：" + storeCode);
    }
}
