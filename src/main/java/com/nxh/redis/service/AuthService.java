package com.nxh.redis.service;

import com.nxh.redis.dto.auth.AuthRequest;
import com.nxh.redis.dto.auth.AuthResponse;
import com.nxh.redis.dto.auth.RefreshRequest;

public interface AuthService {
    void register(AuthRequest request);
    AuthResponse login(AuthRequest request);
    AuthResponse refresh(RefreshRequest request);
}
