package com.nxh.redis.repository;

import com.nxh.redis.entity.Tile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TileRepository extends JpaRepository<Tile, Long> {
    List<Tile> findByBattleId(String battleId);
}
