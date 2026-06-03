package com.nxh.redis.dto.battle;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BattleStateDto {
    private String battleId;
    private int maxRound;
    private int round;
    private String status;
    private List<KingdomDto> kingdoms;
    private List<TileDto> tiles;
    private List<BattleLogDto> logs;
}
