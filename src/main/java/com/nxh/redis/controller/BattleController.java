package com.nxh.redis.controller;

import com.nxh.redis.dto.ApiResponse;
import com.nxh.redis.dto.battle.BattleStateDto;
import com.nxh.redis.dto.battle.CreateBattleRequest;
import com.nxh.redis.dto.battle.JoinBattleRequest;
import com.nxh.redis.service.BattleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/battles")
@RequiredArgsConstructor
public class BattleController {

    private final BattleService battleService;

    @PostMapping
    public ResponseEntity<ApiResponse<BattleStateDto>> createBattle(@RequestBody CreateBattleRequest request) {
        BattleStateDto state = battleService.createBattle(request);
        return ResponseEntity.ok(ApiResponse.success("Khởi tạo trận đấu thành công", state));
    }

    @GetMapping("/{battleId}")
    public ResponseEntity<ApiResponse<BattleStateDto>> getBattleState(@PathVariable String battleId) {
        BattleStateDto state = battleService.getBattleState(battleId);
        return ResponseEntity.ok(ApiResponse.success(state));
    }

    @PostMapping("/{battleId}/start")
    public ResponseEntity<ApiResponse<Void>> startBattle(@PathVariable String battleId) {
        battleService.startBattle(battleId);
        return ResponseEntity.ok(ApiResponse.success("Battle simulation started in the background.", null));
    }

    @PostMapping("/{battleId}/join")
    public ResponseEntity<ApiResponse<BattleStateDto>> joinBattle(
            @PathVariable String battleId,
            @RequestBody JoinBattleRequest request
    ) {
        BattleStateDto state = battleService.joinBattle(battleId, request);
        return ResponseEntity.ok(ApiResponse.success("Tham gia phòng đấu thành công", state));
    }

    @PostMapping("/quick-join")
    public ResponseEntity<ApiResponse<BattleStateDto>> quickJoin(
            @RequestBody JoinBattleRequest request
    ) {
        BattleStateDto state = battleService.quickJoin(request);
        return ResponseEntity.ok(ApiResponse.success("Khớp phòng đấu thành công", state));
    }

    @PostMapping("/{battleId}/ready")
    public ResponseEntity<ApiResponse<BattleStateDto>> toggleReady(
            @PathVariable String battleId,
            @RequestParam String kingdomId
    ) {
        BattleStateDto state = battleService.toggleReady(battleId, kingdomId);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái sẵn sàng thành công", state));
    }

    @PostMapping("/{battleId}/kick/{kingdomId}")
    public ResponseEntity<ApiResponse<BattleStateDto>> kickPlayer(
            @PathVariable String battleId,
            @PathVariable String kingdomId
    ) {
        BattleStateDto state = battleService.kickPlayer(battleId, kingdomId);
        return ResponseEntity.ok(ApiResponse.success("Đuổi người chơi khỏi phòng thành công", state));
    }
}



