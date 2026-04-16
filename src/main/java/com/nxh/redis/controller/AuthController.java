package com.nxh.redis.controller;

import com.nxh.redis.dto.ApiResponse;
import com.nxh.redis.dto.auth.AuthRequest;
import com.nxh.redis.dto.auth.AuthResponse;
import com.nxh.redis.dto.auth.RefreshRequest;
import com.nxh.redis.entity.User;
import com.nxh.redis.exception.AppException;
import com.nxh.redis.exception.ErrorCode;
import com.nxh.redis.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Void>> register(
            @Valid @RequestBody AuthRequest request) {
        authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Đăng ký thành công", null));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody AuthRequest request) {
        AuthResponse data = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Đăng nhập thành công", data));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @Valid @RequestBody RefreshRequest request) {
        AuthResponse data = authService.refresh(request);
        return ResponseEntity.ok(ApiResponse.success("Làm mới token thành công", data));
    }

    /**
     * Logout đơn thiết bị — yêu cầu Bearer token hợp lệ.
     * Token được blacklist; mọi request tiếp theo dùng token này sẽ bị từ chối.
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        authService.logout(authHeader.substring(7));
        return ResponseEntity.ok(ApiResponse.success("Đăng xuất thành công", null));
    }

    /**
     * Logout toàn bộ thiết bị — tăng tokenVersion, vô hiệu mọi token đang lưu hành.
     * Lấy userId từ Principal được filter inject vào SecurityContext.
     */
    @PostMapping("/logout-all")
    public ResponseEntity<ApiResponse<Void>> logoutAll(
            @AuthenticationPrincipal User currentUser) {
        authService.logoutAllDevices(currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success("Đăng xuất toàn bộ thiết bị thành công", null));
    }
}
