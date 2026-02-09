package com.paperio.server.engine;

import com.paperio.server.config.GameProperties;
import com.paperio.server.model.Player;
import com.paperio.server.service.GeometryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class EntityFactory {
    private final GeometryService geoService;
    private final GameProperties props;

    public Player createHuman(WebSocketSession session, String name) {
        var spawn = getRandomSpawn();
        var territory = geoService.createInitialCircle(spawn.x, spawn.y, props.physics().startRadius());

        return new Player(
                session.getId(),
                name,
                spawn.x,
                spawn.y,
                props.physics(),
                territory
        );
    }

    public Player createBot() {
        String id = UUID.randomUUID().toString();
        String name = "Bot-" + id.substring(0, 4);
        var spawn = getRandomSpawn();
        var territory = geoService.createInitialCircle(spawn.x, spawn.y, props.physics().startRadius());

        Player bot = new Player(id, name, spawn.x, spawn.y, props.physics(), territory);
        bot.setBot(true);

        BotController controller = new BotController(bot, props.map(), props.bot());
        bot.setBotController(controller);

        return bot;
    }

    private SpawnPoint getRandomSpawn() {
        double x = 100 + Math.random() * (props.map().width() - 200);
        double y = 100 + Math.random() * (props.map().height() - 200);
        return new SpawnPoint(x, y);
    }

    private record SpawnPoint(double x, double y) {}
}