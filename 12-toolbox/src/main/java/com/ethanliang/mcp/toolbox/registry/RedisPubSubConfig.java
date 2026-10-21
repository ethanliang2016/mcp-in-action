package com.ethanliang.mcp.toolbox.registry;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Redis pub/sub（profile=redis）：订阅 toolbox-events，收到注册/弃用失效事件即记录。
 * 配合 RedisToolboxRegistry「读统一走 Redis」——本监听用于演示"跨实例失效通知"机制，
 * 真实落地可在此触发本地缓存失效或审计。
 */
@Configuration
@Profile("redis")
public class RedisPubSubConfig {

    @Bean
    public RedisMessageListenerContainer toolboxEventListener(RedisConnectionFactory cf) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(cf);
        container.addMessageListener(
                (message, pattern) -> System.out.println("[redis-pubsub] 收到失效事件: " + message.toString()),
                new ChannelTopic("toolbox-events"));
        return container;
    }
}
