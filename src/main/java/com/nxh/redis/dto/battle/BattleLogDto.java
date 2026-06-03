package com.nxh.redis.dto.battle;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BattleLogDto {
    private String id;
    private int roundNumber;
    private String kingdomId;
    private String message;
    private String createdAt;
}
