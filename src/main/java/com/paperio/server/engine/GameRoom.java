package com.paperio.server.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paperio.server.config.GameProperties;
import com.paperio.server.model.Player;
import com.paperio.server.network.protocol.LeaderboardEntryDTO;
import com.paperio.server.network.protocol.WorldStateDTO;
import com.paperio.server.util.PlayerMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RequiredArgsConstructor
public class GameRoom {
    @Getter private final String roomId;
    private final GameProperties.MapConfig mapConfig;
    private final PhysicsProcessor physicsProcessor;
    private final CollisionProcessor collisionProcessor;
    private final ObjectMapper objectMapper;

    private final Map<String, Player> players = new ConcurrentHashMap<>();
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public void addPlayer(WebSocketSession session, Player player) {
        if (session != null) {
            sessions.put(session.getId(), session);
        }
        players.put(player.getId(), player);
    }

    public void removePlayer(String sessionId) {
        sessions.remove(sessionId);
        players.remove(sessionId);
    }

    public void handleInput(String sessionId, double x, double y) {
        var p = players.get(sessionId);
        if (p != null) {
            p.setTargetX(x);
            p.setTargetY(y);
        }
    }

    public int getPlayerCount() {
        return players.size();
    }

    public void tick() {
        if (players.isEmpty()) return;

        players.values().stream()
                .filter(Player::isAlive)
                .forEach(p -> physicsProcessor.movePlayer(p, mapConfig));

        collisionProcessor.processCollisions(players.values());

        players.values().removeIf(p -> {
            if (!p.isAlive()) {
                closeSession(p);
                return true;
            }
            return false;
        });

        broadcast();
    }

    private void closeSession(Player p) {
        var session = sessions.remove(p.getId());
        if (session != null && session.isOpen()) {
            try { session.close(new CloseStatus(4000, "DEATH")); }
            catch (Exception ignored) {}
        }
    }

    private void broadcast() {
        var playerDTOs = players.values().stream()
                .map(PlayerMapper::toDTO)
                .toList();

        var leaderboard = players.values().stream()
                .filter(Player::isAlive)
                .sorted((p1, p2) -> Integer.compare(p2.getScore(), p1.getScore()))
                .limit(5)
                .map(p -> new LeaderboardEntryDTO(p.getName(), p.getScore(), p.getColor()))
                .toList();

        var state = new WorldStateDTO(System.currentTimeMillis(), playerDTOs, leaderboard);

        try {
            var msg = new TextMessage(objectMapper.writeValueAsString(state));
            sessions.values().forEach(s -> {
                try { if (s.isOpen()) s.sendMessage(msg); } catch (Exception ignored) {}
            });
        } catch (Exception e) {
            log.error("Broadcast failed", e);
        }
    }
}