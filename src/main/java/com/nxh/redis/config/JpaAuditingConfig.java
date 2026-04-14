package com.nxh.redis.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Kích hoạt JPA Auditing để @CreatedDate và @LastModifiedDate tự động được điền.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
