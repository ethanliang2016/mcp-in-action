package com.ethanliang.mcp.notify;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 钉钉 / 企微 群机器人 webhook 通知（P4 对比级接入，代码就绪）。
 *
 * 用法（拿到 webhook 后本机自测）：
 *   java -cp ... WebhookNotifier dingtalk "<webhook含access_token>" "<加签secret>" "篇11连通性自测"
 *   java -cp ... WebhookNotifier wecom   "<webhook>"                          ""            "篇11连通性自测"
 * 期望返回体含 "errcode":0。
 *
 * 依赖：仅 JDK 内置 java.net.http / javax.crypto，与飞书 SDK 同源，无新增冲突。
 */
public class WebhookNotifier {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** 钉钉加签版：sign = base64(HmacSHA256(secret + "\n" + timestamp, secret))，再 URL 编码 */
    public static String sendDingTalk(String webhook, String secret, String text) throws Exception {
        String url = webhook;
        if (secret != null && !secret.isBlank()) {
            long timestamp = System.currentTimeMillis();
            String stringToSign = timestamp + "\n" + secret;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String sign = Base64.getEncoder().encodeToString(
                    mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8)));
            String encSign = URLEncoder.encode(sign, "UTF-8");
            url = webhook + (webhook.contains("?") ? "&" : "?")
                    + "timestamp=" + timestamp + "&sign=" + encSign;
        }
        String body = "{\"msgtype\":\"text\",\"text\":{\"content\":" + quote(text) + "}}";
        return post(url, body);
    }

    /** 企微：直接 POST webhook，返回体含 errcode */
    public static String sendWeCom(String webhook, String text) throws Exception {
        String body = "{\"msgtype\":\"text\",\"text\":{\"content\":" + quote(text) + "}}";
        return post(webhook, body);
    }

    private static String post(String url, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        return "HTTP " + resp.statusCode() + " | " + resp.body();
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("用法: WebhookNotifier <dingtalk|wecom> <webhook> [secret] [message]");
            System.exit(1);
        }
        String mode = args[0];
        String webhook = args[1];
        String secret = args.length > 2 ? args[2] : "";
        String msg = args.length > 3 ? args[3] : "篇11连通性自测";

        String result = switch (mode) {
            case "dingtalk" -> sendDingTalk(webhook, secret, msg);
            case "wecom" -> sendWeCom(webhook, msg);
            default -> throw new IllegalArgumentException("未知 mode: " + mode);
        };
        System.out.println("[P4] " + mode + " 响应: " + result);
    }
}
