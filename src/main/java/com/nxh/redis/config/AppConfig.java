package com.nxh.redis.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    /**
     * Cấu hình ObjectMapper với JavaTimeModule để serialize/deserialize
     * LocalDate, LocalDateTime đúng định dạng ISO-8601.
     * Spring Boot 3.x tự đăng ký module này nhưng khai báo tường minh
     * giúp dễ customize sau (disable WRITE_DATES_AS_TIMESTAMPS, v.v.)
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
