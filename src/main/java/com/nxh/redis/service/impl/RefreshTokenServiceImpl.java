package com.nxh.redis.service.impl;

import com.nxh.redis.entity.User;
import com.nxh.redis.entity.UserRefreshToken;
import com.nxh.redis.exception.AppException;
import com.nxh.redis.exception.ErrorCode;
import com.nxh.redis.repository.UserRepository;
import com.nxh.redis.repository.UserRefreshTokenRepository;
import com.nxh.redis.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final UserRefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    @Override
    @Transactional
    public String createRefreshToken(Long userId, String deviceInfo, String ipAddress) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        String rawUuid = UUID.randomUUID().toString();
        String tokenHash = passwordEncoder.encode(rawUuid);
        LocalDateTime expiresAt = LocalDateTime.now().plus(Duration.ofMillis(refreshExpiration));

        UserRefreshToken token = refreshTokenRepository.findByUserIdAndDeviceInfo(userId, deviceInfo)
                .orElseGet(() -> UserRefreshToken.builder()
                        .user(user)
                        .deviceInfo(deviceInfo)
                        .build());

        token.setTokenHash(tokenHash);
        token.setIpAddress(ipAddress);
        token.setVersion(token.getVersion() + 1);
        token.setRevoked(false);
        token.setExpiresAt(expiresAt);
        token.setLastUsedAt(LocalDateTime.now());

        refreshTokenRepository.save(token);

        // Redis Fast-path
        saveToRedis(userId, deviceInfo, token.getVersion(), rawUuid);

        // Định dạng Cookie value: {userId}:{version}:{rawUuid}
        return userId + ":" + token.getVersion() + ":" + rawUuid;
    }

    @Override
    @Transactional
    public String rotateRefreshToken(String rawCookieToken, String requestDeviceInfo, String ipAddress) {
        // 1. Phân tích token từ Cookie
        if (rawCookieToken == null || !rawCookieToken.contains(":")) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        String[] parts = rawCookieToken.split(":");
        if (parts.length != 3) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        Long userId;
        int clientVersion;
        String rawUuid;
        try {
            userId = Long.parseLong(parts[0]);
            clientVersion = Integer.parseInt(parts[1]);
            rawUuid = parts[2];
        } catch (NumberFormatException e) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        // 2. Fast-path check qua Redis
        boolean isRedisVerified = false;
        if (stringRedisTemplate != null) {
            try {
                String redisKey = "refresh_token:" + userId + ":" + requestDeviceInfo;
                String cachedValue = stringRedisTemplate.opsForValue().get(redisKey);
                if (cachedValue != null && cachedValue.contains(":")) {
                    String[] redisParts = cachedValue.split(":");
                    int cachedVersion = Integer.parseInt(redisParts[0]);
                    String cachedUuid = redisParts[1];

                    // Phát hiện Tái sử dụng (Reuse) dựa trên phiên bản phiên làm việc cũ
                    if (clientVersion < cachedVersion) {
                        log.warn("Phát hiện tái sử dụng Refresh Token từ Redis (client version: {}, cached version: {}). Vô hiệu hóa toàn bộ session!", clientVersion, cachedVersion);
                        revokeAllUserTokens(userId);
                        throw new AppException(ErrorCode.UNAUTHORIZED);
                    }

                    if (cachedUuid.equals(rawUuid) && clientVersion == cachedVersion) {
                        isRedisVerified = true;
                    }
                }
            } catch (AppException e) {
                throw e;
            } catch (Exception e) {
                log.error("Lỗi khi kiểm tra Redis fast-path: {}", e.getMessage());
            }
        }

        // 3. Truy vấn DB với PESSIMISTIC_WRITE lock
        UserRefreshToken token = refreshTokenRepository.findByUserIdAndDeviceInfoWithLock(userId, requestDeviceInfo)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED));

        // 4. Phát hiện Tái sử dụng (Reuse Detection) trên DB
        if (token.isRevoked()) {
            log.warn("Phát hiện Refresh Token đã bị thu hồi từ trước trên DB. Vô hiệu hóa toàn bộ session của User ID: {}", userId);
            revokeAllUserTokens(userId);
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        if (clientVersion < token.getVersion()) {
            log.warn("Phát hiện Tái sử dụng Refresh Token phiên bản cũ trên DB (client version: {}, DB version: {}). Vô hiệu hóa toàn bộ session!", clientVersion, token.getVersion());
            revokeAllUserTokens(userId);
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        // 5. Nếu không khớp thông qua Redis fast-path, xác thực bằng BCrypt trên DB
        if (!isRedisVerified) {
            if (!passwordEncoder.matches(rawUuid, token.getTokenHash())) {
                log.warn("Token UUID không khớp với mã băm trong DB. Từ chối xác thực.");
                throw new AppException(ErrorCode.UNAUTHORIZED);
            }
        }

        // 6. Kiểm tra hết hạn (Expiration check)
        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            log.warn("Refresh Token đã hết hạn.");
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        // 7. Xoay vòng (Rotation)
        String newUuid = UUID.randomUUID().toString();
        String newTokenHash = passwordEncoder.encode(newUuid);
        LocalDateTime newExpiresAt = LocalDateTime.now().plus(Duration.ofMillis(refreshExpiration));

        token.setTokenHash(newTokenHash);
        token.setIpAddress(ipAddress);
        token.setVersion(token.getVersion() + 1);
        token.setExpiresAt(newExpiresAt);
        token.setLastUsedAt(LocalDateTime.now());

        refreshTokenRepository.save(token);

        // Cập nhật Redis
        saveToRedis(userId, requestDeviceInfo, token.getVersion(), newUuid);

        return userId + ":" + token.getVersion() + ":" + newUuid;
    }

    @Override
    @Transactional
    public void revokeRefreshToken(Long userId, String deviceInfo) {
        refreshTokenRepository.findByUserIdAndDeviceInfo(userId, deviceInfo)
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                });

        // Xóa khỏi Redis
        try {
            if (stringRedisTemplate != null) {
                String redisKey = "refresh_token:" + userId + ":" + deviceInfo;
                stringRedisTemplate.delete(redisKey);
            }
        } catch (Exception e) {
            log.error("Lỗi khi xóa Refresh Token khỏi Redis (revoke): {}", e.getMessage());
        }
    }

    @Override
    @Transactional
    public void revokeAllUserTokens(Long userId) {
        refreshTokenRepository.revokeAllByUserId(userId);

        // Xóa toàn bộ keys của User trên Redis
        try {
            if (stringRedisTemplate != null) {
                String pattern = "refresh_token:" + userId + ":*";
                Set<String> keys = stringRedisTemplate.keys(pattern);
                if (keys != null && !keys.isEmpty()) {
                    stringRedisTemplate.delete(keys);
                }
            }
        } catch (Exception e) {
            log.error("Lỗi khi xóa toàn bộ Refresh Tokens khỏi Redis (revokeAll): {}", e.getMessage());
        }
    }

    private void saveToRedis(Long userId, String deviceInfo, int version, String rawUuid) {
        try {
            if (stringRedisTemplate != null) {
                String redisKey = "refresh_token:" + userId + ":" + deviceInfo;
                String value = version + ":" + rawUuid;
                stringRedisTemplate.opsForValue().set(redisKey, value, Duration.ofMillis(refreshExpiration));
            }
        } catch (Exception e) {
            log.error("Lỗi khi ghi Refresh Token vào Redis (Fail-Safe): {}", e.getMessage());
        }
    }
}
