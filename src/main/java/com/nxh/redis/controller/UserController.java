package com.nxh.redis.controller;

import com.nxh.redis.dto.ApiResponse;
import com.nxh.redis.dto.user.UserResponse;
import com.nxh.redis.entity.User;
import com.nxh.redis.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * GET /api/users/me — Trả về thông tin user đang đăng nhập.
     * Yêu cầu: Bearer token hợp lệ trong header Authorization.
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getMe(
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(ApiResponse.success(userService.getMe(currentUser)));
    }
}
