package com.nxh.redis.service;

import com.nxh.redis.dto.auth.AuthRequest;
import com.nxh.redis.dto.auth.AuthResponse;
import com.nxh.redis.dto.auth.RefreshRequest;

public interface AuthService {
    void register(AuthRequest request);
    AuthResponse login(AuthRequest request);
    AuthResponse refresh(RefreshRequest request);

    /**
     * Blacklist jti của access token hiện tại — logout đơn thiết bị.
     */
    void logout(String accessToken);

    /**
     * Tăng tokenVersion trong DB + Redis — logout toàn bộ thiết bị.
     */
    void logoutAllDevices(Long userId);
}
