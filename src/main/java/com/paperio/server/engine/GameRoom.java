package com.paperio.server.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paperio.server.config.GameProperties;
import com.paperio.server.model.Player;
import com.paperio.server.network.protocol.LeaderboardEntryDTO;
import com.paperio.server.network.protocol.PlayerDTO;
import com.paperio.server.network.protocol.WorldStateDTO;
import com.paperio.server.util.PlayerMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class GameRoom {
    @Getter private final String roomId;
    private final GameProperties.MapConfig mapConfig;
    private final PhysicsProcessor physicsProcessor;
    private final CollisionProcessor collisionProcessor;
    private final ObjectMapper objectMapper;
    private final GeometryFactory geoFactory = new GeometryFactory();

    private static final double VISIBILITY_RADIUS = 1200.0;

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
        Map<String, PlayerDTO> allPlayerDTOs = players.values().stream()
                .collect(Collectors.toMap(Player::getId, PlayerMapper::toDTO));

        var leaderboard = players.values().stream()
                .filter(Player::isAlive)
                .sorted((p1, p2) -> Integer.compare(p2.getScore(), p1.getScore()))
                .limit(5)
                .map(p -> new LeaderboardEntryDTO(p.getName(), p.getScore(), p.getColor()))
                .toList();

        sessions.forEach((sessionId, session) -> {
            if (!session.isOpen()) return;

            Player me = players.get(sessionId);
            if (me == null) return;

            List<PlayerDTO> visiblePlayers = players.values().stream()
                    .filter(other -> isVisible(me, other))
                    .map(other -> allPlayerDTOs.get(other.getId()))
                    .toList();

            WorldStateDTO state = new WorldStateDTO(System.currentTimeMillis(), visiblePlayers, leaderboard);

            try {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(state)));
            } catch (Exception e) {
                log.error("Failed to send AOI update to {}", me.getName());
            }
        });
    }

    private boolean isVisible(Player observer, Player target) {
        if (observer.getId().equals(target.getId())) return true;

        double r = VISIBILITY_RADIUS;
        Envelope visionEnvelope = new Envelope(
                observer.getX() - r, observer.getX() + r,
                observer.getY() - r, observer.getY() + r
        );

        if (target.getTerritory().getEnvelopeInternal().intersects(visionEnvelope)) {
            return true;
        }

        Point head = geoFactory.createPoint(new Coordinate(target.getX(), target.getY()));
        return visionEnvelope.intersects(head.getCoordinate());
    }
}