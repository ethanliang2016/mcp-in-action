package com.ethanliang.mcp.security;

import java.util.Map;
import java.util.Set;

/**
 * L4 RBAC：角色 → 可访问门店。
 *
 * 门店场景：店长只能查自己门店，区域经理可查区域内所有门店，总部可查全部。
 */
public final class StoreRbac {

    /** 用户 → 角色 */
    private static final Map<String, String> USER_ROLE = Map.of(
            "manager-st001", "STORE_MANAGER",
            "manager-st002", "STORE_MANAGER",
            "regional-gz",    "REGIONAL",
            "hq",             "HQ"
    );

    /** 店长 → 门店 */
    private static final Map<String, String> MANAGER_STORE = Map.of(
            "manager-st001", "ST001",
            "manager-st002", "ST002"
    );

    /** 区域经理 → 门店集合 */
    private static final Map<String, Set<String>> REGIONAL_STORES = Map.of(
            "regional-gz", Set.of("ST001", "ST002")
    );

    public static String roleOf(String userId) {
        return USER_ROLE.getOrDefault(userId, "DENIED");
    }

    /** 核心判断：该用户能否访问该门店 */
    public static boolean canAccessStore(String userId, String storeCode) {
        return switch (roleOf(userId)) {
            case "HQ" -> true;
            case "REGIONAL" -> REGIONAL_STORES.getOrDefault(userId, Set.of()).contains(storeCode);
            case "STORE_MANAGER" -> storeCode.equals(MANAGER_STORE.get(userId));
            default -> false;
        };
    }
}
