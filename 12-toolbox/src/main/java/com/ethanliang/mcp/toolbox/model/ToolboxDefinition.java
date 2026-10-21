package com.ethanliang.mcp.toolbox.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

/**
 * 一个 Toolbox（工具包）的治理元数据。
 *
 * 复合键 = ownerTeamId + name（见 ToolboxId）；version 在外层。
 * checksum 由「方法签名 + 参数schema + 返回值schema」哈希得到，
 * 仅接口变化才变，代码内部实现微调不改 checksum（区分"行为变更"vs"实现微调"）。
 */
@Getter
public class ToolboxDefinition {

    private final ToolboxId id;
    private final String version;            // SemVer 字符串
    private final List<String> tags;
    private final String owner;              // 个人/团队联系人
    private final String ownerTeamId;        // 冗余于 id，便于快照输出
    private final String requiredScope;      // 调用所需 scope
    private final String checksum;           // sha256(签名|参数schema|返回schema)
    private final String docUrl;
    private final String tenantId;           // S级#13：多租户预埋，默认 null = 全局
    private final String methodSignature;
    private final String paramSchemaJson;
    private final String returnSchemaJson;

    @Setter private ToolboxStatus status;
    @Setter private Instant deprecatedAt;
    @Setter private double successRate;     // 健康度：调用成功率
    @Setter private double p99Millis;        // 健康度：P99 耗时(ms)
    @Setter private double errorRate;        // 健康度：报错率

    public ToolboxDefinition(ToolboxId id, String version, List<String> tags, String owner,
                             String requiredScope, String docUrl, String tenantId,
                             String methodSignature, String paramSchemaJson, String returnSchemaJson) {
        this.id = id;
        this.version = version;
        this.tags = tags;
        this.owner = owner;
        this.ownerTeamId = id.ownerTeamId();
        this.requiredScope = requiredScope;
        this.docUrl = docUrl;
        this.tenantId = tenantId;
        this.methodSignature = methodSignature;
        this.paramSchemaJson = paramSchemaJson;
        this.returnSchemaJson = returnSchemaJson;
        this.status = ToolboxStatus.ACTIVE;
        this.checksum = Checksums.sha256(methodSignature, paramSchemaJson, returnSchemaJson);
    }
}
