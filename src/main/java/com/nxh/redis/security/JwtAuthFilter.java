package com.nxh.redis.security;

import com.nxh.redis.entity.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BLACKLIST_PREFIX = "auth:blacklist:";
    private static final String VERSION_PREFIX   = "user:version:";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    /**
     * Inject optional — chỉ có khi chạy với profile "redis".
     * Khi null, bỏ qua kiểm tra blacklist/version (app vẫn khởi động bình thường).
     */
    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);

        // ── Bước 1: Extract username (token không hợp lệ → pass, Spring Security tự chặn) ──
        final String username;
        try {
            username = jwtService.extractUsername(jwt);
        } catch (Exception e) {
            filterChain.doFilter(request, response);
            return;
        }

        // ── Bước 2: Kiểm tra Blacklist ──
        if (redisTemplate != null) {
            try {
                String jti = jwtService.extractJti(jwt);
                Boolean blacklisted = redisTemplate.hasKey(BLACKLIST_PREFIX + jti);
                if (Boolean.TRUE.equals(blacklisted)) {
                    sendUnauthorized(response, "TOKEN_BLACKLISTED",
                            "Token đã bị thu hồi, vui lòng đăng nhập lại");
                    return;
                }
            } catch (Exception ignored) {
                // Lỗi Redis không chặn request — fail open để tránh Redis outage gây service down
            }
        }

        // ── Bước 3: Load user + validate token ──
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            if (jwtService.isTokenValid(jwt, userDetails)) {

                // ── Bước 4: Kiểm tra Token Version (Cache Aside) ──
                if (redisTemplate != null && userDetails instanceof User user) {
                    try {
                        if (!isVersionValid(jwt, user)) {
                            sendUnauthorized(response, "TOKEN_VERSION_INVALID",
                                    "Phiên đăng nhập đã hết hạn trên thiết bị này, vui lòng đăng nhập lại");
                            return;
                        }
                    } catch (Exception ignored) {
                        // Lỗi Redis → bỏ qua kiểm tra version, fail open
                    }
                }

                // ── Bước 5: Set Authentication ──
                var authToken = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * So sánh version trong token với currentVersion trong Redis.
     * Cache Aside: nếu Redis miss → lấy từ DB (User entity đã load) rồi set lại Redis.
     */
    private boolean isVersionValid(String jwt, User user) {
        Integer tokenVersion = jwtService.extractVersion(jwt);
        if (tokenVersion == null) return false;

        String redisKey = VERSION_PREFIX + user.getId();
        Object cached = redisTemplate.opsForValue().get(redisKey);

        int currentVersion;
        if (cached != null) {
            currentVersion = ((Number) cached).intValue();
        } else {
            // Cache miss → lấy từ User entity (đã load từ DB) rồi warm up cache
            currentVersion = user.getTokenVersion();
            redisTemplate.opsForValue().set(redisKey, currentVersion, Duration.ofDays(8));
        }

        return tokenVersion >= currentVersion;
    }

    /**
     * Gửi response 401 JSON trực tiếp và kết thúc filter chain.
     */
    private void sendUnauthorized(HttpServletResponse response, String code, String message)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(String.format(
                "{\"success\":false,\"message\":\"%s\",\"data\":null}", message));
    }
}
