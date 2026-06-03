package com.nxh.redis.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
@Slf4j
public class BattleWebSocketHandler extends TextWebSocketHandler {

    // Key: battleId, Value: Set of Web Session
    private final Map<String, Set<WebSocketSession>> battleSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String battleId = extractBattleId(session);
        if (battleId != null) {
            battleSessions.computeIfAbsent(battleId, k -> new CopyOnWriteArraySet<>()).add(session);
            log.info("Client connected to battle {}. Total sessions: {}", battleId, battleSessions.get(battleId).size());
        } else {
            log.warn("Connection established but no battleId found in URI: {}", session.getUri());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String battleId = extractBattleId(session);
        if (battleId != null) {
            Set<WebSocketSession> sessions = battleSessions.get(battleId);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    battleSessions.remove(battleId);
                }
                log.info("Client disconnected from battle {}. Session remaining: {}", battleId, sessions.size());
            }
        }
    }

    public void broadcast(String battleId, String messageJson) {
        Set<WebSocketSession> sessions = battleSessions.get(battleId);
        if (sessions != null && !sessions.isEmpty()) {
            TextMessage message = new TextMessage(messageJson);
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    try {
                        session.sendMessage(message);
                    } catch (IOException e) {
                        log.error("Failed to send WebSocket message to session in battle {}: {}", battleId, e.getMessage());
                    }
                }
            }
        }
    }

    private String extractBattleId(WebSocketSession session) {
        if (session.getUri() == null) return null;
        String path = session.getUri().getPath();
        // Path format: /ws-battle/{battleId}
        String[] parts = path.split("/");
        if (parts.length > 0) {
            return parts[parts.length - 1];
        }
        return null;
    }
}
