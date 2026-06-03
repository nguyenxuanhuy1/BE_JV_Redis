# Tài Liệu Bối Cảnh Dự Án (Project Context & Architecture Summary)

Tài liệu này tóm tắt cấu trúc thư mục, kiến trúc phân lớp, các công nghệ sử dụng, luồng bảo mật (Authentication & Session), và các quy tắc thiết kế cốt lõi của dự án **BE_JV_Redis**. Các AI Assistant trong các phiên tiếp theo có thể đọc trực tiếp file này để nắm bắt dự án ngay lập tức.

---

## 1. Thông Tin Chung (General Information)
- **Công nghệ chính**: Spring Boot `3.5.12`, Java `21`, PostgreSQL, Redis (Spring Data Redis, Spring Cache).
- **Security**: Spring Security + JSON Web Token (JJWT `0.12.6`).
- **Database driver**: `postgresql`.
- **Lombok**: Sử dụng để tối giản mã nguồn (hãy cẩn thận các quy tắc tránh đệ quy ở Entity).

---

## 2. Cấu Trúc Thư Mục & Phân Lớp (Package & Directory Structure)
Toàn bộ mã nguồn nằm dưới package gốc [com.nxh.redis](file:///f:/BE/BE_JV_Redis/src/main/java/com/nxh/redis):

- [config](file:///f:/BE/BE_JV_Redis/src/main/java/com/nxh/redis/config): Cấu hình hệ thống.
  - `AppConfig.java`: Cấu hình `BCryptPasswordEncoder`, `AuthenticationManager`, v.v.
  - `CorsConfig.java`: Cấu hình cho phép các domain Frontend kết nối.
  - `JpaAuditingConfig.java`: Bật JPA Auditing (`@CreatedDate`, `@LastModifiedDate`).
  - `RedisConfig.java` & `RedisDevConfig.java`: Cấu hình kết nối Redis (hỗ trợ Cluster và Single node local).
  - `SecurityConfig.java`: Phân quyền endpoint, cấu hình Stateless Session và tiêm `JwtAuthFilter`.
- [controller](file:///f:/BE/BE_JV_Redis/src/main/java/com/nxh/redis/controller): Nhận HTTP Request và trả về dữ liệu chuẩn hóa thông qua `ApiResponse`.
  - `AuthController.java`: Login, register, logout, refresh token.
  - `UserController.java`: Lấy thông tin user hiện tại.
  - `TripController.java`: CRUD trip hỗ trợ phân trang và caching.
- [dto](file:///f:/BE/BE_JV_Redis/src/main/java/com/nxh/redis/dto):
  - `ApiResponse.java`: Cấu trúc response chung `{ success, message, data }`.
  - [auth](file:///f:/BE/BE_JV_Redis/src/main/java/com/nxh/redis/dto/auth), [page](file:///f:/BE/BE_JV_Redis/src/main/java/com/nxh/redis/dto/page), [trip](file:///f:/BE/BE_JV_Redis/src/main/java/com/nxh/redis/dto/trip), [user](file:///f:/BE/BE_JV_Redis/src/main/java/com/nxh/redis/dto/user): Chứa các DTO tương ứng.
- [entity](file:///f:/BE/BE_JV_Redis/src/main/java/com/nxh/redis/entity): Ánh xạ DB.
  - `BaseEntity.java`: Chứa các trường audit chung (`id`, `createdAt`, `updatedAt`).
  - `User.java`: Bảng `users`, lưu thông tin định danh và `tokenVersion` (dành cho per-device logout).
  - `UserRefreshToken.java`: Bảng `user_refresh_tokens`, lưu các session đăng nhập của User trên từng thiết bị.
  - `Trip.java`: Bảng `trips`.
- [enums](file:///f:/BE/BE_JV_Redis/src/main/java/com/nxh/redis/enums):
  - `Role.java`: Quyền hạn (`USER`, `ADMIN`).
- [exception](file:///f:/BE/BE_JV_Redis/src/main/java/com/nxh/redis/exception):
  - `AppException.java`: RuntimeException custom nhận tham số `ErrorCode`.
  - `ErrorCode.java`: Danh sách lỗi và HTTP Status tương ứng.
  - `GlobalExceptionHandler.java`: Bắt mọi exception và format trả về dạng `ErrorResponse`.
- [repository](file:///f:/BE/BE_JV_Redis/src/main/java/com/nxh/redis/repository): Thao tác với DB (JPA).
  - `UserRefreshTokenRepository.java`: Có hỗ trợ Pessimistic Write Lock (`@Lock(LockModeType.PESSIMISTIC_WRITE)`) để chống Race Condition khi xoay vòng token.
- [security](file:///f:/BE/BE_JV_Redis/src/main/java/com/nxh/redis/security):
  - `JwtAuthFilter.java`: Bộ lọc JWT cho mỗi request.
  - `JwtService.java`: Tạo, validate và giải mã Access Token / Refresh Token.
- [service](file:///f:/BE/BE_JV_Redis/src/main/java/com/nxh/redis/service) & [impl](file:///f:/BE/BE_JV_Redis/src/main/java/com/nxh/redis/service/impl): Logic nghiệp vụ.
  - `AuthServiceImpl.java`, `RefreshTokenServiceImpl.java`, `TripServiceImpl.java`, `UserServiceImpl.java`.
- [util](file:///f:/BE/BE_JV_Redis/src/main/java/com/nxh/redis/util):
  - `PagingUtil.java`: Tạo đối tượng `Pageable` đồng bộ.
  - `SortUtils.java`: Phân tích chuỗi sort.

---

## 3. Kiến Trúc Bảo Mật & Quản Lý Phiên (Security & Session Lifecycle)

### A. Access Token
- Chứa thông tin: `subject` (username), `role`, `type` ("access"), `version` (tokenVersion của user), `jti` (UUID định danh duy nhất).
- Cơ chế logout nhanh (Blacklist): Sử dụng Redis để lưu các JTI bị blacklist cho đến khi hết hạn tự nhiên.

### B. Refresh Token & Session Management
- Sử dụng **Cookie HTTP-Only** để lưu trữ Refresh Token. Định dạng giá trị: `{userId}:{version}:{rawUuid}`.
- Cơ chế hoạt động 2 lớp (Hybrid DB & Redis):
  - **Redis Fast-Path (Cache Layer)**: Kiểm tra nhanh phiên bản và UUID lưu tại key `refresh_token:{userId}:{deviceInfo}`.
  - **Database (Pessimistic Write Lock)**: Nếu Redis cache không khớp hoặc bị thiếu, truy vấn từ database bằng cách khóa bản ghi `UserRefreshToken` nhằm tránh race condition khi gọi API song song từ client.
- **Xoay Vòng Refresh Token (Token Rotation)**: Mỗi lần refresh, một UUID mới sẽ được sinh ra, phiên bản (version) tăng lên 1 và ghi đè cả DB và Redis.
- **Phát hiện tái sử dụng (Reuse Detection)**: Nếu Client gửi Refresh Token có phiên bản cũ hơn phiên bản hiện hành (chứng tỏ token cũ đã bị rò rỉ và kẻ gian đang cố gắng replay):
  - Hệ thống sẽ phát hiện và ngay lập tức **thu hồi (revoke) toàn bộ session** của User đó (xóa sạch Redis keys và cập nhật trạng thái `revoked = true` cho mọi token của User trong DB).

---

## 4. Các Quy Tắc Thiết Kế Code Cực Kỳ Quan Trọng (Critical Code Rules)

1. **Kháng Lỗi Redis (Redis Resilience)**:
   - Tất cả các thao tác gọi Redis phải kiểm tra `redisTemplate != null` hoặc được bọc trong block `try-catch` an toàn để hệ thống vẫn tiếp tục chạy độc lập thông qua cơ sở dữ liệu quan hệ nếu Redis gặp sự cố.
2. **Quy Tắc Lombok**:
   - Đối với các **Entity JPA**, **TUYỆT ĐỐI KHÔNG** dùng `@Data` để tránh lỗi đệ quy vòng lặp khi tạo chuỗi quan hệ DB. Chỉ sử dụng `@Getter`, `@Setter` kết hợp với `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`.
3. **Tiêm Dependency**:
   - Sử dụng `@RequiredArgsConstructor` để tự động tạo Constructor cho các thuộc tính khai báo `private final`. Hạn chế dùng `@Autowired` trực tiếp lên field ngoại trừ các trường hợp tùy chọn (như `redisTemplate` với `required = false`).
4. **Chuẩn Hóa Kết Quả Trả Về**:
   - Mọi response thành công từ Controller phải bọc trong `ApiResponse<T>`.
   - Kết quả phân trang phải trả về qua `PageResponseDto<T>` được build từ `PagingUtil.buildPageable(...)`. Không trả trực tiếp `Page<T>` của Spring Boot.
5. **Đổi Dữ Liệu (DTO Mapping)**:
   - Viết các phương thức map tường minh (ví dụ: `toResponse(...)`) để chuyển Entity sang DTO tại Service, không lộ cấu trúc Entity vật lý trực tiếp ra ngoài API.
