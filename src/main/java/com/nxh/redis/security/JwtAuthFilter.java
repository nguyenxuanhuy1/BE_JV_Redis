package com.nxh.redis.security;

import com.nxh.redis.entity.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BLACKLIST_PREFIX = "auth:blacklist:";
    private static final String VERSION_PREFIX   = "user:version:";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

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

        // 1. Extract username
        final String username;
        try {
            username = jwtService.extractUsername(jwt);
        } catch (Exception e) {
            log.error("Không thể giải mã Token: {}", e.getMessage());
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Kiểm tra Blacklist (Chốt chặn logout cho token cụ thể)
        if (redisTemplate != null) {
            try {
                String jti = jwtService.extractJti(jwt);
                if (jti != null && Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + jti))) {
                    sendUnauthorized(response, "TOKEN_BLACKLISTED", "Token đã bị thu hồi, vui lòng đăng nhập lại");
                    return;
                }
            } catch (Exception e) {
                log.warn("Redis lỗi khi check blacklist (Fail-Open): {}", e.getMessage());
            }
        }

        // 3. Load user + validate token
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            if (jwtService.isTokenValid(jwt, userDetails)) {

                // 4. Kiểm tra Token Version (Đăng xuất từ xa / Đổi mật khẩu)
                if (redisTemplate != null && userDetails instanceof User user) {
                    try {
                        if (!isVersionValid(jwt, user)) {
                            sendUnauthorized(response, "TOKEN_VERSION_INVALID",
                                    "Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại");
                            return;
                        }
                    } catch (Exception e) {
                        log.warn("Redis lỗi khi check version (Fail-Open): {}", e.getMessage());
                    }
                }

                // 5. Set Authentication
                var authToken = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Kiểm tra version trong Token có khớp với version hiện tại không.
     */
    private boolean isVersionValid(String jwt, User user) {
        Integer tokenVersion = jwtService.extractVersion(jwt);
        if (tokenVersion == null) return false;

        String redisKey = VERSION_PREFIX + user.getId();
        Object cached = redisTemplate.opsForValue().get(redisKey);

        int currentVersion;
        if (cached != null) {
            // Xử lý an toàn khi lấy dữ liệu từ Redis (có thể là String hoặc Integer)
            if (cached instanceof Number number) {
                currentVersion = number.intValue();
            } else {
                currentVersion = Integer.parseInt(cached.toString());
            }
        } else {
            // Cache miss -> Warm up từ DB (User entity đã được load bởi userDetailsService)
            currentVersion = user.getTokenVersion();
            redisTemplate.opsForValue().set(redisKey, currentVersion, Duration.ofDays(8));
        }

        // Nếu version trong Token KHÁC version mới nhất -> Token không còn hiệu lực
        return tokenVersion == currentVersion;
    }

    private void sendUnauthorized(HttpServletResponse response, String code, String message)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        // Format đúng chuẩn ApiResponse của bạn
        String jsonResponse = String.format(
                "{\"success\":false,\"code\":\"%s\",\"message\":\"%s\",\"data\":null}",
                code, message);
        response.getWriter().write(jsonResponse);
    }
}