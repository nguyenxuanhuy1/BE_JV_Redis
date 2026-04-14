package com.nxh.redis.service.impl;

import com.nxh.redis.dto.auth.AuthRequest;
import com.nxh.redis.dto.auth.AuthResponse;
import com.nxh.redis.dto.auth.RefreshRequest;
import com.nxh.redis.entity.User;
import com.nxh.redis.enums.Role;
import com.nxh.redis.exception.AppException;
import com.nxh.redis.exception.ErrorCode;
import com.nxh.redis.repository.UserRepository;
import com.nxh.redis.security.JwtService;
import com.nxh.redis.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

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
    public AuthResponse login(AuthRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        String token        = jwtService.generateToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        return AuthResponse.builder()
                .token(token)
                .refreshToken(refreshToken)
                .build();
    }
    @Override
    public AuthResponse refresh(RefreshRequest request) {
        String refreshToken = request.getRefreshToken();

        // 1. Kiểm tra token có hợp lệ không (chữ ký + hạn dùng)
        if (!jwtService.isTokenNotExpired(refreshToken)) {
            throw new AppException(ErrorCode.REFRESH_TOKEN_EXPIRED);
        }

        // 2. Lấy username từ refresh token
        String username = jwtService.extractUsername(refreshToken);

        // 3. Load user từ DB
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        // 4. Sinh access token mới + rotate refresh token
        String newToken        = jwtService.generateToken(user);
        String newRefreshToken = jwtService.generateRefreshToken(user);

        return AuthResponse.builder()
                .token(newToken)
                .refreshToken(newRefreshToken)
                .build();
    }
}
