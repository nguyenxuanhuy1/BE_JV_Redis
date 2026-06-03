package com.nxh.redis.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nxh.redis.entity.Battle;
import com.nxh.redis.entity.Kingdom;
import com.nxh.redis.entity.Tile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LlmService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LlmResponse {
        private String action;          // EXPAND, RECRUIT, ATTACK, DEFEND, RESEARCH, DIPLOMACY
        private String targetTileCode;  // e.g. "C3"
        private String targetKingdomId; // e.g. "k-2"
        private String message;         // Dialogue message in Vietnamese
        private String replyMessage;    // Advisor response in Vietnamese
    }

    public LlmResponse chooseAction(Battle battle, Kingdom kingdom, List<Tile> allTiles, List<Kingdom> allKingdoms, Map<String, String> alliances) {
        String apiKey = kingdom.getApiKey();
        String model = kingdom.getModel();

        // 1. If API Key or Model is empty, run Fallback AI directly
        if (apiKey == null || apiKey.trim().isEmpty() || model == null || model.trim().isEmpty()) {
            log.info("API Key or Model is empty for kingdom {}. Using Fallback Heuristic AI.", kingdom.getName());
            return runFallbackAi(battle, kingdom, allTiles, allKingdoms, alliances);
        }

        // 2. Generate Prompt and call LLM API
        try {
            String prompt = buildPrompt(battle, kingdom, allTiles, allKingdoms, alliances);
            String jsonResponse;

            if (model.toLowerCase().contains("gpt") || model.toLowerCase().contains("openai")) {
                jsonResponse = callOpenAi(model, apiKey, prompt);
            } else {
                // Default to Gemini API
                jsonResponse = callGemini(model, apiKey, prompt);
            }

            if (jsonResponse != null && !jsonResponse.isEmpty()) {
                LlmResponse response = parseLlmResponse(jsonResponse);
                if (response != null && validateAction(response, kingdom, allTiles, allKingdoms, alliances)) {
                    log.info("Successfully parsed and validated LLM action for {}: {}", kingdom.getName(), response.getAction());
                    return response;
                }
            }
        } catch (Exception e) {
            log.error("Error invoking LLM for {}: {}. Falling back to Heuristic AI.", kingdom.getName(), e.getMessage());
        }

        return runFallbackAi(battle, kingdom, allTiles, allKingdoms, alliances);
    }

    private String callGemini(String model, String apiKey, String prompt) throws Exception {
        String targetModel = model.contains("/") ? model : "models/" + model;
        String url = "https://generativelanguage.googleapis.com/v1beta/" + targetModel + ":generateContent?key=" + apiKey;

        Map<String, Object> textPart = Map.of("text", prompt);
        Map<String, Object> parts = Map.of("parts", List.of(textPart));
        Map<String, Object> contents = Map.of("contents", List.of(parts));
        Map<String, Object> generationConfig = Map.of("responseMimeType", "application/json");
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("contents", contents.get("contents"));
        requestBody.put("generationConfig", generationConfig);

        String jsonPayload = objectMapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode textNode = root.path("candidates").get(0).path("content").path("parts").get(0).path("text");
            return textNode.asText();
        } else {
            log.warn("Gemini API call failed with status: {}, response: {}", response.statusCode(), response.body());
            return null;
        }
    }

    private String callOpenAi(String model, String apiKey, String prompt) throws Exception {
        String url = "https://api.openai.com/v1/chat/completions";

        Map<String, Object> systemMessage = Map.of("role", "system", "content", "You are the leader of a kingdom in a turn-based strategy game.");
        Map<String, Object> userMessage = Map.of("role", "user", "content", prompt);
        
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(systemMessage, userMessage),
                "response_format", Map.of("type", "json_object"),
                "temperature", 0.7
        );

        String jsonPayload = objectMapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            JsonNode root = objectMapper.readTree(response.body());
            String text = root.path("choices").get(0).path("message").path("content").asText();
            return text;
        } else {
            log.warn("OpenAI API call failed with status: {}, response: {}", response.statusCode(), response.body());
            return null;
        }
    }

    private LlmResponse parseLlmResponse(String jsonString) {
        try {
            // Strip markdown block markers if present
            String cleaned = jsonString.trim();
            if (cleaned.startsWith("```json")) {
                cleaned = cleaned.substring(7);
            }
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.substring(3);
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3);
            }
            cleaned = cleaned.trim();

            JsonNode node = objectMapper.readTree(cleaned);
            return LlmResponse.builder()
                    .action(node.path("action").asText("").toUpperCase())
                    .targetTileCode(node.path("targetTileCode").asText(null))
                    .targetKingdomId(node.path("targetKingdomId").asText(null))
                    .message(node.path("message").asText("Ta quyết định hành động!"))
                    .replyMessage(node.path("replyMessage").asText("Báo cáo, tuân lệnh bệ hạ!"))
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse LLM Response JSON: {}", e.getMessage());
            return null;
        }
    }

    private boolean validateAction(LlmResponse response, Kingdom kingdom, List<Tile> allTiles, List<Kingdom> allKingdoms, Map<String, String> alliances) {
        String action = response.getAction();
        if (action == null) return false;

        switch (action) {
            case "EXPAND":
                if (kingdom.getEnergy() < 15) return false;
                Tile expandTile = findTileByCode(allTiles, response.getTargetTileCode());
                return expandTile != null && expandTile.getOwnerKingdomId() == null && isAdjacentToOwned(expandTile, kingdom.getId(), allTiles);
            
            case "RECRUIT":
                return kingdom.getGold() >= 12 && kingdom.getSupplies() >= 12;
            
            case "ATTACK":
                if (kingdom.getSoldiers() < 2) return false;
                Tile attackTile = findTileByCode(allTiles, response.getTargetTileCode());
                if (attackTile == null || attackTile.getOwnerKingdomId() == null || attackTile.getOwnerKingdomId().equals(kingdom.getId())) {
                    return false;
                }
                // Check if target is an ally
                String key1 = kingdom.getId() + ":" + attackTile.getOwnerKingdomId();
                String key2 = attackTile.getOwnerKingdomId() + ":" + kingdom.getId();
                if (alliances.containsKey(key1) || alliances.containsKey(key2)) {
                    return false; // Allied, cannot attack
                }
                return isAdjacentToOwned(attackTile, kingdom.getId(), allTiles);
            
            case "DEFEND":
                if (kingdom.getEnergy() < 15) return false;
                Tile defendTile = findTileByCode(allTiles, response.getTargetTileCode());
                return defendTile != null && kingdom.getId().equals(defendTile.getOwnerKingdomId());
            
            case "RESEARCH":
                return kingdom.getGold() >= 30 && kingdom.getEnergy() >= 20;
            
            case "DIPLOMACY":
                if (kingdom.getGold() < 20) return false;
                String targetId = response.getTargetKingdomId();
                if (targetId == null || targetId.equals(kingdom.getId())) return false;
                Optional<Kingdom> target = allKingdoms.stream()
                        .filter(k -> k.getId().equals(targetId) && k.isAlive())
                        .findFirst();
                return target.isPresent();

            default:
                return false;
        }
    }

    private LlmResponse runFallbackAi(Battle battle, Kingdom kingdom, List<Tile> allTiles, List<Kingdom> allKingdoms, Map<String, String> alliances) {
        log.info("Running Fallback Heuristic AI for kingdom {}", kingdom.getName());
        
        List<Tile> ownedTiles = allTiles.stream()
                .filter(t -> kingdom.getId().equals(t.getOwnerKingdomId()))
                .collect(Collectors.toList());

        // Prioritize Research if rich
        if (kingdom.getGold() >= 40 && kingdom.getEnergy() >= 25) {
            return LlmResponse.builder()
                    .action("RESEARCH")
                    .message("Chúng ta cần nâng cấp công nghệ học thuật để củng cố sức mạnh quốc gia!")
                    .replyMessage("Tuân lệnh! Phòng thí nghiệm hoàng gia đã bắt đầu nghiên cứu kỹ nghệ mới.")
                    .build();
        }

        // Prioritize Recruit if low on soldiers
        if (kingdom.getSoldiers() < 8 && kingdom.getGold() >= 12 && kingdom.getSupplies() >= 12) {
            return LlmResponse.builder()
                    .action("RECRUIT")
                    .message("Binh lực đang mỏng! Chiêu binh mãi mã ngay lập tức để phòng thủ biên cương!")
                    .replyMessage("Đã phát lệnh nghĩa vụ quân sự! Các trại lính đang tấp nập tân binh.")
                    .build();
        }

        // Try to Expand if we have energy
        if (kingdom.getEnergy() >= 15) {
            List<Tile> expandCandidates = new ArrayList<>();
            for (Tile t : allTiles) {
                if (t.getOwnerKingdomId() == null && isAdjacentToOwned(t, kingdom.getId(), allTiles)) {
                    expandCandidates.add(t);
                }
            }
            if (!expandCandidates.isEmpty()) {
                // Prioritize farms or gold mines
                expandCandidates.sort((t1, t2) -> {
                    int p1 = "GOLD_MINE".equals(t1.getType()) || "FARM".equals(t1.getType()) ? 1 : 0;
                    int p2 = "GOLD_MINE".equals(t2.getType()) || "FARM".equals(t2.getType()) ? 1 : 0;
                    return Integer.compare(p2, p1);
                });
                Tile target = expandCandidates.get(0);
                return LlmResponse.builder()
                        .action("EXPAND")
                        .targetTileCode(target.getCode())
                        .message("Hãy khai phá vùng đất hoang sơ tại ô [" + target.getCode() + "] để gia tăng lãnh thổ!")
                        .replyMessage("Đoàn thám hiểm đã xuất kích khai hoang thành công ô [" + target.getCode() + "]!")
                        .build();
            }
        }

        // Try to Attack an adjacent enemy if strong
        if (kingdom.getSoldiers() >= 12) {
            List<Tile> enemyCandidates = new ArrayList<>();
            for (Tile t : allTiles) {
                if (t.getOwnerKingdomId() != null && !t.getOwnerKingdomId().equals(kingdom.getId())) {
                    // Check if allied
                    String key1 = kingdom.getId() + ":" + t.getOwnerKingdomId();
                    String key2 = t.getOwnerKingdomId() + ":" + kingdom.getId();
                    if (!alliances.containsKey(key1) && !alliances.containsKey(key2)) {
                        if (isAdjacentToOwned(t, kingdom.getId(), allTiles)) {
                            enemyCandidates.add(t);
                        }
                    }
                }
            }
            if (!enemyCandidates.isEmpty()) {
                // Sort by lower defense or target capital
                enemyCandidates.sort((t1, t2) -> {
                    int p1 = "CAPITAL".equals(t1.getType()) ? 2 : t1.getLevel();
                    int p2 = "CAPITAL".equals(t2.getType()) ? 2 : t2.getLevel();
                    return Integer.compare(p1, p2); // prefer easier targets
                });
                Tile target = enemyCandidates.get(0);
                Optional<Kingdom> enemyOpt = allKingdoms.stream()
                        .filter(k -> k.getId().equals(target.getOwnerKingdomId()))
                        .findFirst();
                String enemyName = enemyOpt.map(Kingdom::getName).orElse("kẻ thù");
                return LlmResponse.builder()
                        .action("ATTACK")
                        .targetTileCode(target.getCode())
                        .targetKingdomId(target.getOwnerKingdomId())
                        .message("Tuyên chiến! Toàn quân tiến công đánh sập cứ điểm ô [" + target.getCode() + "] của " + enemyName + "!")
                        .replyMessage("Quân ta đã dàn trận tại biên giới ô [" + target.getCode() + "], sẵn sàng xung trận!")
                        .build();
            }
        }

        // Try to Defend/upgrade owned tiles
        if (kingdom.getEnergy() >= 15 && !ownedTiles.isEmpty()) {
            // Sort by level (upgrade lower level first, especially Capital)
            ownedTiles.sort((t1, t2) -> {
                int p1 = "CAPITAL".equals(t1.getType()) ? -5 : t1.getLevel();
                int p2 = "CAPITAL".equals(t2.getType()) ? -5 : t2.getLevel();
                return Integer.compare(p1, p2);
            });
            Tile target = ownedTiles.get(0);
            return LlmResponse.builder()
                    .action("DEFEND")
                    .targetTileCode(target.getCode())
                    .message("Xây dựng thêm thành lũy, gia cố công sự tại cứ điểm [" + target.getCode() + "]!")
                    .replyMessage("Công trình sư đã hoàn thành nâng cấp phòng thủ ô [" + target.getCode() + "].")
                    .build();
        }

        // Propose alliance if we have gold and someone is alive
        if (kingdom.getGold() >= 20) {
            List<Kingdom> otherLivingKingdoms = allKingdoms.stream()
                    .filter(k -> !k.getId().equals(kingdom.getId()) && k.isAlive())
                    .collect(Collectors.toList());
            if (!otherLivingKingdoms.isEmpty()) {
                // Find someone we are not allied with
                Kingdom targetPartner = null;
                for (Kingdom other : otherLivingKingdoms) {
                    String key1 = kingdom.getId() + ":" + other.getId();
                    String key2 = other.getId() + ":" + kingdom.getId();
                    if (!alliances.containsKey(key1) && !alliances.containsKey(key2)) {
                        targetPartner = other;
                        break;
                    }
                }
                if (targetPartner != null) {
                    return LlmResponse.builder()
                            .action("DIPLOMACY")
                            .targetKingdomId(targetPartner.getId())
                            .message("Chúng tôi muốn gửi sứ giả kết giao hữu nghị, ký hiệp ước hòa bình với vương quốc " + targetPartner.getName() + ".")
                            .replyMessage("Rất vinh hạnh! Hiệp ước hòa bình đã được ký kết, đình chiến trong 3 lượt.")
                            .build();
                }
            }
        }

        // Fallback: Recruit if we can, otherwise research, otherwise default empty/festival
        if (kingdom.getGold() >= 12 && kingdom.getSupplies() >= 12) {
            return LlmResponse.builder()
                    .action("RECRUIT")
                    .message("Tăng cường tuyển quân bảo vệ bờ cõi quốc gia.")
                    .replyMessage("Binh sĩ mới đã sẵn sàng huấn luyện.")
                    .build();
        }

        // Local festival (default when no resources)
        return LlmResponse.builder()
                .action("DIPLOMACY") // triggers local festival in service when target is empty
                .message("Tài nguyên hạn hẹp. Hãy tổ chức một lễ hội quốc gia để động viên tinh thần dân chúng!")
                .replyMessage("Dân chúng rất phấn khởi, sĩ khí vương quốc tăng cao!")
                .build();
    }

    private boolean isAdjacentToOwned(Tile target, String ownerId, List<Tile> allTiles) {
        for (Tile t : allTiles) {
            if (ownerId.equals(t.getOwnerKingdomId())) {
                int manhattanDistance = Math.abs(t.getX() - target.getX()) + Math.abs(t.getY() - target.getY());
                if (manhattanDistance == 1) {
                    return true;
                }
            }
        }
        return false;
    }

    private Tile findTileByCode(List<Tile> allTiles, String code) {
        if (code == null) return null;
        String clean = code.trim().toUpperCase();
        return allTiles.stream()
                .filter(t -> t.getCode().equals(clean))
                .findFirst()
                .orElse(null);
    }

    private String buildPrompt(Battle battle, Kingdom kingdom, List<Tile> allTiles, List<Kingdom> allKingdoms, Map<String, String> alliances) {
        List<Tile> owned = allTiles.stream().filter(t -> kingdom.getId().equals(t.getOwnerKingdomId())).toList();
        List<String> ownedCodes = owned.stream().map(Tile::getCode).toList();
        
        List<String> adjacentUnowned = new ArrayList<>();
        List<String> adjacentEnemy = new ArrayList<>();
        for (Tile t : allTiles) {
            if (isAdjacentToOwned(t, kingdom.getId(), allTiles)) {
                if (t.getOwnerKingdomId() == null) {
                    adjacentUnowned.add(t.getCode() + " (" + t.getType() + ")");
                } else if (!t.getOwnerKingdomId().equals(kingdom.getId())) {
                    // check if allied
                    String key1 = kingdom.getId() + ":" + t.getOwnerKingdomId();
                    String key2 = t.getOwnerKingdomId() + ":" + kingdom.getId();
                    if (!alliances.containsKey(key1) && !alliances.containsKey(key2)) {
                        adjacentEnemy.add(t.getCode() + " (" + t.getType() + ", Owner: " + t.getOwnerKingdomId() + ")");
                    }
                }
            }
        }

        StringBuilder otherKingdomsDesc = new StringBuilder();
        for (Kingdom ok : allKingdoms) {
            if (!ok.getId().equals(kingdom.getId())) {
                String status = ok.isAlive() ? "ALIVE" : "DEAD";
                String allianceStatus = (alliances.containsKey(kingdom.getId() + ":" + ok.getId()) || alliances.containsKey(ok.getId() + ":" + kingdom.getId())) ? "ALLIED" : "NONE";
                otherKingdomsDesc.append(String.format("- %s (%s): Soldiers: %d, Gold: %d, Score: %d, Status: %s, Alliance: %s\n",
                        ok.getName(), ok.getId(), ok.getSoldiers(), ok.getGold(), ok.getScore(), status, allianceStatus));
            }
        }

        return "You are the AI ruler of the kingdom '" + kingdom.getName() + "' (ID: " + kingdom.getId() + ") in a turn-based strategy war game.\n" +
                "Current Battle Status:\n" +
                "- Current Round: " + battle.getRound() + " / " + battle.getMaxRound() + "\n" +
                "Your Kingdom Status:\n" +
                "- Soldiers: " + kingdom.getSoldiers() + "\n" +
                "- Gold: " + kingdom.getGold() + "\n" +
                "- Supplies: " + kingdom.getSupplies() + "\n" +
                "- Energy: " + kingdom.getEnergy() + "\n" +
                "- Oil: " + kingdom.getOil() + "\n" +
                "- Tech Level: " + kingdom.getTech() + "\n" +
                "- Morale: " + kingdom.getMorale() + "\n" +
                "- Score: " + kingdom.getScore() + "\n" +
                "Your Owned Tiles: " + String.join(", ", ownedCodes) + "\n" +
                "Adjacent Unowned Tiles: " + String.join(", ", adjacentUnowned) + "\n" +
                "Adjacent Target Enemy Tiles: " + String.join(", ", adjacentEnemy) + "\n" +
                "Other Kingdoms:\n" + otherKingdomsDesc + "\n" +
                "RULES & COSTS:\n" +
                "1. EXPAND: Cost 15 energy. Claim adjacent unowned tile. Score +15.\n" +
                "2. RECRUIT: Cost 12 gold & 12 supplies. Gain soldiers. Morale +3.\n" +
                "3. ATTACK: Target adjacent enemy tile. Attacker lost soldiers. Success changes tile owner, loots 30% gold & supplies.\n" +
                "4. DEFEND: Cost 15 energy. Level up one of your tiles (+1 level, +2 defenseBonus). Score +10.\n" +
                "5. RESEARCH: Cost 30 gold & 20 energy. Tech level +1. Score +25.\n" +
                "6. DIPLOMACY: Cost 20 gold. Establish peace alliance for 3 turns with another living kingdom (cannot attack each other).\n\n" +
                "Choose your action for this turn. You MUST reply in JSON format with exact fields:\n" +
                "{\n" +
                "  \"action\": \"EXPAND|RECRUIT|ATTACK|DEFEND|RESEARCH|DIPLOMACY\",\n" +
                "  \"targetTileCode\": \"[Tile code, e.g. A2]\",\n" +
                "  \"targetKingdomId\": \"[Target Kingdom ID, e.g. k-2]\",\n" +
                "  \"message\": \"[Your dialogue message in Vietnamese, e.g., 'Toàn quân tiến công...']\",\n" +
                "  \"replyMessage\": \"[Advisor/Defender reply in Vietnamese]\"\n" +
                "}\n" +
                "Choose wisely based on your current resources. If you don't have enough resources for an action, do NOT choose it. Ensure targetTileCode is valid and adjacent where required.";
    }
}
