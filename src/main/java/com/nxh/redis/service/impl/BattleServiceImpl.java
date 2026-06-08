package com.nxh.redis.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nxh.redis.dto.battle.*;
import com.nxh.redis.entity.*;
import com.nxh.redis.repository.*;
import com.nxh.redis.service.BattleService;
import com.nxh.redis.service.LlmService;
import com.nxh.redis.websocket.BattleWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BattleServiceImpl implements BattleService {

    private final BattleRepository battleRepository;
    private final KingdomRepository kingdomRepository;
    private final TileRepository tileRepository;
    private final BattleLogRepository battleLogRepository;
    private final LlmService llmService;
    private final BattleWebSocketHandler webSocketHandler;
    private final PlatformTransactionManager transactionManager;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();

    // Map to keep track of active alliances per battle.
    // Key: battleId, Value: Map of alliance keys (e.g. "k-1:k-2") and their expiration rounds (Integer)
    private final Map<String, Map<String, Integer>> battleAlliances = new ConcurrentHashMap<>();

    @Override
    @Transactional
    public BattleStateDto createBattle(CreateBattleRequest request) {
        String battleId = "battle-" + UUID.randomUUID().toString().substring(0, 8);
        
        Battle battle = Battle.builder()
                .id(battleId)
                .maxRound(request.getMaxRound() <= 0 ? 30 : request.getMaxRound())
                .round(0)
                .status("WAITING")
                .createdAt(LocalDateTime.now())
                .build();

        battleRepository.save(battle);

        // Color palette for kingdoms
        String[] colors = {"#3b82f6", "#ef4444", "#10b981", "#8b5cf6", "#f97316"};
        List<Kingdom> kingdoms = new ArrayList<>();
        if (request.getKingdoms() != null) {
            java.util.Set<String> uniqueNames = new java.util.HashSet<>();
            for (CreateBattleRequest.KingdomRequest kr : request.getKingdoms()) {
                if (kr.getName() == null || kr.getName().trim().isEmpty()) {
                    throw new IllegalArgumentException("Tên vương quốc không được để trống");
                }
                if (!uniqueNames.add(kr.getName().trim().toLowerCase())) {
                    throw new IllegalArgumentException("Tên vương quốc trong phòng không được trùng nhau: " + kr.getName());
                }
            }
            for (int i = 0; i < Math.min(4, request.getKingdoms().size()); i++) {
                CreateBattleRequest.KingdomRequest kr = request.getKingdoms().get(i);
                String kId = "k-" + (i + 1);
                int startSoldiers = 15;
                
                Kingdom kingdom = Kingdom.builder()
                        .id(kId)
                        .name(kr.getName())
                        .model(kr.getModel())
                        .apiKey(kr.getApiKey())
                        .color(colors[i % colors.length])
                        .soldiers(startSoldiers)
                        .gold(100)
                        .supplies(120)
                        .energy(80)
                        .oil(50)
                        .population(1000)
                        .morale(85)
                        .tech(1)
                        .score(100)
                        .scoreHistory("100")
                        .infantry(startSoldiers * 60)
                        .tanks(startSoldiers * 12)
                        .aircraft(startSoldiers * 3)
                        .artillery(startSoldiers * 9)
                        .navy(startSoldiers * 1)
                        .drones(startSoldiers * 5)
                        .alive(true)
                        .ready(true)
                        .battle(battle)
                        .build();
                
                kingdoms.add(kingdom);
            }
            kingdomRepository.saveAll(kingdoms);
            battle.getKingdoms().addAll(kingdoms);
        }

        // Save starting log
        BattleLog initLog = BattleLog.builder()
                .id(UUID.randomUUID().toString())
                .roundNumber(0)
                .kingdomId("system")
                .message("Lobby phòng đấu đã được khởi tạo. Đang chờ bắt đầu...")
                .createdAt(LocalDateTime.now().toLocalTime().toString().substring(0, 8))
                .battle(battle)
                .build();
        battleLogRepository.save(initLog);
        battle.getLogs().add(initLog);

        return mapToDto(battle);
    }

    @Override
    @Transactional(readOnly = true)
    public BattleStateDto getBattleState(String battleId) {
        Battle battle = battleRepository.findById(battleId)
                .orElseThrow(() -> new com.nxh.redis.exception.AppException(com.nxh.redis.exception.ErrorCode.BATTLE_NOT_FOUND));
        return mapToDto(battle);
    }

    @Override
    @Transactional
    public void startBattle(String battleId) {
        Battle battle = battleRepository.findById(battleId)
                .orElseThrow(() -> new com.nxh.redis.exception.AppException(com.nxh.redis.exception.ErrorCode.BATTLE_NOT_FOUND));

        if (!"WAITING".equals(battle.getStatus())) {
            throw new com.nxh.redis.exception.AppException(com.nxh.redis.exception.ErrorCode.BATTLE_ALREADY_STARTED);
        }

        List<Kingdom> kingdoms = battle.getKingdoms();
        if (kingdoms.isEmpty()) {
            throw new IllegalArgumentException("Cannot start battle with 0 players.");
        }

        // If >= 2 human players, check if all other players are ready
        if (kingdoms.size() >= 2) {
            for (Kingdom k : kingdoms) {
                if (!"k-1".equals(k.getId()) && !k.isReady()) {
                    throw new com.nxh.redis.exception.AppException(com.nxh.redis.exception.ErrorCode.PLAYERS_NOT_READY);
                }
            }
        }

        // Fill up to 4 kingdoms with Heuristic computer bots
        int currentCount = kingdoms.size();
        String[] colors = {"#3b82f6", "#ef4444", "#10b981", "#8b5cf6", "#f97316"};
        String[] botNames = {"Bot Alpha", "Bot Beta", "Bot Gamma", "Bot Delta"};

        for (int i = currentCount; i < 4; i++) {
            String kId = "k-" + (i + 1);
            int startSoldiers = 15;
            Kingdom bot = Kingdom.builder()
                    .id(kId)
                    .name(botNames[i])
                    .model("heuristic")
                    .apiKey(null)
                    .color(colors[i % colors.length])
                    .soldiers(startSoldiers)
                    .gold(100)
                    .supplies(120)
                    .energy(80)
                    .oil(50)
                    .population(1000)
                    .morale(85)
                    .tech(1)
                    .score(100)
                    .scoreHistory("100")
                    .infantry(startSoldiers * 60)
                    .tanks(startSoldiers * 12)
                    .aircraft(startSoldiers * 3)
                    .artillery(startSoldiers * 9)
                    .navy(startSoldiers * 1)
                    .drones(startSoldiers * 5)
                    .alive(true)
                    .ready(true)
                    .battle(battle)
                    .build();
            kingdoms.add(bot);
        }

        // Define capital spots for exactly 4 players
        List<int[]> capitalSpots = new ArrayList<>();
        capitalSpots.add(new int[]{1, 1}); // A2 for k-1
        capitalSpots.add(new int[]{8, 8}); // I9 for k-2
        capitalSpots.add(new int[]{8, 1}); // B9 for k-3
        capitalSpots.add(new int[]{1, 8}); // I2 for k-4

        List<Tile> tiles = new ArrayList<>();
        Random random = new Random();

        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 10; x++) {
                String id = "tile-" + x + "-" + y;
                String code = "" + (char)('A' + y) + (x + 1);

                int capIdx = -1;
                for (int c = 0; c < capitalSpots.size(); c++) {
                    int[] spot = capitalSpots.get(c);
                    if (spot[0] == x && spot[1] == y) {
                        capIdx = c;
                        break;
                    }
                }

                String type = "PLAIN";
                String ownerKingdomId = null;
                int level = 1;
                int defenseBonus = 1;

                if (capIdx != -1 && capIdx < kingdoms.size()) {
                    type = "CAPITAL";
                    ownerKingdomId = kingdoms.get(capIdx).getId();
                    level = 3;
                    defenseBonus = 5;
                } else {
                    double rand = random.nextDouble();
                    if (rand < 0.12) {
                        type = "GOLD_MINE";
                        defenseBonus = 2;
                    } else if (rand < 0.25) {
                        type = "FOREST";
                        defenseBonus = 3;
                    } else if (rand < 0.38) {
                        type = "FARM";
                        defenseBonus = 1;
                    } else if (rand < 0.45) {
                        type = "MOUNTAIN";
                        defenseBonus = 6;
                    }
                }

                Tile tile = Tile.builder()
                        .id(id)
                        .code(code)
                        .x(x)
                        .y(y)
                        .type(type)
                        .ownerKingdomId(ownerKingdomId)
                        .level(level)
                        .defenseBonus(defenseBonus)
                        .battle(battle)
                        .build();

                tiles.add(tile);
            }
        }
        tileRepository.saveAll(tiles);
        battle.getTiles().clear();
        battle.getTiles().addAll(tiles);

        // Save starting log
        BattleLog initLog = BattleLog.builder()
                .id(UUID.randomUUID().toString())
                .roundNumber(0)
                .kingdomId("system")
                .message("Trận đấu bắt đầu! Chúc các vương quốc may mắn!")
                .createdAt(LocalDateTime.now().toLocalTime().toString().substring(0, 8))
                .battle(battle)
                .build();
        battleLogRepository.save(initLog);
        battle.getLogs().add(initLog);

        battle.setStatus("RUNNING");
        battleRepository.save(battle);

        // Run simulation loop asynchronously
        executorService.submit(() -> runSimulation(battleId));
    }

    private void runSimulation(String battleId) {
        log.info("Starting simulation loop for battle: {}", battleId);
        org.springframework.transaction.support.TransactionTemplate transactionTemplate = 
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        try {
            int round = 0;
            boolean gameOver = false;

            while (!gameOver) {
                round++;
                final int currentRound = round;

                // 1. Upkeep Phase (Transaction 1)
                Map<String, Object> upkeepResult = transactionTemplate.execute(status -> 
                    executeRoundUpkeep(battleId, currentRound)
                );

                if (upkeepResult == null || (boolean) upkeepResult.get("gameOver")) {
                    break;
                }

                int maxRound = (int) upkeepResult.get("maxRound");
                if (currentRound > maxRound) {
                    terminateBattle(battleId, "Max rounds reached.");
                    break;
                }

                @SuppressWarnings("unchecked")
                List<String> livingKingdomIds = (List<String>) upkeepResult.get("livingKingdomIds");

                // 2. Action Choosing & Execution (No long transaction held)
                for (String kingdomId : livingKingdomIds) {
                    // Load snapshot in a short read-only transaction
                    Map<String, Object> snapshot = transactionTemplate.execute(status -> 
                        getLlmStateSnapshot(battleId)
                    );

                    if (snapshot == null) continue;

                    Battle battle = (Battle) snapshot.get("battle");
                    @SuppressWarnings("unchecked")
                    List<Kingdom> allKingdoms = (List<Kingdom>) snapshot.get("kingdoms");
                    @SuppressWarnings("unchecked")
                    List<Tile> allTiles = (List<Tile>) snapshot.get("tiles");

                    Kingdom kingdom = allKingdoms.stream()
                            .filter(k -> k.getId().equals(kingdomId))
                            .findFirst()
                            .orElse(null);

                    if (kingdom == null || !kingdom.isAlive()) continue;

                    Map<String, Integer> alliances = battleAlliances.computeIfAbsent(battleId, k -> new ConcurrentHashMap<>());

                    // Call LLM API (External HTTP network request - NO TRANSACTION HELD)
                    LlmService.LlmResponse actionResponse = llmService.chooseAction(battle, kingdom, allTiles, allKingdoms, alliances);

                    // Broadcast ACTION_SELECTED (Visual Novel dialogue)
                    sendActionSelectedEvent(battleId, kingdom, actionResponse);

                    // Sleep to allow UI text display (NO TRANSACTION HELD)
                    try {
                        Thread.sleep(2500);
                    } catch (InterruptedException ignored) {}

                    // Execute the action (Transaction 2)
                    transactionTemplate.executeWithoutResult(status -> 
                        executeKingdomAction(battleId, kingdomId, actionResponse)
                    );

                    // Sleep to allow UI animation play (NO TRANSACTION HELD)
                    try {
                        Thread.sleep(2500);
                    } catch (InterruptedException ignored) {}
                }

                // 3. Finalize & Death Check Phase (Transaction 3)
                Map<String, Object> cleanupResult = transactionTemplate.execute(status -> 
                    executeRoundCleanup(battleId, currentRound)
                );

                if (cleanupResult == null || (boolean) cleanupResult.get("gameOver")) {
                    break;
                }

                // Wait between rounds
                Thread.sleep(3000);
            }
        } catch (InterruptedException e) {
            log.warn("Simulation loop interrupted for battle {}: {}", battleId, e.getMessage());
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Fatal error in battle simulation {}: {}", battleId, e.getMessage(), e);
            terminateBattle(battleId, "System error: " + e.getMessage());
        }
    }

    @Transactional
    protected Map<String, Object> executeRoundUpkeep(String battleId, int round) {
        Map<String, Object> result = new HashMap<>();
        Battle battle = battleRepository.findById(battleId).orElse(null);
        if (battle == null || !"RUNNING".equals(battle.getStatus())) {
            result.put("gameOver", true);
            return result;
        }

        result.put("maxRound", battle.getMaxRound());
        battle.setRound(round);

        List<Kingdom> kingdoms = kingdomRepository.findByBattleId(battleId);
        List<Tile> tiles = tileRepository.findByBattleId(battleId);

        // Filter living kingdoms
        List<Kingdom> livingKingdoms = kingdoms.stream()
                .filter(Kingdom::isAlive)
                .collect(Collectors.toList());

        if (livingKingdoms.size() <= 1) {
            result.put("gameOver", true);
            finalizeBattle(battle, livingKingdoms, tiles);
            battleRepository.save(battle);
            return result;
        }

        // ==========================================
        // UPKEEP & RESOURCE HARVEST PHASE
        // ==========================================
        for (Kingdom kingdom : livingKingdoms) {
            List<Tile> owned = tiles.stream()
                    .filter(t -> kingdom.getId().equals(t.getOwnerKingdomId()))
                    .collect(Collectors.toList());

            int suppliesGain = 10;
            int goldGain = 10;
            int energyGain = 10;
            int oilGain = 5;

            for (Tile tile : owned) {
                switch (tile.getType()) {
                    case "FARM":
                        suppliesGain += 15 + tile.getLevel() * 2;
                        break;
                    case "GOLD_MINE":
                        goldGain += 15 + tile.getLevel() * 2;
                        break;
                    case "FOREST":
                        energyGain += 15 + tile.getLevel() * 2;
                        break;
                    case "MOUNTAIN":
                        oilGain += 15 + tile.getLevel() * 2;
                        break;
                    case "CAPITAL":
                        suppliesGain += 20;
                        goldGain += 20;
                        energyGain += 20;
                        oilGain += 10;
                        break;
                }
            }

            int scoreGain = owned.size() * 2;
            int upkeep = (int) Math.floor(kingdom.getSoldiers() * 1.5);

            kingdom.setSupplies(Math.max(0, kingdom.getSupplies() + suppliesGain - upkeep));
            kingdom.setGold(Math.max(0, kingdom.getGold() + goldGain - (int) Math.floor(upkeep / 2.0)));
            kingdom.setEnergy(kingdom.getEnergy() + energyGain);
            kingdom.setOil(kingdom.getOil() + oilGain);
            kingdom.setScore(kingdom.getScore() + scoreGain);

            // Population adjustments
            if (kingdom.getSupplies() > 20) {
                kingdom.setPopulation(kingdom.getPopulation() + 15);
            } else {
                kingdom.setPopulation(Math.max(0, kingdom.getPopulation() - 10));
            }

            // Starvation Check
            if (kingdom.getSupplies() == 0) {
                kingdom.setMorale(Math.max(20, kingdom.getMorale() - 8));
                kingdom.setSoldiers(Math.max(2, kingdom.getSoldiers() - 1));
            }

            // Keep military specific numbers in sync with soldiers count
            syncMilitaryUnits(kingdom);

            // Record score history
            kingdom.setScoreHistory(kingdom.getScoreHistory() + "," + kingdom.getScore());
        }

        kingdomRepository.saveAll(livingKingdoms);

        // Broadcast ROUND_START
        sendRoundStartEvent(battle, livingKingdoms);

        // ==========================================
        // RANDOM EVENTS (15% rate, from round 2)
        // ==========================================
        if (round >= 2 && Math.random() < 0.15 && !livingKingdoms.isEmpty()) {
            Kingdom target = livingKingdoms.get(new Random().nextInt(livingKingdoms.size()));
            triggerRandomEvent(battle, target);
        }

        // Return IDs of living kingdoms so we can run LLM choosing on them
        List<String> livingKingdomIds = livingKingdoms.stream()
                .filter(Kingdom::isAlive)
                .map(Kingdom::getId)
                .collect(Collectors.toList());

        result.put("gameOver", false);
        result.put("livingKingdomIds", livingKingdomIds);
        battleRepository.save(battle);
        return result;
    }

    @Transactional(readOnly = true)
    protected Map<String, Object> getLlmStateSnapshot(String battleId) {
        Map<String, Object> snapshot = new HashMap<>();
        Battle battle = battleRepository.findById(battleId).orElse(null);
        if (battle == null) return null;

        List<Kingdom> kingdoms = kingdomRepository.findByBattleId(battleId);
        List<Tile> tiles = tileRepository.findByBattleId(battleId);

        // Force lazy collections to initialize inside transaction
        battle.getKingdoms().size();
        battle.getTiles().size();
        battle.getLogs().size();

        snapshot.put("battle", battle);
        snapshot.put("kingdoms", kingdoms);
        snapshot.put("tiles", tiles);
        return snapshot;
    }

    @Transactional
    protected void executeKingdomAction(String battleId, String kingdomId, LlmService.LlmResponse actionResponse) {
        Battle battle = battleRepository.findById(battleId).orElse(null);
        if (battle == null || !"RUNNING".equals(battle.getStatus())) return;

        List<Kingdom> kingdoms = kingdomRepository.findByBattleId(battleId);
        List<Tile> tiles = tileRepository.findByBattleId(battleId);
        Map<String, Integer> alliances = battleAlliances.computeIfAbsent(battleId, k -> new ConcurrentHashMap<>());

        Kingdom kingdom = kingdoms.stream()
                .filter(k -> k.getId().equals(kingdomId))
                .findFirst()
                .orElse(null);

        if (kingdom == null || !kingdom.isAlive()) return;

        executeAction(battle, kingdom, actionResponse, tiles, kingdoms, alliances);
        battleRepository.save(battle);
    }

    @Transactional
    protected Map<String, Object> executeRoundCleanup(String battleId, int round) {
        Map<String, Object> result = new HashMap<>();
        Battle battle = battleRepository.findById(battleId).orElse(null);
        if (battle == null || !"RUNNING".equals(battle.getStatus())) {
            result.put("gameOver", true);
            return result;
        }

        List<Kingdom> kingdoms = kingdomRepository.findByBattleId(battleId);
        List<Tile> tiles = tileRepository.findByBattleId(battleId);

        // ==========================================
        // DEATH CHECKS
        // ==========================================
        for (Kingdom k : kingdoms) {
            if (!k.isAlive()) continue;
            long ownedCount = tiles.stream().filter(t -> k.getId().equals(t.getOwnerKingdomId())).count();
            if (ownedCount == 0) {
                k.setAlive(false);
                kingdomRepository.save(k);

                BattleLog deathLog = BattleLog.builder()
                        .id(UUID.randomUUID().toString())
                        .roundNumber(round)
                        .kingdomId("system")
                        .message("Vương quốc " + k.getName() + " (" + k.getId() + ") đã bị quét sạch hoàn toàn khỏi bản đồ và bị xóa sổ!")
                        .createdAt(LocalDateTime.now().toLocalTime().toString().substring(0, 8))
                        .battle(battle)
                        .build();
                battleLogRepository.save(deathLog);
            }
        }

        // Clean expired alliances
        Map<String, Integer> alliances = battleAlliances.computeIfAbsent(battleId, k -> new ConcurrentHashMap<>());
        alliances.entrySet().removeIf(entry -> entry.getValue() <= round);

        // Check if game end condition met
        List<Kingdom> survivors = kingdoms.stream().filter(Kingdom::isAlive).collect(Collectors.toList());
        if (survivors.size() <= 1 || round >= battle.getMaxRound()) {
            result.put("gameOver", true);
            finalizeBattle(battle, survivors, tiles);
        } else {
            result.put("gameOver", false);
        }

        battleRepository.save(battle);
        return result;
    }

    private void syncMilitaryUnits(Kingdom kingdom) {
        int soldiers = kingdom.getSoldiers();
        kingdom.setInfantry(soldiers * 60);
        kingdom.setTanks(soldiers * 12);
        kingdom.setAircraft(soldiers * 3);
        kingdom.setArtillery(soldiers * 9);
        kingdom.setNavy(soldiers * 1);
        kingdom.setDrones(soldiers * 5);
    }

    private void triggerRandomEvent(Battle battle, Kingdom target) {
        String eventType = Math.random() < 0.5 ? "PLAGUE" : "DISASTER";
        
        int soldiersLost = 0;
        int moraleLost = 0;
        int goldLost = 0;
        int suppliesLost = 0;
        String dialogMsg = "";
        String replyMsg = "";

        if ("PLAGUE".equals(eventType)) {
            soldiersLost = (int) Math.floor(target.getSoldiers() * 0.3);
            target.setSoldiers(Math.max(2, target.getSoldiers() - soldiersLost));
            moraleLost = 20;
            target.setMorale(Math.max(10, target.getMorale() - moraleLost));
            syncMilitaryUnits(target);
            
            dialogMsg = "Nguy to! Đại dịch vương quốc 🦠 đang bùng phát dữ dội! Quân sĩ kiệt quệ!";
            replyMsg = "Báo cáo bệ hạ, quân ta đã mất đi 30% lực lượng (trừ " + soldiersLost + " binh sĩ)!";
        } else {
            suppliesLost = (int) Math.floor(target.getSupplies() * 0.5);
            target.setSupplies(Math.max(0, target.getSupplies() - suppliesLost));
            goldLost = (int) Math.floor(target.getGold() * 0.3);
            target.setGold(Math.max(0, target.getGold() - goldLost));
            
            dialogMsg = "Báo cáo! Thiên tai 🌪️ càn quét qua lãnh thổ, kho thóc và ngân khố chịu thiệt hại nặng nề!";
            replyMsg = "Khắc phục hậu quả thiên tai tiêu tốn " + goldLost + " vàng và làm hao hụt " + suppliesLost + " lương thảo!";
        }

        kingdomRepository.save(target);

        // Add log
        BattleLog eventLog = BattleLog.builder()
                .id(UUID.randomUUID().toString())
                .roundNumber(battle.getRound())
                .kingdomId(target.getId())
                .message("SỰ KIỆN: " + ("PLAGUE".equals(eventType) ? "Dịch bệnh bùng phát" : "Thiên tai càn quét") + " tại " + target.getName() + ".")
                .createdAt(LocalDateTime.now().toLocalTime().toString().substring(0, 8))
                .battle(battle)
                .build();
        battleLogRepository.save(eventLog);

        // Payload schema matches DISASTER_TRIGGERED
        Map<String, Object> payload = Map.of(
                "effectType", eventType,
                "targetKingdomId", target.getId(),
                "soldiersLost", soldiersLost,
                "moraleLost", moraleLost,
                "goldLost", goldLost,
                "suppliesLost", suppliesLost,
                "dialogue", Map.of(
                        "type", "DISASTER",
                        "senderId", target.getId(),
                        "senderName", target.getName(),
                        "senderColor", target.getColor(),
                        "senderModel", target.getModel() != null ? target.getModel() : "heuristic",
                        "message", dialogMsg,
                        "replyMessage", replyMsg
                )
        );

        Map<String, Object> event = Map.of(
                "type", "DISASTER_TRIGGERED",
                "payload", payload
        );

        try {
            webSocketHandler.broadcast(battle.getId(), objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            log.error("Failed to serialize disaster event", e);
        }
    }

    private void executeAction(Battle battle, Kingdom kingdom, LlmService.LlmResponse response, List<Tile> allTiles, List<Kingdom> allKingdoms, Map<String, Integer> alliances) {
        String action = response.getAction();
        boolean success = false;
        List<Tile> updatedTiles = new ArrayList<>();
        Map<String, Object> lootedResources = null;
        Map<String, Object> attackLine = null;

        if ("EXPAND".equals(action)) {
            Tile tile = findTileByCode(allTiles, response.getTargetTileCode());
            if (tile != null && tile.getOwnerKingdomId() == null && kingdom.getEnergy() >= 15) {
                kingdom.setEnergy(kingdom.getEnergy() - 15);
                kingdom.setScore(kingdom.getScore() + 15);
                tile.setOwnerKingdomId(kingdom.getId());
                tile.setLevel(1);
                tileRepository.save(tile);
                updatedTiles.add(tile);
                success = true;

                addLog(battle, kingdom.getId(), "Khai hoang thành công ô đất hoang [" + tile.getCode() + "].");
            }
        } 
        else if ("RECRUIT".equals(action)) {
            if (kingdom.getGold() >= 12 && kingdom.getSupplies() >= 12) {
                kingdom.setGold(kingdom.getGold() - 12);
                kingdom.setSupplies(kingdom.getSupplies() - 12);
                int recruited = 4 + (int) Math.floor(kingdom.getTech() * 1.5);
                kingdom.setSoldiers(kingdom.getSoldiers() + recruited);
                kingdom.setMorale(Math.min(100, kingdom.getMorale() + 3));
                syncMilitaryUnits(kingdom);
                success = true;

                addLog(battle, kingdom.getId(), "Chiêu mộ thành công " + recruited + " binh sĩ mới.");
            }
        } 
        else if ("ATTACK".equals(action)) {
            Tile tile = findTileByCode(allTiles, response.getTargetTileCode());
            if (tile != null && tile.getOwnerKingdomId() != null && !tile.getOwnerKingdomId().equals(kingdom.getId()) && kingdom.getSoldiers() >= 2) {
                Kingdom defender = allKingdoms.stream()
                        .filter(k -> k.getId().equals(tile.getOwnerKingdomId()))
                        .findFirst()
                        .orElse(null);

                if (defender != null) {
                    // Attack Power
                    double randAttack = 0.5 + new Random().nextDouble();
                    double attackPower = (kingdom.getSoldiers() * 0.7) * randAttack * (1 + kingdom.getTech() * 0.15);

                    // Defense Power
                    double randDefense = 0.5 + new Random().nextDouble();
                    double defensePower = (defender.getSoldiers() * 0.4 + tile.getDefenseBonus() * 3) * randDefense * (1 + defender.getTech() * 0.1);

                    // Sync line for frontend
                    // Find attacker capital or center of owned tiles to draw attacking line
                    List<Tile> ownedByAttacker = allTiles.stream()
                            .filter(t -> kingdom.getId().equals(t.getOwnerKingdomId()))
                            .collect(Collectors.toList());
                    Tile fromTile = ownedByAttacker.isEmpty() ? tile : ownedByAttacker.get(0);
                    // Find nearest adjacent owned tile of attacker
                    for (Tile t : ownedByAttacker) {
                        int dist = Math.abs(t.getX() - tile.getX()) + Math.abs(t.getY() - tile.getY());
                        if (dist == 1) {
                            fromTile = t;
                            break;
                        }
                    }

                    attackLine = Map.of(
                            "fromX", fromTile.getX(),
                            "fromY", fromTile.getY(),
                            "toX", tile.getX(),
                            "toY", tile.getY(),
                            "unitType", getAttackUnitType(kingdom),
                            "color", kingdom.getColor()
                    );

                    if (attackPower > defensePower) {
                        // Attack Success
                        success = true;
                        String previousOwner = tile.getOwnerKingdomId();
                        tile.setOwnerKingdomId(kingdom.getId());
                        tileRepository.save(tile);
                        updatedTiles.add(tile);

                        // Casualties
                        int lostAttacker = (int) Math.floor(kingdom.getSoldiers() * 0.3);
                        kingdom.setSoldiers(Math.max(5, kingdom.getSoldiers() - lostAttacker));
                        
                        int lostDefender = (int) Math.floor(defender.getSoldiers() * 0.4);
                        defender.setSoldiers(Math.max(2, defender.getSoldiers() - lostDefender));

                        syncMilitaryUnits(kingdom);
                        syncMilitaryUnits(defender);

                        // Score adjustments
                        kingdom.setScore(kingdom.getScore() + 40);
                        defender.setScore(Math.max(0, defender.getScore() - 20));

                        // Looting
                        int lootedGold = (int) Math.floor(defender.getGold() * 0.3);
                        int lootedSupplies = (int) Math.floor(defender.getSupplies() * 0.3);
                        
                        defender.setGold(Math.max(0, defender.getGold() - lootedGold));
                        defender.setSupplies(Math.max(0, defender.getSupplies() - lootedSupplies));
                        
                        kingdom.setGold(kingdom.getGold() + lootedGold);
                        kingdom.setSupplies(kingdom.getSupplies() + lootedSupplies);

                        lootedResources = Map.of(
                                "gold", lootedGold,
                                "supplies", lootedSupplies,
                                "targetKingdomId", defender.getId()
                        );

                        // Capital siege effect
                        if ("CAPITAL".equals(tile.getType())) {
                            defender.setMorale(Math.max(10, defender.getMorale() - 40));
                            kingdom.setMorale(Math.min(100, kingdom.getMorale() + 15));
                            addLog(battle, kingdom.getId(), "CHIẾN THẮNG vang dội! Chiếm thành công Đại bản doanh [" + tile.getCode() + "] của " + defender.getName() + ".");
                        } else {
                            addLog(battle, kingdom.getId(), "Chiến thắng! Chiếm được ô [" + tile.getCode() + "] của " + defender.getName() + ".");
                        }

                        kingdomRepository.save(defender);
                    } else {
                        // Attack Failure
                        success = false;
                        int lostAttacker = (int) Math.floor(kingdom.getSoldiers() * 0.5);
                        kingdom.setSoldiers(Math.max(2, kingdom.getSoldiers() - lostAttacker));
                        kingdom.setMorale(Math.max(30, kingdom.getMorale() - 10));
                        syncMilitaryUnits(kingdom);

                        addLog(battle, kingdom.getId(), "Thất bại khi tấn công ô [" + tile.getCode() + "] của " + defender.getName() + ".");
                    }
                }
            }
        } 
        else if ("DEFEND".equals(action)) {
            Tile tile = findTileByCode(allTiles, response.getTargetTileCode());
            if (tile != null && kingdom.getId().equals(tile.getOwnerKingdomId()) && kingdom.getEnergy() >= 15) {
                kingdom.setEnergy(kingdom.getEnergy() - 15);
                kingdom.setScore(kingdom.getScore() + 10);
                
                tile.setLevel(tile.getLevel() + 1);
                tile.setDefenseBonus(tile.getDefenseBonus() + 2);
                tileRepository.save(tile);
                updatedTiles.add(tile);
                success = true;

                addLog(battle, kingdom.getId(), "Gia cố công sự thành công tại ô [" + tile.getCode() + "] lên Cấp " + tile.getLevel() + ".");
            }
        } 
        else if ("RESEARCH".equals(action)) {
            if (kingdom.getGold() >= 30 && kingdom.getEnergy() >= 20) {
                kingdom.setGold(kingdom.getGold() - 30);
                kingdom.setEnergy(kingdom.getEnergy() - 20);
                kingdom.setTech(kingdom.getTech() + 1);
                kingdom.setScore(kingdom.getScore() + 25);
                success = true;

                addLog(battle, kingdom.getId(), "Nghiên cứu thành công kỹ nghệ mới. Cấp công nghệ hiện tại: " + kingdom.getTech() + ".");
            }
        } 
        else if ("DIPLOMACY".equals(action)) {
            String partnerId = response.getTargetKingdomId();
            Kingdom partner = null;
            if (partnerId != null && !partnerId.equals(kingdom.getId())) {
                partner = allKingdoms.stream()
                        .filter(k -> k.getId().equals(partnerId) && k.isAlive())
                        .findFirst()
                        .orElse(null);
            }

            if (partner != null && kingdom.getGold() >= 20) {
                kingdom.setGold(kingdom.getGold() - 20);
                kingdom.setMorale(Math.min(100, kingdom.getMorale() + 10));
                partner.setMorale(Math.min(100, partner.getMorale() + 10));
                
                kingdom.setScore(kingdom.getScore() + 15);
                partner.setScore(partner.getScore() + 10);

                // Add alliance
                String allianceKey = kingdom.getId() + ":" + partner.getId();
                alliances.put(allianceKey, battle.getRound() + 3);

                kingdomRepository.save(partner);
                success = true;

                addLog(battle, kingdom.getId(), "Ký kết hiệp ước liên minh hòa bình thành công với " + partner.getName() + ".");
            } else if (kingdom.getGold() >= 20) {
                // No target kingdom or already allied / died -> local festival
                kingdom.setGold(kingdom.getGold() - 20);
                kingdom.setMorale(Math.min(100, kingdom.getMorale() + 15));
                kingdom.setScore(kingdom.getScore() + 5);
                success = true;

                addLog(battle, kingdom.getId(), "Tổ chức đại lễ hội quốc gia thành công.");
            }
        }

        kingdomRepository.save(kingdom);

        // Create updated vương quốc list DTOs
        List<KingdomDto> updatedKingdoms = allKingdoms.stream()
                .map(this::mapKingdomToDto)
                .collect(Collectors.toList());

        // Broadcast ACTION_EXECUTED
        sendActionExecutedEvent(battle.getId(), kingdom.getId(), action, success, updatedTiles, lootedResources, updatedKingdoms, attackLine);
    }

    private String getAttackUnitType(Kingdom kingdom) {
        if (kingdom.getAircraft() > 0) return "AIRCRAFT";
        if (kingdom.getDrones() > 0) return "DRONE";
        return "TANK";
    }

    private void addLog(Battle battle, String kingdomId, String message) {
        BattleLog blog = BattleLog.builder()
                .id(UUID.randomUUID().toString())
                .roundNumber(battle.getRound())
                .kingdomId(kingdomId)
                .message(message)
                .createdAt(LocalDateTime.now().toLocalTime().toString().substring(0, 8))
                .battle(battle)
                .build();
        battleLogRepository.save(blog);
        battle.getLogs().add(blog);
    }

    private void finalizeBattle(Battle battle, List<Kingdom> survivors, List<Tile> tiles) {
        battle.setStatus("FINISHED");
        battleRepository.save(battle);

        String winnerId = "system";
        String winnerName = "Không có";
        
        if (survivors.size() == 1) {
            Kingdom winner = survivors.get(0);
            winnerId = winner.getId();
            winnerName = winner.getName();
        } else if (survivors.size() > 1) {
            // Find survivor with highest score
            Kingdom winner = survivors.stream()
                    .max(Comparator.comparingInt(Kingdom::getScore))
                    .orElse(survivors.get(0));
            winnerId = winner.getId();
            winnerName = winner.getName();
        }

        BattleLog endLog = BattleLog.builder()
                .id(UUID.randomUUID().toString())
                .roundNumber(battle.getRound())
                .kingdomId("system")
                .message("TRẬN ĐẤU KẾT THÚC! Nhà vô địch vinh quang của Đấu trường AI Arena là: " + winnerName + " (" + winnerId + ")!")
                .createdAt(LocalDateTime.now().toLocalTime().toString().substring(0, 8))
                .battle(battle)
                .build();
        battleLogRepository.save(endLog);

        // Broadcast ROUND_START one last time to sync final FINISHED status
        sendRoundStartEvent(battle, survivors);

        // Clean up alliances
        battleAlliances.remove(battle.getId());
    }

    private void terminateBattle(String battleId, String reason) {
        try {
            Battle battle = battleRepository.findById(battleId).orElse(null);
            if (battle != null) {
                battle.setStatus("FINISHED");
                battleRepository.save(battle);

                BattleLog endLog = BattleLog.builder()
                        .id(UUID.randomUUID().toString())
                        .roundNumber(battle.getRound())
                        .kingdomId("system")
                        .message("Trận đấu dừng đột ngột. Lý do: " + reason)
                        .createdAt(LocalDateTime.now().toLocalTime().toString().substring(0, 8))
                        .battle(battle)
                        .build();
                battleLogRepository.save(endLog);
            }
            battleAlliances.remove(battleId);
        } catch (Exception e) {
            log.error("Failed to terminate battle {}", battleId, e);
        }
    }

    private void sendRoundStartEvent(Battle battle, List<Kingdom> livingKingdoms) {
        Map<String, Object> payload = Map.of(
                "round", battle.getRound(),
                "kingdoms", livingKingdoms.stream()
                        .map(this::mapKingdomToDto)
                        .collect(Collectors.toList())
        );

        Map<String, Object> event = Map.of(
                "type", "ROUND_START",
                "payload", payload
        );

        try {
            webSocketHandler.broadcast(battle.getId(), objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            log.error("Failed to send ROUND_START event via WS", e);
        }
    }

    private void sendActionSelectedEvent(String battleId, Kingdom kingdom, LlmService.LlmResponse response) {
        Map<String, Object> dialogue = Map.of(
                "type", response.getAction(),
                "senderId", kingdom.getId(),
                "senderName", kingdom.getName(),
                "senderColor", kingdom.getColor(),
                "senderModel", kingdom.getModel() != null ? kingdom.getModel() : "heuristic",
                "receiverId", response.getTargetKingdomId() != null ? response.getTargetKingdomId() : "",
                "message", response.getMessage(),
                "replyMessage", response.getReplyMessage(),
                "targetTileCode", response.getTargetTileCode() != null ? response.getTargetTileCode() : ""
        );

        Map<String, Object> event = Map.of(
                "type", "ACTION_SELECTED",
                "payload", Map.of(
                        "action", response.getAction(),
                        "dialogue", dialogue
                )
        );

        try {
            webSocketHandler.broadcast(battleId, objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            log.error("Failed to send ACTION_SELECTED event via WS", e);
        }
    }

    private void sendActionExecutedEvent(String battleId, String kingdomId, String action, boolean success, List<Tile> updatedTiles, Map<String, Object> lootedResources, List<KingdomDto> updatedKingdoms, Map<String, Object> attackLine) {
        List<TileDto> tileDtos = updatedTiles.stream()
                .map(this::mapTileToDto)
                .collect(Collectors.toList());

        Map<String, Object> payload = new HashMap<>();
        payload.put("action", action);
        payload.put("kingdomId", kingdomId);
        payload.put("success", success);
        payload.put("updatedTiles", tileDtos);
        payload.put("updatedKingdoms", updatedKingdoms);

        if (lootedResources != null) {
            payload.put("lootedResources", lootedResources);
        }
        if (attackLine != null) {
            payload.put("attackLine", attackLine);
        }

        Map<String, Object> event = Map.of(
                "type", "ACTION_EXECUTED",
                "payload", payload
        );

        try {
            webSocketHandler.broadcast(battleId, objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            log.error("Failed to send ACTION_EXECUTED event via WS", e);
        }
    }

    private Tile findTileByCode(List<Tile> allTiles, String code) {
        if (code == null) return null;
        String clean = code.trim().toUpperCase();
        return allTiles.stream()
                .filter(t -> t.getCode().equals(clean))
                .findFirst()
                .orElse(null);
    }

    private BattleStateDto mapToDto(Battle battle) {
        List<KingdomDto> kingdoms = battle.getKingdoms().stream()
                .map(this::mapKingdomToDto)
                .collect(Collectors.toList());

        List<TileDto> tiles = battle.getTiles().stream()
                .map(this::mapTileToDto)
                .collect(Collectors.toList());

        List<BattleLogDto> logs = battle.getLogs().stream()
                .map(this::mapLogToDto)
                .collect(Collectors.toList());

        return BattleStateDto.builder()
                .battleId(battle.getId())
                .maxRound(battle.getMaxRound())
                .round(battle.getRound())
                .status(battle.getStatus())
                .kingdoms(kingdoms)
                .tiles(tiles)
                .logs(logs)
                .build();
    }

    private KingdomDto mapKingdomToDto(Kingdom k) {
        List<Integer> history = new ArrayList<>();
        if (k.getScoreHistory() != null && !k.getScoreHistory().isEmpty()) {
            history = Arrays.stream(k.getScoreHistory().split(","))
                    .map(String::trim)
                    .map(Integer::parseInt)
                    .collect(Collectors.toList());
        }

        return KingdomDto.builder()
                .id(k.getId())
                .name(k.getName())
                .model(k.getModel())
                .population(k.getPopulation())
                .gold(k.getGold())
                .oil(k.getOil())
                .supplies(k.getSupplies())
                .energy(k.getEnergy())
                .infantry(k.getInfantry())
                .tanks(k.getTanks())
                .aircraft(k.getAircraft())
                .artillery(k.getArtillery())
                .navy(k.getNavy())
                .drones(k.getDrones())
                .soldiers(k.getSoldiers())
                .tech(k.getTech())
                .morale(k.getMorale())
                .score(k.getScore())
                .scoreHistory(history)
                .alive(k.isAlive())
                .ready(k.isReady())
                .color(k.getColor())
                .build();
    }

    private TileDto mapTileToDto(Tile t) {
        return TileDto.builder()
                .id(t.getId())
                .code(t.getCode())
                .x(t.getX())
                .y(t.getY())
                .type(t.getType())
                .ownerKingdomId(t.getOwnerKingdomId())
                .level(t.getLevel())
                .defenseBonus(t.getDefenseBonus())
                .build();
    }

    private BattleLogDto mapLogToDto(BattleLog l) {
        return BattleLogDto.builder()
                .id(l.getId())
                .roundNumber(l.getRoundNumber())
                .kingdomId(l.getKingdomId())
                .message(l.getMessage())
                .createdAt(l.getCreatedAt())
                .build();
    }

    @Override
    @Transactional
    public BattleStateDto joinBattle(String battleId, JoinBattleRequest request) {
        Battle battle = battleRepository.findById(battleId)
                .orElseThrow(() -> new com.nxh.redis.exception.AppException(com.nxh.redis.exception.ErrorCode.BATTLE_NOT_FOUND));

        if (!"WAITING".equals(battle.getStatus())) {
            throw new com.nxh.redis.exception.AppException(com.nxh.redis.exception.ErrorCode.BATTLE_ALREADY_STARTED);
        }

        List<Kingdom> kingdoms = battle.getKingdoms();
        if (kingdoms.size() >= 4) {
            throw new com.nxh.redis.exception.AppException(com.nxh.redis.exception.ErrorCode.BATTLE_ROOM_FULL);
        }

        // Validate unique name in the battle room
        for (Kingdom k : kingdoms) {
            if (k.getName() != null && k.getName().trim().equalsIgnoreCase(request.getName().trim())) {
                throw new IllegalArgumentException("Tên vương quốc '" + request.getName() + "' đã tồn tại trong phòng đấu.");
            }
        }

        String[] colors = {"#3b82f6", "#ef4444", "#10b981", "#8b5cf6", "#f97316"};
        int i = kingdoms.size();
        String kId = "k-" + (i + 1);
        int startSoldiers = 15;

        Kingdom kingdom = Kingdom.builder()
                .id(kId)
                .name(request.getName())
                .model(request.getModel())
                .apiKey(request.getApiKey())
                .color(colors[i % colors.length])
                .soldiers(startSoldiers)
                .gold(100)
                .supplies(120)
                .energy(80)
                .oil(50)
                .population(1000)
                .morale(85)
                .tech(1)
                .score(100)
                .scoreHistory("100")
                .infantry(startSoldiers * 60)
                .tanks(startSoldiers * 12)
                .aircraft(startSoldiers * 3)
                .artillery(startSoldiers * 9)
                .navy(startSoldiers * 1)
                .drones(startSoldiers * 5)
                .alive(true)
                .ready(false)
                .battle(battle)
                .build();

        kingdoms.add(kingdom);
        battleRepository.save(battle);

        // Broadcast PLAYER_JOINED event via WebSocket
        List<KingdomDto> kingdomDtos = kingdoms.stream()
                .map(this::mapKingdomToDto)
                .collect(Collectors.toList());

        Map<String, Object> event = Map.of(
                "type", "PLAYER_JOINED",
                "payload", Map.of(
                        "battleId", battleId,
                        "kingdoms", kingdomDtos
                )
        );

        try {
            webSocketHandler.broadcast(battleId, objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            log.error("Failed to broadcast PLAYER_JOINED event", e);
        }

        return mapToDto(battle);
    }

    @Override
    @Transactional
    public BattleStateDto quickJoin(JoinBattleRequest request) {
        List<Battle> waitingBattles = battleRepository.findByStatus("WAITING");
        
        Battle availableBattle = null;
        for (Battle b : waitingBattles) {
            List<Kingdom> kingdoms = kingdomRepository.findByBattleId(b.getId());
            if (kingdoms.size() < 4) {
                // Check if a kingdom with the same name exists in this room
                boolean nameConflict = false;
                for (Kingdom k : kingdoms) {
                    if (k.getName() != null && k.getName().trim().equalsIgnoreCase(request.getName().trim())) {
                        nameConflict = true;
                        break;
                    }
                }
                if (!nameConflict) {
                    availableBattle = b;
                    break;
                }
            }
        }

        if (availableBattle != null) {
            return joinBattle(availableBattle.getId(), request);
        } else {
            CreateBattleRequest createRequest = CreateBattleRequest.builder()
                    .maxRound(30)
                    .kingdoms(List.of(CreateBattleRequest.KingdomRequest.builder()
                            .name(request.getName())
                            .model(request.getModel())
                            .apiKey(request.getApiKey())
                            .build()))
                    .build();
            return createBattle(createRequest);
        }
    }

    @Override
    @Transactional
    public BattleStateDto toggleReady(String battleId, String kingdomId) {
        Battle battle = battleRepository.findById(battleId)
                .orElseThrow(() -> new com.nxh.redis.exception.AppException(com.nxh.redis.exception.ErrorCode.BATTLE_NOT_FOUND));

        if (!"WAITING".equals(battle.getStatus())) {
            throw new com.nxh.redis.exception.AppException(com.nxh.redis.exception.ErrorCode.BATTLE_ALREADY_STARTED);
        }

        List<Kingdom> kingdoms = battle.getKingdoms();
        Kingdom target = null;
        for (Kingdom k : kingdoms) {
            if (k.getId().equals(kingdomId)) {
                target = k;
                break;
            }
        }

        if (target == null) {
            throw new IllegalArgumentException("Kingdom not found in battle: " + kingdomId);
        }

        target.setReady(!target.isReady());
        kingdomRepository.save(target);

        // Broadcast LOBBY_UPDATE event via WebSocket
        List<KingdomDto> kingdomDtos = kingdoms.stream()
                .map(this::mapKingdomToDto)
                .collect(Collectors.toList());

        Map<String, Object> event = Map.of(
                "type", "LOBBY_UPDATE",
                "payload", Map.of(
                        "battleId", battleId,
                        "kingdoms", kingdomDtos
                )
        );

        try {
            webSocketHandler.broadcast(battleId, objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            log.error("Failed to broadcast LOBBY_UPDATE event", e);
        }

        return mapToDto(battle);
    }

    @Override
    @Transactional
    public BattleStateDto kickPlayer(String battleId, String kingdomId) {
        Battle battle = battleRepository.findById(battleId)
                .orElseThrow(() -> new com.nxh.redis.exception.AppException(com.nxh.redis.exception.ErrorCode.BATTLE_NOT_FOUND));

        if (!"WAITING".equals(battle.getStatus())) {
            throw new com.nxh.redis.exception.AppException(com.nxh.redis.exception.ErrorCode.BATTLE_ALREADY_STARTED);
        }

        if ("k-1".equals(kingdomId)) {
            throw new IllegalArgumentException("Cannot kick the host (k-1).");
        }

        List<Kingdom> kingdoms = battle.getKingdoms();
        Kingdom target = null;
        for (Kingdom k : kingdoms) {
            if (k.getId().equals(kingdomId)) {
                target = k;
                break;
            }
        }

        if (target == null) {
            throw new IllegalArgumentException("Kingdom not found in battle: " + kingdomId);
        }

        kingdoms.remove(target);
        kingdomRepository.delete(target);

        // Re-index remaining kingdoms
        String[] colors = {"#3b82f6", "#ef4444", "#10b981", "#8b5cf6", "#f97316"};
        for (int idx = 0; idx < kingdoms.size(); idx++) {
            Kingdom k = kingdoms.get(idx);
            k.setId("k-" + (idx + 1));
            k.setColor(colors[idx % colors.length]);
            kingdomRepository.save(k);
        }

        battleRepository.save(battle);

        // Broadcast WebSocket events
        List<KingdomDto> kingdomDtos = kingdoms.stream()
                .map(this::mapKingdomToDto)
                .collect(Collectors.toList());

        Map<String, Object> lobbyEvent = Map.of(
                "type", "LOBBY_UPDATE",
                "payload", Map.of(
                        "battleId", battleId,
                        "kingdoms", kingdomDtos
                )
        );

        Map<String, Object> kickEvent = Map.of(
                "type", "PLAYER_KICKED",
                "payload", Map.of(
                        "battleId", battleId,
                        "kickedKingdomId", kingdomId
                )
        );

        try {
            webSocketHandler.broadcast(battleId, objectMapper.writeValueAsString(lobbyEvent));
            webSocketHandler.broadcast(battleId, objectMapper.writeValueAsString(kickEvent));
        } catch (Exception e) {
            log.error("Failed to broadcast kick events", e);
        }

        return mapToDto(battle);
    }
}


