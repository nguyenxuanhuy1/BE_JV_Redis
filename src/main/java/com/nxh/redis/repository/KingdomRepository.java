package com.nxh.redis.repository;

import com.nxh.redis.entity.Kingdom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KingdomRepository extends JpaRepository<Kingdom, Long> {
    List<Kingdom> findByBattleId(String battleId);
}
