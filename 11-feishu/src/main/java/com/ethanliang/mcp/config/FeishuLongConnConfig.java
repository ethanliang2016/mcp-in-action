package com.ethanliang.mcp.config;

import com.lark.oapi.event.EventDispatcher;
import com.lark.oapi.service.im.ImService;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1;
import com.lark.oapi.service.im.v1.model.ReplyMessageReq;
import com.lark.oapi.service.im.v1.model.ReplyMessageReqBody;
import com.lark.oapi.ws.Client;
import com.ethanliang.mcp.model.StoreResult;
import com.ethanliang.mcp.service.StoreService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 飞书长连接接入（篇11 第3层核心）。
 *
 * 在 Spring 容器就绪后启动飞书 WebSocket 长连接：
 *  - 构建 Client / EventDispatcher 会加载飞书 SDK 全部类（gson/slf4j 等），
 *    若与 Spring AI 2.0.1 冲突，启动或首条消息即抛 NoSuchMethodError / ClassNotFoundException；
 *  - 群内 @ 机器人 → 收到 im.message.receive_v1 → 解析意图 → 调门店 @Tool（复用篇8 业务域）
 *    → 渲染飞书交互卡片回复，形成「事件订阅双向」闭环。
 *
 * 多租户增量（报告🟠3.1）：用 sender open_id → 门店编码 的绑定做数据切片，
 * 生产来源应是飞书通讯录 contact API（open_id→user_id→部门/门店）；此处实测用 demo 映射。
 */
@Slf4j
@Component
public class FeishuLongConnConfig {

    private final Environment env;
    private final StoreService storeService;

    /** 生产应由飞书通讯录 contact API 解析；实测用 demo 映射（open_id 占位）。 */
    private static final Map<String, String> OPENID_STORE = Map.of();

