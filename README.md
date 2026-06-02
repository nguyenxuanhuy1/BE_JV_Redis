# Hướng Dẫn Phong Cách Code & Cấu Trúc Dự Án (Coding Style & Architecture Guide)

Tài liệu này tổng hợp cấu trúc thư mục, quy tắc đặt tên, phong cách lập trình (coding style) và các best practices được áp dụng đồng bộ trong dự án **BE_JV_Redis** (Spring Boot + PostgreSQL + Redis). Mọi thành viên cần tuân thủ hướng dẫn này khi phát triển tính năng mới để đảm bảo tính nhất quán và chất lượng mã nguồn.

---

## 1. Cấu Trúc Thư Mục & Phân Lớp (Architecture & Package Structure)

Dự án tuân theo kiến trúc phân lớp (Layered Architecture) rõ ràng, đóng gói dưới package gốc `com.nxh.redis`:

```text
com.nxh.redis
├── config               # Cấu hình hệ thống (CORS, JPA Auditing, Security, Redis...)
├── controller           # Lớp tiếp nhận HTTP Requests, điều phối dữ liệu qua Service
├── dto                  # Data Transfer Objects (phân tách theo nghiệp vụ: auth, trip, user, page)
├── entity               # Các thực thể JPA ánh xạ xuống Database (kế thừa BaseEntity)
├── enums                # Các định nghĩa kiểu Enum (Role, v.v.)
├── exception            # Xử lý lỗi tập trung (AppException, ErrorCode, GlobalExceptionHandler)
├── repository           # Interfaces thao tác với cơ sở dữ liệu (Spring Data JPA)
├── security             # Bộ lọc Security (JWT Filter) và dịch vụ sinh/xác thực Token
├── service              # Định nghĩa interface nghiệp vụ (business logic contracts)
│   └── impl             # Các lớp hiện thực hoá nghiệp vụ (Service Implementation)
└── util                 # Các class tiện ích tái sử dụng (PagingUtil, SortUtils)
```

---

## 2. Quy Tắc Đặt Tên (Naming Conventions)

### Class và Interface
- **Controller**: Đuôi `Controller` (ví dụ: `AuthController`, `TripController`).
- **Service Interface**: Đuôi `Service` (ví dụ: `AuthService`, `TripService`).
- **Service Implementation**: Nằm trong package `impl` và kết thúc bằng `ServiceImpl` (ví dụ: `AuthServiceImpl`, `TripServiceImpl`).
- **Repository**: Kết thúc bằng `Repository` (ví dụ: `UserRepository`, `TripRepository`).
- **DTO**: Phân tách rõ ràng giữa dữ liệu gửi lên và dữ liệu trả về:
  - Dữ liệu Request: Kết thúc bằng `Request` (ví dụ: `AuthRequest`, `TripRequest`).
  - Dữ liệu Response: Kết thúc bằng `Response` hoặc `Dto` (ví dụ: `AuthResponse`, `TripResponse`, `PageResponseDto`).
- **Exception**: Kết thúc bằng `Exception` (ví dụ: `AppException`).
- **Configuration**: Kết thúc bằng `Config` (ví dụ: `RedisDevConfig`, `SecurityConfig`).

### Các biến và phương thức
- Sử dụng **camelCase** cho tên biến, tên tham số và tên phương thức (ví dụ: `tokenVersion`, `generateToken`, `toResponse`).
- Tên package: Viết thường hoàn toàn, phân tách bằng dấu chấm (ví dụ: `com.nxh.redis.service.impl`).
- Hằng số: Viết hoa toàn bộ cách nhau bởi dấu gạch dưới (**UPPER_SNAKE_CASE**) (ví dụ: `VERSION_PREFIX`, `BLACKLIST_PREFIX`).

### Redis Keys
- Quy chuẩn định dạng Key Redis: `chữ_thường:phân_tách:key_động:` (kèm dấu `:` ở cuối tiền tố để rõ ràng).
  - Ví dụ: `user:version:` cho việc quản lý phiên đăng nhập của User ID.
  - Ví dụ: `auth:blacklist:` cho các JTI token bị thu hồi.

---

## 3. Phong Cách Thiết Kế Code (Coding Style & Syntax)

### Định dạng code (Formatting)
- Sử dụng thụt lề bằng **4 khoảng trắng (spaces)** hoặc thụt dòng đồng bộ theo IDE tiêu chuẩn của dự án.
- Các cặp ngoặc nhọn `{}` mở đầu nằm cùng hàng với tên phương thức/lớp, đóng ngoặc nằm trên một dòng riêng.
- Sử dụng các dòng chú thích phân vùng phân tách trực quan cho các phần cấu trúc logic lớn trong class:
  ```java
  // ── Key prefix constants ──
  private static final String VERSION_PREFIX = "user:version:";
  
  // ─────────────────────────────────────────────────────────────────────────
  ```

