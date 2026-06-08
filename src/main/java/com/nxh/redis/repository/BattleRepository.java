package com.nxh.redis.repository;

import com.nxh.redis.entity.Battle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BattleRepository extends JpaRepository<Battle, String> {
    List<Battle> findByStatus(String status);
}

