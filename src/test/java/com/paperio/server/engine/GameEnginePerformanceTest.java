package com.paperio.server.engine;

import com.paperio.server.config.GameProperties;
import com.paperio.server.service.GeometryService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GameEnginePerformanceTest {
    @ParameterizedTest(name = "Engine stress test: {0} players")
    @ValueSource(ints = {100, 500, 1000, 2000, 3000})
    void enginePerformanceTest(int totalPlayers) {
        var geoService = new GeometryService();
        var physics = new PhysicsProcessor(geoService);
        var collision = new CollisionProcessor(geoService);
        var props = new GameProperties(
                new GameProperties.MapConfig(3000, 3000),
                new GameProperties.PhysicsConfig(4.0, 0.1, 50.0)
        );

        GameEngine engine = new GameEngine(props, geoService, physics, collision);

        for (int i = 0; i < totalPlayers; i++) {
            WebSocketSession session = Mockito.mock(WebSocketSession.class);
            Mockito.when(session.getId()).thenReturn("session-" + i);
            engine.joinGame(session, "Player-" + i);
        }

        for (int i = 0; i < 50; i++) engine.serverTick();

        List<Long> durations = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            long start = System.nanoTime();
            engine.serverTick();
            long end = System.nanoTime();
            durations.add((end - start) / 1_000_000);
        }

        double avg = durations.stream().mapToLong(Long::longValue).average().orElse(0);
        System.out.println("TOTAL PLAYERS: " + totalPlayers);
        System.out.println("ROOMS CREATED: " + (totalPlayers / 20));
        System.out.println("AVG TICK TIME: " + String.format("%.2f", avg) + "ms");

        assertThat(avg).isLessThan(16.0);
    }
}