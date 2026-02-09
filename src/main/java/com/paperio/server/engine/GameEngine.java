package com.paperio.server.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paperio.server.config.GameProperties;
import com.paperio.server.model.Player;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;

@Service
@Slf4j
public class GameEngine {
    private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();
    private final Map<String, String> sessionRoomMap = new ConcurrentHashMap<>();

    private final GameProperties props;
    private final EntityFactory entityFactory;
    private final PhysicsProcessor physicsProcessor;
    private final CollisionProcessor collisionProcessor;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService gameExecutor;

    public GameEngine(GameProperties props, EntityFactory entityFactory,
                      PhysicsProcessor physicsProcessor, CollisionProcessor collisionProcessor,
                      @Qualifier("gameExecutor") ExecutorService gameExecutor) {
        this.props = props;
        this.entityFactory = entityFactory;
        this.physicsProcessor = physicsProcessor;
        this.collisionProcessor = collisionProcessor;
        this.gameExecutor = gameExecutor;
    }

    @PostConstruct
    public void wakeUpCommonPool() {
        ForkJoinPool.commonPool().submit(() -> log.info("Common pool warmed up")).join();
    }

    public void joinGame(WebSocketSession session, String playerName) {
        var room = findBestRoom();
        Player player = entityFactory.createHuman(session, playerName);
        room.addPlayer(session, player);
        sessionRoomMap.put(session.getId(), room.getRoomId());
    }

    private GameRoom findBestRoom() {
        return rooms.values().stream()
                .filter(r -> r.getPlayerCount() < props.room().maxPlayers())
                .findFirst()
                .orElseGet(this::createRoom);
    }

    private GameRoom createRoom() {
        String id = UUID.randomUUID().toString();
        var room = new GameRoom(id, props, entityFactory, physicsProcessor, collisionProcessor, objectMapper);
        rooms.put(id, room);
        log.info("New room created with UUID: {}", id);
        return room;
    }

    @Scheduled(fixedRate = 16)
    public void serverTick() {
        rooms.values().forEach(room ->
                gameExecutor.submit(() -> {
                    try {
                        room.tick();
                    } catch (Exception e) {
                        log.error("Error ticking room {}", room.getRoomId(), e);
                    }
                })
        );

        long now = System.currentTimeMillis();
        rooms.entrySet().removeIf(entry -> {
            GameRoom room = entry.getValue();
            boolean isEmpty = room.getPlayerCount() == 0;
            boolean isOldEnough = (now - room.getCreatedAt()) > props.room().gracePeriodMs();

            if (isEmpty && isOldEnough) {
                log.info("Room Pruning: Closing inactive room {}", entry.getKey());
                return true;
            }
            return false;
        });
    }

    @Scheduled(fixedRate = 1000)
    public void manageBotPopulation() {
        rooms.values().forEach(room -> {
            try {
                room.maintainPopulation();
            } catch (Exception e) {
                log.error("Error maintaining population in room {}", room.getRoomId(), e);
            }
        });
    }

    public void leaveGame(String id) {
        var roomId = sessionRoomMap.remove(id);
        if (roomId != null) {
            var room = rooms.get(roomId);
            if (room != null) {
                room.removePlayer(id);
                if (room.getPlayerCount() == 0) {
                    rooms.remove(roomId);
                    log.info("Room {} purged from engine", roomId);
                }
            }
        }
    }

    public void handleInput(String id, double x, double y) {
        var roomId = sessionRoomMap.get(id);
        if (roomId != null) {
            var room = rooms.get(roomId);
            if (room != null) room.handleInput(id, x, y);
        }
    }

    public Integer getTotalPlayerCount() {
        return rooms.values().stream().mapToInt(GameRoom::getPlayerCount).sum();
    }

    public Integer getRoomCount() {
        return rooms.size();
    }
}