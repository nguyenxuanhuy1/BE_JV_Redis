package com.nxh.redis.repository;

import com.nxh.redis.entity.BattleLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BattleLogRepository extends JpaRepository<BattleLog, String> {
    List<BattleLog> findByBattleIdOrderByRoundNumberAsc(String battleId);
}
