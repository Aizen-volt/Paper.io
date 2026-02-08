package com.paperio.server.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paperio.server.config.GameProperties;
import com.paperio.server.model.Player;
import com.paperio.server.service.GeometryService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@Slf4j
public class GameEngine {
    private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();
    private final Map<String, String> sessionRoomMap = new ConcurrentHashMap<>();
    private final AtomicInteger roomCounter = new AtomicInteger(1);

    private final GameProperties props;
    private final GeometryService geoService;
    private final PhysicsProcessor physicsProcessor;
    private final CollisionProcessor collisionProcessor;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void wakeUpCommonPool() {
        ForkJoinPool.commonPool().submit(() -> log.info("Common pool warmed up")).join();
    }

    public void joinGame(WebSocketSession session, String playerName) {
        var room = findBestRoom();
        double startX = 100 + Math.random() * (props.map().width() - 200);
        double startY = 100 + Math.random() * (props.map().height() - 200);

        var initialTerritory = geoService.createInitialCircle(startX, startY, props.physics().startRadius());
        var player = new Player(session.getId(), playerName, startX, startY, props.physics(), initialTerritory);

        room.addPlayer(session, player);
        sessionRoomMap.put(session.getId(), room.getRoomId());
    }

    private GameRoom findBestRoom() {
        return rooms.values().stream().filter(r -> r.getPlayerCount() < 20).findFirst().orElseGet(this::createRoom);
    }

    private GameRoom createRoom() {
        var id = "room-" + roomCounter.getAndIncrement();
        var room = new GameRoom(id, props.map(), physicsProcessor, collisionProcessor, objectMapper);
        rooms.put(id, room);
        return room;
    }

    @Scheduled(fixedRate = 16)
    public void serverTick() { rooms.values().parallelStream().forEach(GameRoom::tick); }

    public void leaveGame(String id) {
        var roomId = sessionRoomMap.remove(id);
        if (roomId != null) rooms.get(roomId).removePlayer(id);
    }

    public void handleInput(String id, double x, double y) {
        var roomId = sessionRoomMap.get(id);
        if (roomId != null) rooms.get(roomId).handleInput(id, x, y);
    }
}