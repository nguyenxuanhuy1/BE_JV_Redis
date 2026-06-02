package com.nxh.redis.security;

import com.nxh.redis.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;
import java.util.function.Function;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration}")
    private long expiration;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    // ---- Generate ----

    public String generateAccessToken(UserDetails userDetails) {
        return generateToken(userDetails);
    }

    public boolean validateToken(String token, UserDetails userDetails) {
        return isTokenValid(token, userDetails);
    }

    /**
     * Access token: chứa role, jti (UUID), version, hết hạn ngắn (mặc định 24h).
     * Cast UserDetails → User để lấy tokenVersion phục vụ per-device logout.
     */
    public String generateToken(UserDetails userDetails) {
        String role = userDetails.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .orElse("USER");

        int version = (userDetails instanceof User u) ? u.getTokenVersion() : 1;

        return Jwts.builder()
                .subject(userDetails.getUsername())
                .claim("role", role)
                .claim("type", "access")
                .claim("version", version)
                .id(UUID.randomUUID().toString())   // jti claim
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Refresh token: không chứa role, có jti + version, hết hạn dài (mặc định 7 ngày).
     */
    public String generateRefreshToken(UserDetails userDetails) {
        int version = (userDetails instanceof User u) ? u.getTokenVersion() : 1;

        return Jwts.builder()
                .subject(userDetails.getUsername())
                .claim("type", "refresh")
                .claim("version", version)
                .id(UUID.randomUUID().toString())   // jti claim
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    // ---- Extract ----

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    /** Trả về jti (JWT ID) — dùng cho blacklist. */
    public String extractJti(String token) {
        return extractClaim(token, Claims::getId);
    }

    /** Trả về tokenVersion nhúng trong JWT — dùng để so sánh với giá trị Redis/DB. */
    public Integer extractVersion(String token) {
        return extractClaim(token, claims -> claims.get("version", Integer.class));
    }

    /** Trả về thời điểm hết hạn — dùng để tính TTL khi lưu vào blacklist. */
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    // ---- Validate ----

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    /**
     * Validate refresh token mà không cần UserDetails.
     * Chỉ kiểm tra chữ ký hợp lệ và chưa hết hạn.
     */
    public boolean isTokenNotExpired(String token) {
        try {
            return !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    // ---- Private helpers ----

    private boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        return claimsResolver.apply(extractAllClaims(token));
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
