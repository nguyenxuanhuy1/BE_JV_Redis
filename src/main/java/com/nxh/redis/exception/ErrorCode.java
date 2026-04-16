package com.nxh.redis.exception;

public enum ErrorCode {

    // Common
    INTERNAL_ERROR(500, "Internal server error"),
    INVALID_INPUT(400, "Invalid input data"),

    // User
    USER_NOT_FOUND(404, "User not found"),
    USER_ALREADY_EXISTS(400, "User already exists"),

    // Auth
    UNAUTHORIZED(401, "Unauthorized"),
    FORBIDDEN(403, "Access denied"),
    REFRESH_TOKEN_EXPIRED(401, "Refresh token đã hết hạn, vui lòng đăng nhập lại"),
    TOKEN_BLACKLISTED(401, "Token đã bị thu hồi, vui lòng đăng nhập lại"),
    TOKEN_VERSION_INVALID(401, "Phiên đăng nhập đã hết hạn trên thiết bị này, vui lòng đăng nhập lại"),

    // Wheel
    WHEEL_NOT_FOUND(404, "Wheel not found"),
    WHEEL_NO_ITEMS(422, "Wheel has no items"),
    WHEEL_PRESET_INVALID(400, "Preset result is not in wheel items");

    private final int status;
    private final String message;

    ErrorCode(int status, String message) {
        this.status = status;
        this.message = message;
    }

    public int getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
