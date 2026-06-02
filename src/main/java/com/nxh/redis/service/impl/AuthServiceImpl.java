package com.nxh.redis.service.impl;

import com.nxh.redis.dto.auth.AuthRequest;
import com.nxh.redis.dto.auth.RefreshRequest;
import com.nxh.redis.entity.User;
import com.nxh.redis.enums.Role;
import com.nxh.redis.exception.AppException;
import com.nxh.redis.exception.ErrorCode;
import com.nxh.redis.repository.UserRepository;
import com.nxh.redis.security.JwtService;
import com.nxh.redis.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Date;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    // ── Key prefix constants ──
    private static final String VERSION_PREFIX   = "user:version:";
    private static final String BLACKLIST_PREFIX = "auth:blacklist:";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    /**
     * Inject optional — chỉ có khi chạy với profile "redis".
     * Khi null, tất cả Redis operation bị bỏ qua, app vẫn chạy bình thường.
     */
    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void register(AuthRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new AppException(ErrorCode.USER_ALREADY_EXISTS);
        }

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.USER)
                .build();

        userRepository.save(user);
    }

    @Override
    public User authenticate(AuthRequest request) {
        // 1. Xác thực username/password
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        // 2. Load user từ DB
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        // 3. Đồng bộ tokenVersion vào Redis (ghi đè nếu đã tồn tại)
        try {
            if (redisTemplate != null) {
                redisTemplate.opsForValue().set(
                        VERSION_PREFIX + user.getId(),
                        user.getTokenVersion(),
                        Duration.ofDays(8)   // TTL > refresh token (7 ngày) để tránh miss
                );
            }
        } catch (Exception e) {
            log.error("Lỗi đồng bộ tokenVersion lên Redis khi đăng nhập: {}", e.getMessage());
        }

        return user;
    }

    /**
     * Logout đơn thiết bị: blacklist jti của access token với TTL = thời gian còn lại của token.
     * Redis sẽ tự xoá khi token thực sự hết hạn → không lãng phí RAM.
     */
    @Override
    public void logout(String accessToken) {
        if (redisTemplate == null) return;  // Redis không bật → bỏ qua

        // Token đã hết hạn → không cần blacklist
        if (!jwtService.isTokenNotExpired(accessToken)) return;

        String jti;
        Date expiration;
        try {
            jti        = jwtService.extractJti(accessToken);
            expiration   = jwtService.extractExpiration(accessToken);
        } catch (Exception e) {
            // Lỗi khi parse token → coi như đã logout thành công (token không hợp lệ)
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        long ttlSeconds   = (expiration.getTime() - System.currentTimeMillis()) / 1000;

        if (ttlSeconds > 0) {
            try {
                redisTemplate.opsForValue().set(
                        BLACKLIST_PREFIX + jti,
                        "1",
                        Duration.ofSeconds(ttlSeconds)
                );
            } catch (Exception e) {
                log.error("Lỗi khi thêm token vào blacklist trên Redis: {}", e.getMessage());
            }
        }
    }

    /**
     * Logout toàn bộ thiết bị: tăng tokenVersion trong DB rồi cập nhật Redis.
     * Mọi token cũ (version nhỏ hơn) sẽ bị filter từ chối.
     */
    @Override
    @Transactional
    public void logoutAllDevices(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        // Tăng version trong DB
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);

        // Cập nhật version mới vào Redis
        try {
            if (redisTemplate != null) {
                redisTemplate.opsForValue().set(
                        VERSION_PREFIX + userId,
                        user.getTokenVersion(),
                        Duration.ofDays(8)
                );
            }
        } catch (Exception e) {
            log.error("Lỗi cập nhật token version lên Redis khi logout-all: {}", e.getMessage());
        }
    }
}
