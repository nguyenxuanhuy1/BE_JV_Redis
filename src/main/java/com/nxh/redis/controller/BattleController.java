package com.nxh.redis.controller;

import com.nxh.redis.dto.battle.BattleStateDto;
import com.nxh.redis.dto.battle.CreateBattleRequest;
import com.nxh.redis.service.BattleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/battles")
@RequiredArgsConstructor
public class BattleController {

    private final BattleService battleService;

    @PostMapping
    public ResponseEntity<BattleStateDto> createBattle(@RequestBody CreateBattleRequest request) {
        BattleStateDto state = battleService.createBattle(request);
        return ResponseEntity.ok(state);
    }

    @GetMapping("/{battleId}")
    public ResponseEntity<BattleStateDto> getBattleState(@PathVariable String battleId) {
        BattleStateDto state = battleService.getBattleState(battleId);
        return ResponseEntity.ok(state);
    }

    @PostMapping("/{battleId}/start")
    public ResponseEntity<Map<String, Object>> startBattle(@PathVariable String battleId) {
        battleService.startBattle(battleId);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Battle simulation started in the background."
        ));
    }
}
