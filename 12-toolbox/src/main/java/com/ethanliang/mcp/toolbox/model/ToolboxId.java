package com.ethanliang.mcp.toolbox.model;

/**
 * Toolbox 复合唯一键：owner_team_id + name。
 * 版本号在外层，不参与唯一键——同一团队同名工具可并存多版本（v1/v2）。
 */
public record ToolboxId(String ownerTeamId, String name) {

    public static ToolboxId of(String ownerTeamId, String name) {
        return new ToolboxId(ownerTeamId, name);
    }

    @Override
    public String toString() {
        return ownerTeamId + "/" + name;
    }
}
