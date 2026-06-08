package com.nxh.redis.service;

import com.nxh.redis.dto.battle.BattleStateDto;
import com.nxh.redis.dto.battle.CreateBattleRequest;
import com.nxh.redis.dto.battle.JoinBattleRequest;

public interface BattleService {
    BattleStateDto createBattle(CreateBattleRequest request);
    BattleStateDto getBattleState(String battleId);
    void startBattle(String battleId);
    BattleStateDto joinBattle(String battleId, JoinBattleRequest request);
    BattleStateDto quickJoin(JoinBattleRequest request);
    BattleStateDto toggleReady(String battleId, String kingdomId);
    BattleStateDto kickPlayer(String battleId, String kingdomId);
}



