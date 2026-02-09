package com.paperio.server.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paperio.server.config.GameProperties;
import com.paperio.server.model.Player;
import com.paperio.server.network.protocol.LeaderboardEntryDTO;
import com.paperio.server.network.protocol.PlayerDTO;
import com.paperio.server.network.protocol.WorldStateDTO;
import com.paperio.server.util.PlayerMapper;
import lombok.Getter;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Slf4j
public class GameRoom {
    @Getter private final String roomId;
    private final GameProperties props;
    private final PhysicsProcessor physicsProcessor;
    private final CollisionProcessor collisionProcessor;
    private final ObjectMapper objectMapper;
    private final EntityFactory entityFactory;

    private final SpatialGrid spatialGrid;
    private final GeometryFactory geoFactory = new GeometryFactory();

    private final Lock tickLock = new ReentrantLock();

    @Getter
    private final long createdAt = System.currentTimeMillis();

    private final Map<String, Player> players = new ConcurrentHashMap<>();
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final List<BotController> botControllers = new CopyOnWriteArrayList<>();

    public GameRoom(String roomId, GameProperties props, EntityFactory entityFactory,
                    PhysicsProcessor physicsProcessor, CollisionProcessor collisionProcessor,
                    ObjectMapper objectMapper) {
        this.roomId = roomId;
        this.props = props;
        this.entityFactory = entityFactory;
        this.physicsProcessor = physicsProcessor;
        this.collisionProcessor = collisionProcessor;
        this.objectMapper = objectMapper;
        this.spatialGrid = new SpatialGrid(props.map().width(), props.map().height(), props.map().gridCellSize());
    }

    public void addPlayer(WebSocketSession session, Player player) {
        if (session != null) sessions.put(session.getId(), session);
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
        if (!tickLock.tryLock()) {
            return;
        }

        try {
            if (players.isEmpty()) return;

            botControllers.forEach(BotController::tick);

            for (Player p : players.values()) {
                if (p.isAlive()) {
                    physicsProcessor.movePlayer(p, props.map(), players.values());
                }
            }

            spatialGrid.clear();
            for (Player p : players.values()) {
                if (p.isAlive()) {
                    spatialGrid.insert(p);
                }
            }

            collisionProcessor.processCollisions(spatialGrid, players.values());

            players.values().removeIf(p -> {
                if (!p.isAlive()) {
                    handleDeath(p);
                    return true;
                }
                return false;
            });

            broadcast();
        } finally {
            tickLock.unlock();
        }
    }

    private void handleDeath(Player p) {
        if (p.isBot()) {
            botControllers.remove(p.getBotController());
        } else {
            closeSession(p);
        }
    }

    private void closeSession(Player p) {
        var session = sessions.remove(p.getId());
        if (session != null && session.isOpen()) {
            try { session.close(new CloseStatus(4000, "DEATH")); }
            catch (Exception ignored) {}
        }
    }

    public void maintainPopulation() {
        if (tickLock.tryLock()) {
            try {
                int currentCount = players.size();
                if (currentCount < props.room().botTarget() && currentCount < props.room().maxPlayers()) {
                    Player bot = entityFactory.createBot();
                    botControllers.add(bot.getBotController());
                    this.addPlayer(null, bot);
                }
            } finally {
                tickLock.unlock();
            }
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

        int allPlayersCount = players.size();

        sessions.forEach((sessionId, session) -> {
            if (!session.isOpen()) return;

            Player me = players.get(sessionId);
            if (me == null) return;

            List<PlayerDTO> visiblePlayers = players.values().stream()
                    .filter(other -> isVisible(me, other))
                    .map(other -> allPlayerDTOs.get(other.getId()))
                    .toList();

            WorldStateDTO state = new WorldStateDTO(System.currentTimeMillis(), allPlayersCount, visiblePlayers, leaderboard);

            try {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(state)));
            } catch (Exception e) {
                log.debug("Failed to send AOI update to {}", me.getName());
            }
        });
    }

    private boolean isVisible(Player observer, Player target) {
        if (observer.getId().equals(target.getId())) return true;
        double r = props.room().visibilityRadius();
        Envelope visionEnvelope = new Envelope(
                observer.getX() - r, observer.getX() + r,
                observer.getY() - r, observer.getY() + r
        );
        if (target.getTerritory().getEnvelopeInternal().intersects(visionEnvelope)) return true;
        Point head = geoFactory.createPoint(new Coordinate(target.getX(), target.getY()));
        return visionEnvelope.intersects(head.getCoordinate());
    }
}