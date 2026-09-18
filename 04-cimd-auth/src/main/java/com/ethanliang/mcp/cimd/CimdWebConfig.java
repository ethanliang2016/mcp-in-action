package com.ethanliang.mcp.cimd;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CimdWebConfig implements WebMvcConfigurer {
    private final ClientIdMetadataInterceptor interceptor;

    public CimdWebConfig(ClientIdMetadataInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 只对受保护接口做客户端身份校验，元数据端点本身开放
        registry.addInterceptor(interceptor).addPathPatterns("/api/**");
    }
}
