package com.nxh.redis.service.impl;

import com.nxh.redis.dto.user.UserResponse;
import com.nxh.redis.entity.User;
import com.nxh.redis.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    @Override
    public UserResponse getMe(User currentUser) {
        return UserResponse.builder()
                .id(currentUser.getId())
                .username(currentUser.getUsername())
                .role(currentUser.getRole())
                .createdAt(currentUser.getCreatedAt())
                .build();
    }
}
