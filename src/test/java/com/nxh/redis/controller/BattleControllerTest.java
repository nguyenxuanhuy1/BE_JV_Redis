package com.nxh.redis.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nxh.redis.dto.battle.BattleStateDto;
import com.nxh.redis.dto.battle.CreateBattleRequest;
import com.nxh.redis.dto.battle.JoinBattleRequest;
import com.nxh.redis.service.BattleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BattleController.class)
@AutoConfigureMockMvc(addFilters = false)
class BattleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BattleService battleService;

    @MockBean
    private com.nxh.redis.security.JwtService jwtService;

    @MockBean
    private com.nxh.redis.repository.UserRepository userRepository;

    @Test
    void createBattle_Success() throws Exception {
        CreateBattleRequest request = CreateBattleRequest.builder()
                .maxRound(30)
                .build();

        BattleStateDto stateDto = BattleStateDto.builder()
                .battleId("battle-1")
                .status("WAITING")
                .build();

        when(battleService.createBattle(any(CreateBattleRequest.class))).thenReturn(stateDto);

        mockMvc.perform(post("/api/battles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Khởi tạo trận đấu thành công"))
                .andExpect(jsonPath("$.data.battleId").value("battle-1"));
    }

    @Test
    void getBattleState_Success() throws Exception {
        BattleStateDto stateDto = BattleStateDto.builder()
                .battleId("battle-1")
                .status("WAITING")
                .build();

        when(battleService.getBattleState("battle-1")).thenReturn(stateDto);

        mockMvc.perform(get("/api/battles/battle-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.battleId").value("battle-1"));
    }

    @Test
    void startBattle_Success() throws Exception {
        doNothing().when(battleService).startBattle("battle-1");

        mockMvc.perform(post("/api/battles/battle-1/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Battle simulation started in the background."));
    }

    @Test
    void joinBattle_Success() throws Exception {
        JoinBattleRequest request = JoinBattleRequest.builder()
                .name("Player 2")
                .model("gpt-4")
                .apiKey("test-key")
                .build();

        BattleStateDto stateDto = BattleStateDto.builder()
                .battleId("battle-1")
                .status("WAITING")
                .build();

        when(battleService.joinBattle(eq("battle-1"), any(JoinBattleRequest.class))).thenReturn(stateDto);

        mockMvc.perform(post("/api/battles/battle-1/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Tham gia phòng đấu thành công"));
    }

    @Test
    void quickJoin_Success() throws Exception {
        JoinBattleRequest request = JoinBattleRequest.builder()
                .name("Player 2")
                .build();

        BattleStateDto stateDto = BattleStateDto.builder()
                .battleId("battle-1")
                .status("WAITING")
                .build();

        when(battleService.quickJoin(any(JoinBattleRequest.class))).thenReturn(stateDto);

        mockMvc.perform(post("/api/battles/quick-join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Khớp phòng đấu thành công"));
    }

    @Test
    void toggleReady_Success() throws Exception {
        BattleStateDto stateDto = BattleStateDto.builder()
                .battleId("battle-1")
                .status("WAITING")
                .build();

        when(battleService.toggleReady("battle-1", "k-2")).thenReturn(stateDto);

        mockMvc.perform(post("/api/battles/battle-1/ready")
                        .param("kingdomId", "k-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Cập nhật trạng thái sẵn sàng thành công"));
    }

    @Test
    void kickPlayer_Success() throws Exception {
        BattleStateDto stateDto = BattleStateDto.builder()
                .battleId("battle-1")
                .status("WAITING")
                .build();

        when(battleService.kickPlayer("battle-1", "k-2")).thenReturn(stateDto);

        mockMvc.perform(post("/api/battles/battle-1/kick/k-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Đuổi người chơi khỏi phòng thành công"));
    }
}
