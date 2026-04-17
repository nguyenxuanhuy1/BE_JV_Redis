//package com.nxh.redis.config;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.fasterxml.jackson.databind.SerializationFeature;
//import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
//import io.lettuce.core.cluster.ClusterClientOptions;
//import io.lettuce.core.cluster.ClusterTopologyRefreshOptions;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.cache.CacheManager;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.context.annotation.Profile;
//import org.springframework.data.redis.cache.RedisCacheConfiguration;
//import org.springframework.data.redis.cache.RedisCacheManager;
//import org.springframework.data.redis.connection.RedisClusterConfiguration;
//import org.springframework.data.redis.connection.RedisConnectionFactory;
//import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
//import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
//import org.springframework.data.redis.core.RedisTemplate;
//import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
//import org.springframework.data.redis.serializer.RedisSerializationContext;
//import org.springframework.data.redis.serializer.StringRedisSerializer;
//
//import java.time.Duration;
//import java.util.Arrays;
//import java.util.List;
//
//@Configuration
//@Profile("redis") // bật lại: chạy với --spring.profiles.active=redis
//public class RedisConfig {
//
//        @Value("${spring.data.redis.cluster.nodes}")
//        private String clusterNodes;
//
//        @Value("${spring.data.redis.cluster.max-redirects:3}")
//        private int maxRedirects;
//
//        @Bean
//        public RedisClusterConfiguration redisClusterConfiguration() {
//                List<String> nodes = Arrays.asList(clusterNodes.split(","));
//                RedisClusterConfiguration config = new RedisClusterConfiguration(nodes);
//                config.setMaxRedirects(maxRedirects);
//                return config;
//        }
//
//        @Bean
//        public LettuceConnectionFactory redisConnectionFactory() {
//                ClusterTopologyRefreshOptions topologyRefreshOptions = ClusterTopologyRefreshOptions.builder()
//                                .enableAdaptiveRefreshTrigger(
//                                                ClusterTopologyRefreshOptions.RefreshTrigger.MOVED_REDIRECT,
//                                                ClusterTopologyRefreshOptions.RefreshTrigger.PERSISTENT_RECONNECTS)
//                                .adaptiveRefreshTriggersTimeout(Duration.ofSeconds(10))
//                                .enablePeriodicRefresh(Duration.ofSeconds(30))
//                                .build();
//
//                ClusterClientOptions clientOptions = ClusterClientOptions.builder()
//                                .topologyRefreshOptions(topologyRefreshOptions)
//                                .validateClusterNodeMembership(false)
//                                .build();
//
//                LettuceClientConfiguration lettuceConfig = LettuceClientConfiguration.builder()
//                                .clientOptions(clientOptions)
//                                .commandTimeout(Duration.ofSeconds(5))
//                                .build();
//
//                return new LettuceConnectionFactory(redisClusterConfiguration(), lettuceConfig);
//        }
//
//        /**
//         * ObjectMapper riêng cho Redis — kích hoạt DefaultTyping để serialize/deserialize
//         * đúng kiểu cụ thể (Integer, Long, v.v.) thay vì bị ép thành LinkedHashMap.
//         */
//        private ObjectMapper redisObjectMapper() {
//                ObjectMapper mapper = new ObjectMapper();
//                mapper.registerModule(new JavaTimeModule());
//                mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
//                // Nhúng thông tin kiểu vào JSON để deserialize đúng class
//                mapper.activateDefaultTyping(
//                        mapper.getPolymorphicTypeValidator(),
//                        ObjectMapper.DefaultTyping.NON_FINAL
//                );
//                return mapper;
//        }
//
//        /**
//         * RedisTemplate<String, Object> để thao tác key-value thủ công:
//         * - Key   : StringRedisSerializer  (plain string)
//         * - Value : GenericJackson2JsonRedisSerializer  (JSON + type info)
//         */
//        @Bean
//        public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
//                GenericJackson2JsonRedisSerializer jsonSerializer =
//                        new GenericJackson2JsonRedisSerializer(redisObjectMapper());
//
//                RedisTemplate<String, Object> template = new RedisTemplate<>();
//                template.setConnectionFactory(connectionFactory);
//                template.setKeySerializer(new StringRedisSerializer());
//                template.setHashKeySerializer(new StringRedisSerializer());
//                template.setValueSerializer(jsonSerializer);
//                template.setHashValueSerializer(jsonSerializer);
//                template.afterPropertiesSet();
//                return template;
//        }
//
//        /**
//         * CacheManager cho @Cacheable / @CacheEvict — dùng JSON serializer.
//         * TTL mặc định 30 phút; từng cache có thể override qua cấu hình riêng.
//         */
//        @Bean
//        public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
//                GenericJackson2JsonRedisSerializer jsonSerializer =
//                        new GenericJackson2JsonRedisSerializer(redisObjectMapper());
//
//                RedisCacheConfiguration cacheConfig = RedisCacheConfiguration.defaultCacheConfig()
//                        .entryTtl(Duration.ofMinutes(30))
//                        .serializeKeysWith(RedisSerializationContext.SerializationPair
//                                .fromSerializer(new StringRedisSerializer()))
//                        .serializeValuesWith(RedisSerializationContext.SerializationPair
//                                .fromSerializer(jsonSerializer))
//                        .disableCachingNullValues();
//
//                return RedisCacheManager.builder(connectionFactory)
//                        .cacheDefaults(cacheConfig)
//                        .build();
//        }
//}