    private static final Pattern TEXT = Pattern.compile("\"text\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern MENTION = Pattern.compile("@_user_\\d+");
    private static final Pattern STORE = Pattern.compile("ST\\d{3}", Pattern.CASE_INSENSITIVE);

    public FeishuLongConnConfig(Environment env, StoreService storeService) {
        this.env = env;
        this.storeService = storeService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        String appId = resolveAppId();
        String appSecret = resolveAppSecret();

        if (appId == null || appSecret == null || appId.contains("xxxx") || appSecret.contains("xxxx")) {
            log.warn("[P3] 未配置飞书凭据，跳过长连接启动（仅验证 classpath 共存，不连真机）");
            return;
        }

        // 主 API 客户端：用于接收事件后回复卡片（全限定名，避免与 ws.Client 同名冲突，踩坑1）
        com.lark.oapi.Client apiClient = com.lark.oapi.Client.newBuilder(appId, appSecret).build();

        EventDispatcher dispatcher = EventDispatcher.newBuilder(appId, appSecret)
                .onP2MessageReceiveV1(new ImService.P2MessageReceiveV1Handler() {
                    @Override
                    public void handle(P2MessageReceiveV1 event) throws Exception {
                        String chatType = event.getEvent().getMessage().getChatType();
                        String messageId = event.getEvent().getMessage().getMessageId();
                        String openId = event.getEvent().getSender().getSenderId().getOpenId();
                        String raw = event.getEvent().getMessage().getContent();
                        log.info("[P3][event] 收到消息 | 会话类型={} | 发送者={}", chatType, openId);

                        // 1) 解析文本意图（剥掉 @_user_xxx mention）
                        String text = parseText(raw);
                        log.info("[P3][parse] 用户原话={}", text);

                        // 2) 多租户：open_id → 门店编码（实测 demo；生产走通讯录 API）
                        String storeCode = resolveStoreCode(openId, text);
                        log.info("[P3][tenant] open_id={} -> storeCode={}", openId, storeCode);

                        // 3) 调门店 @Tool（复用篇8 业务域，零重写）
                        StoreResult result = dispatch(text, storeCode);

                        // 4) 渲染飞书交互卡片并回复（第3层双向闭环）
                        String card = buildCard(text, result);
                        ReplyMessageReq req = ReplyMessageReq.newBuilder()
                                .messageId(messageId)
                                .replyMessageReqBody(ReplyMessageReqBody.newBuilder()
                                        .msgType("interactive")
                                        .content(card)
                                        .build())
                                .build();
                        apiClient.im().message().reply(req);
                        log.info("[P3][reply] 已回卡片 message_id={} | success={}", messageId, result.success());
                    }
                })
                .build();

        Client wsClient = new Client.Builder(appId, appSecret)
                .eventHandler(dispatcher)
                .autoReconnect(true)
                .build();

        Thread wsThread = new Thread(() -> {
            try {
                wsClient.start();
            } catch (Exception e) {
                log.error("[P3] 长连接异常", e);
            }
        }, "feishu-ws");
        wsThread.setDaemon(true);
        wsThread.start();

        log.info("[P3] 飞书 SDK 已加载，长连接客户端已在 Spring 进程内启动（无冲突）");
    }

    /** 解析事件消息体的 text 字段，并剥除 @ 提及。 */
    private String parseText(String content) {
        if (content == null) return "";
        Matcher m = TEXT.matcher(content);
        String t = m.find() ? m.group(1) : content;
        return MENTION.matcher(t).replaceAll("").trim();
    }

    /** 多租户数据隔离：优先从文本取门店编码，其次 open_id 映射；生产应来自通讯录 API。 */
    private String resolveStoreCode(String openId, String text) {
        Matcher m = STORE.matcher(text);
        if (m.find()) return m.group().toUpperCase();
        String bound = OPENID_STORE.get(openId);
        return bound; // null → 工具层会返回 inputRequired 提示补门店编码
    }

    /** 按关键词把消息路由到对应门店工具。 */
    private StoreResult dispatch(String text, String storeCode) {
        String lower = text.toLowerCase();
        if (lower.contains("库存") || lower.contains("inventory")) {
            return storeService.getInventory(storeCode);
        }
        if (lower.contains("设备") || lower.contains("device")) {
            return storeService.getDeviceStatus(storeCode);
        }
        if (lower.contains("补货") || lower.contains("restock")) {
            return storeService.suggestRestock(storeCode);
        }
        // 默认：销售（含 sales）
        return storeService.getStoreSales(storeCode);
    }

    /** 渲染飞书交互卡片（interactive）。 */
    private String buildCard(String text, StoreResult r) {
        String body = r.inputRequired()
                ? "⚠️ " + r.message() + "（如：销售 ST001）"
                : (r.success() ? r.message() : "❌ " + r.message());
        String title = r.inputRequired() ? "需要补充信息" : (r.success() ? "门店助手 · 查询成功" : "门店助手 · 查询失败");
        return """
                {
                  "config": { "wide_screen_mode": true },
                  "header": {
                    "title": { "tag": "plain_text", "content": "%s" },
                    "template": "%s"
                  },
                  "elements": [
                    { "tag": "div", "text": { "tag": "lark_md", "content": "**指令**：%s\\n\\n%s" } },
                    { "tag": "hr" },
                    { "tag": "note", "elements": [ { "tag": "plain_text", "content": "MCP 实战手记（十一）· 飞书长连接事件订阅闭环" } ] }
                  ]
                }
                """.formatted(title, r.success() ? "green" : "red", escape(text), escape(body));
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String resolveAppId() {
        String v = env.getProperty("feishu.app-id");
        if (v != null) return v;
        return readCredLine(0);
    }

    private String resolveAppSecret() {
        String v = env.getProperty("feishu.app-secret");
        if (v != null) return v;
        return readCredLine(1);
    }

    private String readCredLine(int idx) {
        try {
            java.nio.file.Path p = Paths.get("feishu.cred");
            if (Files.exists(p)) {
                List<String> lines = Files.readAllLines(p);
                if (lines.size() > idx) return lines.get(idx).trim();
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
