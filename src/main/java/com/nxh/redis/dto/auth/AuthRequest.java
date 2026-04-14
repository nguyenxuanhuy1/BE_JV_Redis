package com.nxh.redis.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AuthRequest {

        @NotBlank(message = "Tài khoản không được để trống")
        @Size(min = 3, max = 25, message = "Tài khoản phải từ 3 đến 50 ký tự")
        @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "Tài khoản chỉ được chứa chữ cái (a-z, A-Z), số (0-9) và dấu gạch dưới (_)")
        private String username;

        @NotBlank(message = "Mật khẩu không được để trống")
        @Size(min = 6, max = 25, message = "Mật khẩu phải có ít nhất 6 ký tự")
        @Pattern(regexp = "^\\S+$", message = "Mật khẩu không được chứa dấu cách")
        private String password;
}
