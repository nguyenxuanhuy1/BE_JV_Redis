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
public class CreateBattleRequest {
    private int maxRound;
    private List<KingdomRequest> kingdoms;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KingdomRequest {
        private String name;
        private String model;
        private String apiKey;
    }
}