### Sử dụng Lombok giảm Boilerplate
- **Trong Entity**: Chỉ sử dụng `@Getter`, `@Setter` thay vì `@Data` để tránh lỗi đệ quy vô hạn khi gọi các hàm tự sinh như `toString()`, `equals()`, `hashCode()` có quan hệ liên kết dữ liệu, kết hợp với `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`.
- **Trong DTO**: Có thể dùng `@Data` nếu cấu trúc đơn giản, không chứa các liên kết Entity.
- **Tiêm phụ thuộc (Dependency Injection)**: 
  - Khuyến khích sử dụng `@RequiredArgsConstructor` từ Lombok để tiêm các dependency qua constructor.
  - Khai báo các dependency cần tiêm dạng `private final`.

Ví dụ:
```java
@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class TripController {
    private final TripService tripService; // Được tiêm qua constructor tự sinh bởi Lombok
}
```

---

## 4. Cơ Chế Xử Lý Lỗi (Exception & Error Handling)

Dự án xây dựng cơ chế xử lý lỗi tập trung để đảm bảo API luôn trả về cấu trúc đồng bộ.

- **AppException**: Lớp ngoại lệ runtime tùy biến kế thừa từ `RuntimeException`. Sử dụng bất cứ khi nào có lỗi nghiệp vụ cần chặn lại và trả về cho Client.
- **ErrorCode (Enum)**: Định nghĩa tập trung các mã lỗi, trạng thái HTTP thích hợp và thông điệp lỗi mặc định.
  ```java
  USER_NOT_FOUND(404, "User not found"),
  USER_ALREADY_EXISTS(400, "User already exists"),
  UNAUTHORIZED(401, "Unauthorized")
  ```
- **GlobalExceptionHandler**: Sử dụng `@RestControllerAdvice` để bắt toàn bộ lỗi xảy ra ở tầng Controller hoặc trôi từ tầng Service lên. Lỗi sẽ được parse sang JSON chuẩn hóa theo mẫu của `ErrorResponse`:
  ```json
  {
    "timestamp": "2026-06-02T09:07:40",
    "status": 400,
    "error": "USER_ALREADY_EXISTS",
    "message": "User already exists"
  }
  ```

---

## 5. Chuẩn Hóa Kết Quả Trả Về (API Response Standardization)

Mọi API trả về thành công đều được đóng gói thông qua cấu trúc `ApiResponse<T>` để phía Client dễ dàng bắt cấu trúc dữ liệu chung:

```java
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
}
```

**Ví dụ trả về thành công từ Controller:**
```java
@PostMapping("/login")
public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody AuthRequest request) {
    AuthResponse data = authService.login(request);
    return ResponseEntity.ok(ApiResponse.success("Đăng nhập thành công", data));
}
```

---

## 6. Khả Năng Kháng Lỗi Redis (Redis Resilience Design)

Nhằm đảm bảo dự án có khả năng chạy độc lập kể cả khi không bật Redis (ví dụ: chạy local dev đơn giản không có môi trường Docker), mã nguồn tuân thủ nguyên tắc **Redis Resilience**:

- Khai báo tiêm `RedisTemplate` ở chế độ tùy chọn (`required = false`):
  ```java
  @Autowired(required = false)
  private RedisTemplate<String, Object> redisTemplate;
  ```
- Luôn kiểm tra `null` trước khi thực hiện bất kỳ thao tác đọc/ghi vào Redis:
  ```java
  if (redisTemplate != null) {
      redisTemplate.opsForValue().set(VERSION_PREFIX + userId, version);
  }
  ```
- Nếu Redis không hoạt động, hệ thống vẫn tiếp tục xử lý các logic nghiệp vụ từ cơ sở dữ liệu quan hệ mà không sinh lỗi crash.

---

## 7. Quy Trình Chuyển Đổi Dữ Liệu (DTO Mapping)

- Các chuyển đổi từ JPA Entity sang Response DTO được viết tường minh trong các phương thức private (như `toResponse(...)` hoặc thông qua các Mapper class rõ ràng) giúp hạn chế rủi ro lộ lọt cấu trúc bảng dữ liệu vật lý ra API bên ngoài.
  ```java
  private TripResponse toResponse(Trip trip) {
      return TripResponse.builder()
              .id(trip.getId())
              .title(trip.getTitle())
              .price(trip.getPrice())
              .createdAt(trip.getCreatedAt())
              .build();
  }
  ```

---

## 8. Chuẩn Phân Trang & Sắp Xếp (Paging & Sorting)

- Lớp Controller tiếp nhận tham số phân trang dưới dạng `page` (mặc định = 0), `size` (mặc định = 10), và `sorts` (mặc định = `createdAt:desc`).
- Các Service hiện thực sử dụng `PagingUtil.buildPageable(page, size, sorts)` để xây dựng đối tượng `Pageable` của Spring Data, giúp logic phân trang thống nhất và an toàn.
- Trả về kết quả phân trang dưới dạng `PageResponseDto<T>` thay vì trả trực tiếp đối tượng `Page<T>` của Spring, nhằm tránh lộ thông tin nội bộ không cần thiết của cấu trúc Spring Page.
