package com.nxh.redis.service;

import com.nxh.redis.entity.Battle;
import com.nxh.redis.entity.Kingdom;
import com.nxh.redis.entity.Tile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

public interface LlmService {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class LlmResponse {
        private String action;          // EXPAND, RECRUIT, ATTACK, DEFEND, RESEARCH, DIPLOMACY
        private String targetTileCode;  // e.g. "C3"
        private String targetKingdomId; // e.g. "k-2"
        private String message;         // Dialogue message in Vietnamese
        private String replyMessage;    // Advisor response in Vietnamese
    }

    LlmResponse chooseAction(Battle battle, Kingdom kingdom, List<Tile> allTiles, List<Kingdom> allKingdoms, Map<String, Integer> alliances);
}
