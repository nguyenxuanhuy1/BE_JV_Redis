package com.nxh.redis.service;

public interface RefreshTokenService {
    /**
     * Tạo Refresh Token mới cho User trên Device cụ thể, lưu DB và Redis.
     * Trả về chuỗi raw token chứa siêu dữ liệu để client định danh ở cuộc gọi sau.
     */
    String createRefreshToken(Long userId, String deviceInfo, String ipAddress);

    /**
     * Thực hiện xoay vòng (rotation) Refresh Token khi nhận được token cũ từ Cookie.
     * Sử dụng cơ chế khóa bi quan và kiểm tra chống tái sử dụng (Reuse Detection).
     */
    String rotateRefreshToken(String rawCookieToken, String requestDeviceInfo, String ipAddress);

    /**
     * Thu hồi token của thiết bị hiện tại (Xóa DB và Redis).
     */
    void revokeRefreshToken(Long userId, String deviceInfo);

    /**
     * Thu hồi toàn bộ token của User (DB và Redis).
     */
    void revokeAllUserTokens(Long userId);
}
