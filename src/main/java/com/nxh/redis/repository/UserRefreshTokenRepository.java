package com.nxh.redis.repository;

import com.nxh.redis.entity.UserRefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRefreshTokenRepository extends JpaRepository<UserRefreshToken, Long> {

    Optional<UserRefreshToken> findByUserIdAndDeviceInfo(Long userId, String deviceInfo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT urt FROM UserRefreshToken urt WHERE urt.user.id = :userId AND urt.deviceInfo = :deviceInfo")
    Optional<UserRefreshToken> findByUserIdAndDeviceInfoWithLock(@Param("userId") Long userId, @Param("deviceInfo") String deviceInfo);

    List<UserRefreshToken> findByUserId(Long userId);

    @Modifying
    @Query("UPDATE UserRefreshToken urt SET urt.isRevoked = true WHERE urt.user.id = :userId")
    void revokeAllByUserId(@Param("userId") Long userId);
}
