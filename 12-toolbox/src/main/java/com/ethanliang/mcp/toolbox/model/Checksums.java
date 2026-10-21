package com.ethanliang.mcp.toolbox.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * checksum = sha256(方法签名 | 参数schema | 返回值schema)。
 * 接口变了哈希才变；纯实现微调哈希不变——这是"行为变更vs实现微调"的判定标准。
 */
public final class Checksums {

    public static String sha256(String methodSignature, String paramSchema, String returnSchema) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String input = methodSignature + "|" + paramSchema + "|" + returnSchema;
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private Checksums() {
    }
}
