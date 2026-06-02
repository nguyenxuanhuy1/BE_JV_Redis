package com.nxh.redis.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nxh.redis.dto.auth.AuthRequest;
import com.nxh.redis.entity.User;
import com.nxh.redis.enums.Role;
import com.nxh.redis.exception.AppException;
import com.nxh.redis.exception.ErrorCode;
import com.nxh.redis.repository.UserRepository;
import com.nxh.redis.security.JwtService;
import com.nxh.redis.service.AuthService;
import com.nxh.redis.service.RefreshTokenService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false) // Tắt spring security filter để đơn giản hóa việc test logic controller
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private RefreshTokenService refreshTokenService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    private User mockUser;

    @BeforeEach
    void setUp() {
        mockUser = User.builder()
                .username("testuser")
                .password("password123")
                .role(Role.USER)
                .enabled(true)
                .tokenVersion(1)
                .build();
        mockUser.setId(1L);
    }

    @Test
    void register_Success() throws Exception {
        AuthRequest request = new AuthRequest();
        request.setUsername("newuser");
        request.setPassword("password123");

        doNothing().when(authService).register(any(AuthRequest.class));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Đăng ký thành công"));
    }

    @Test
    void login_Success() throws Exception {
        AuthRequest request = new AuthRequest();
        request.setUsername("testuser");
        request.setPassword("password123");

        when(authService.authenticate(any(AuthRequest.class))).thenReturn(mockUser);
        when(jwtService.generateAccessToken(any(User.class))).thenReturn("mock-access-token");
        when(refreshTokenService.createRefreshToken(anyLong(), anyString(), anyString())).thenReturn("1:1:mock-uuid");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Đăng nhập thành công"))
                .andExpect(jsonPath("$.data.token").value("mock-access-token"))
                .andExpect(cookie().exists("refresh_token"))
                .andExpect(cookie().value("refresh_token", "1:1:mock-uuid"))
                .andExpect(cookie().httpOnly("refresh_token", true));
    }

    @Test
    void refresh_Success() throws Exception {
        Cookie cookie = new Cookie("refresh_token", "1:1:mock-uuid");

        when(refreshTokenService.rotateRefreshToken(anyString(), anyString(), anyString())).thenReturn("1:2:new-mock-uuid");
        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(jwtService.generateAccessToken(any(User.class))).thenReturn("new-mock-access-token");

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Làm mới token thành công"))
                .andExpect(jsonPath("$.data.token").value("new-mock-access-token"))
                .andExpect(cookie().exists("refresh_token"))
                .andExpect(cookie().value("refresh_token", "1:2:new-mock-uuid"));
    }

    @Test
    void refresh_NoCookie_ThrowsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void logout_Success() throws Exception {
        Cookie cookie = new Cookie("refresh_token", "1:1:mock-uuid");
        doNothing().when(refreshTokenService).revokeRefreshToken(anyLong(), anyString());
        doNothing().when(authService).logout(anyString());

        mockMvc.perform(post("/api/auth/logout")
                        .cookie(cookie)
                        .header("Authorization", "Bearer mock-access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Đăng xuất thành công"))
                .andExpect(cookie().maxAge("refresh_token", 0));
    }

    @Test
    void logoutAll_Success() throws Exception {
        doNothing().when(refreshTokenService).revokeAllUserTokens(anyLong());
        doNothing().when(authService).logoutAllDevices(anyLong());

        UsernamePasswordAuthenticationToken principal =
                new UsernamePasswordAuthenticationToken(mockUser, null, mockUser.getAuthorities());

        SecurityContextHolder.setContext(new SecurityContextImpl(principal));

        try {
            mockMvc.perform(post("/api/auth/logout-all")
                            .principal(principal))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Đăng xuất toàn bộ thiết bị thành công"))
                    .andExpect(cookie().maxAge("refresh_token", 0));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
