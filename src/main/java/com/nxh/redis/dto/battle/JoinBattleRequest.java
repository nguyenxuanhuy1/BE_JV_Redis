package com.nxh.redis.dto.battle;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JoinBattleRequest {
    private String name;
    private String model;
    private String apiKey;
}
