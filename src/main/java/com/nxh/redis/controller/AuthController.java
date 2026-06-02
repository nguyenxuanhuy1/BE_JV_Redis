package com.nxh.redis.controller;

import com.nxh.redis.dto.ApiResponse;
import com.nxh.redis.dto.auth.AuthRequest;
import com.nxh.redis.dto.auth.AuthResponse;
import com.nxh.redis.entity.User;
import com.nxh.redis.exception.AppException;
import com.nxh.redis.exception.ErrorCode;
import com.nxh.redis.repository.UserRepository;
import com.nxh.redis.security.JwtService;
import com.nxh.redis.service.AuthService;
import com.nxh.redis.service.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final JwtService jwtService;
    private final UserRepository userRepository;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Void>> register(
            @Valid @RequestBody AuthRequest request
    ) {
        authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Đăng ký thành công", null));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody AuthRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        User user = authService.authenticate(request);
        String accessToken = jwtService.generateAccessToken(user);

        String deviceId = getDeviceId(httpRequest);
        String ip = httpRequest.getRemoteAddr();
        String refreshTokenCookieValue = refreshTokenService.createRefreshToken(user.getId(), deviceId, ip);

        setRefreshTokenCookie(httpResponse, refreshTokenCookieValue);

        AuthResponse data = AuthResponse.builder()
                .token(accessToken)
                .build();

        return ResponseEntity.ok(ApiResponse.success("Đăng nhập thành công", data));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        String cookieToken = getRefreshTokenFromCookie(httpRequest);
        if (cookieToken == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        String deviceId = getDeviceId(httpRequest);
        String ip = httpRequest.getRemoteAddr();

        // 1. Thực hiện xoay vòng token trong service
        String newCookieToken = refreshTokenService.rotateRefreshToken(cookieToken, deviceId, ip);

        // 2. Trả về cookie mới
        setRefreshTokenCookie(httpResponse, newCookieToken);

        // 3. Load user từ DB và sinh Access Token mới
        Long userId = Long.parseLong(newCookieToken.split(":")[0]);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        String newAccessToken = jwtService.generateAccessToken(user);

        AuthResponse data = AuthResponse.builder()
                .token(newAccessToken)
                .build();

        return ResponseEntity.ok(ApiResponse.success("Làm mới token thành công", data));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        String cookieToken = getRefreshTokenFromCookie(httpRequest);
        if (cookieToken != null && cookieToken.contains(":")) {
            try {
                Long userId = Long.parseLong(cookieToken.split(":")[0]);
                String deviceId = getDeviceId(httpRequest);
                refreshTokenService.revokeRefreshToken(userId, deviceId);
            } catch (Exception ignored) {
            }
        }

        String authHeader = httpRequest.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                authService.logout(authHeader.substring(7));
            } catch (Exception ignored) {
            }
        }

        deleteRefreshTokenCookie(httpResponse);
        return ResponseEntity.ok(ApiResponse.success("Đăng xuất thành công", null));
    }

    @PostMapping("/logout-all")
    public ResponseEntity<ApiResponse<Void>> logoutAll(
            @AuthenticationPrincipal User currentUser,
            HttpServletResponse httpResponse
    ) {
        refreshTokenService.revokeAllUserTokens(currentUser.getId());
        authService.logoutAllDevices(currentUser.getId());

        deleteRefreshTokenCookie(httpResponse);
        return ResponseEntity.ok(ApiResponse.success("Đăng xuất toàn bộ thiết bị thành công", null));
    }

    // ---- Helper Methods ----

    private String getDeviceId(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        String ip = request.getRemoteAddr();
        String raw = (userAgent != null ? userAgent : "") + "|" + (ip != null ? ip : "");
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return "default_device";
        }
    }

    private String getRefreshTokenFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (var cookie : request.getCookies()) {
            if ("refresh_token".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String value) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(7 * 24 * 60 * 60) // 7 ngày
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void deleteRefreshTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
