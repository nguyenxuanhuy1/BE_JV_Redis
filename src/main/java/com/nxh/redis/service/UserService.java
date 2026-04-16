package com.nxh.redis.service;

import com.nxh.redis.dto.user.UserResponse;
import com.nxh.redis.entity.User;

public interface UserService {
    UserResponse getMe(User currentUser);
}
