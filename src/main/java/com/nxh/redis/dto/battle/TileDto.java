package com.nxh.redis.dto.battle;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TileDto {
    private String id;
    private String code;
    private int x;
    private int y;
    private String type;
    private String ownerKingdomId;
    private int level;
    private int defenseBonus;
}
