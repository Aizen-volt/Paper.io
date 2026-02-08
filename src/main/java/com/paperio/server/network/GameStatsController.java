package com.paperio.server.network;

import com.paperio.server.engine.GameEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class GameStatsController {
    private final GameEngine gameEngine;

    @GetMapping("/stats")
    public Map<String, Integer> getGlobalStats() {
        return Map.of(
                "players", gameEngine.getTotalPlayerCount(),
                "rooms", gameEngine.getRoomCount()
        );
    }
}