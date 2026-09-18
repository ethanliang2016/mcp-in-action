package com.ethanliang.mcp.cimd;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import java.net.InetAddress;
import java.net.URI;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
public class ClientIdMetadataInterceptor implements HandlerInterceptor {

    // 生产务必打开：只接受 https 的 client_id，挡掉明文 http 被中间人篡改
    @Value("${cimd.require-https:false}")
    private boolean requireHttps;

    // 生产务必关闭：demo 允许 localhost 才能本地跑
    @Value("${cimd.allow-private:true}")
    private boolean allowPrivate;

    // 允许托管客户端元数据的 host 白名单（demo 用，生产接 IdP / EMA）
    private final Set<String> allowedHosts = Set.of("localhost");
    private final RestTemplate rest = buildRestTemplate(2000, 2000);
    // 元数据按 client_id 缓存 5 分钟，避免每次请求都 GET
    private final Cache<String, ClientMetadata> cache =
            Caffeine.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).build();

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
        String clientId = req.getHeader("Mcp-Client-Id");
        if (clientId == null || clientId.isBlank()) {
            res.setStatus(401);
            return false;
        }
        if (requireHttps && !clientId.startsWith("https://")) {
            res.setStatus(401);
            return false;
        }
        if (!isHostAllowed(clientId)) {
            res.setStatus(401);
            return false;
        }
        ClientMetadata meta = fetchMetadata(clientId);
        if (meta == null || !validate(meta, clientId)) {
            res.setStatus(401);
            return false;
        }
        req.setAttribute("clientName", meta.clientName());
        return true;
    }

    // host 白名单是防 SSRF 的第一道关：只 GET 你认可的 host
    private boolean isHostAllowed(String url) {
        String host = hostOf(url);
        if (host == null || !allowedHosts.contains(host)) return false;
        if (allowPrivate) return true;
        try {
            InetAddress addr = InetAddress.getByName(host);
            return !(addr.isSiteLocalAddress() || addr.isLoopbackAddress() || addr.isLinkLocalAddress());
        } catch (Exception e) {
            return false;
        }
    }

    private ClientMetadata fetchMetadata(String url) {
        ClientMetadata cached = cache.getIfPresent(url);
        if (cached != null) return cached;
        try {
            ClientMetadata meta = rest.getForObject(url, ClientMetadata.class);
            if (meta != null) cache.put(url, meta);
            return meta;
        } catch (Exception e) {
            return null; // 元数据拉不到直接拒
        }
    }

    private boolean validate(ClientMetadata m, String clientId) {
        if (m.redirectUris() == null || m.redirectUris().isEmpty()) return false;
        if (m.clientName() == null || m.clientName().isBlank()) return false;
        return true;
    }

    private String hostOf(String url) {
        try {
            return URI.create(url).getHost();
        } catch (Exception e) {
            return null;
        }
    }

    // RestTemplate 必须设超时，否则恶意/慢 URL 会拖满容器线程
    private static RestTemplate buildRestTemplate(int connectMs, int readMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectMs);
        factory.setReadTimeout(readMs);
        return new RestTemplate(factory);
    }
}
