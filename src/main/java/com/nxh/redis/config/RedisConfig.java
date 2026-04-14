package com.nxh.redis.config;

import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

@Configuration
@Profile("redis") // bật lại: chạy với --spring.profiles.active=redis
public class RedisConfig {

        @Value("${spring.data.redis.cluster.nodes}")
        private String clusterNodes;

        @Value("${spring.data.redis.cluster.max-redirects:3}")
        private int maxRedirects;

        @Bean
        public RedisClusterConfiguration redisClusterConfiguration() {
                List<String> nodes = Arrays.asList(clusterNodes.split(","));
                RedisClusterConfiguration config = new RedisClusterConfiguration(nodes);
                config.setMaxRedirects(maxRedirects);
                return config;
        }

        @Bean
        public LettuceConnectionFactory redisConnectionFactory() {
                ClusterTopologyRefreshOptions topologyRefreshOptions = ClusterTopologyRefreshOptions.builder()
                                .enableAdaptiveRefreshTrigger(
                                                ClusterTopologyRefreshOptions.RefreshTrigger.MOVED_REDIRECT,
                                                ClusterTopologyRefreshOptions.RefreshTrigger.PERSISTENT_RECONNECTS)
                                .adaptiveRefreshTriggersTimeout(Duration.ofSeconds(10))
                                .enablePeriodicRefresh(Duration.ofSeconds(30))
                                .build();

                ClusterClientOptions clientOptions = ClusterClientOptions.builder()
                                .topologyRefreshOptions(topologyRefreshOptions)
                                .validateClusterNodeMembership(false)
                                .build();

                LettuceClientConfiguration lettuceConfig = LettuceClientConfiguration.builder()
                                .clientOptions(clientOptions)
                                .commandTimeout(Duration.ofSeconds(5))
                                .build();

                return new LettuceConnectionFactory(redisClusterConfiguration(), lettuceConfig);
        }
}
