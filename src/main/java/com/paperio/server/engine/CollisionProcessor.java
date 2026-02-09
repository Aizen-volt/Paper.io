package com.paperio.server.engine;

import com.paperio.server.config.GameProperties;
import com.paperio.server.model.Player;
import com.paperio.server.service.GeometryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.*;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CollisionProcessor {
    private final GeometryService geoService;
    private final GameProperties props;
    private final GeometryFactory factory = new GeometryFactory();

    public void processCollisions(SpatialGrid grid, Iterable<Player> allPlayers) {
        for (Player attacker : allPlayers) {
            if (!attacker.isAlive()) continue;

            checkSelfCollision(attacker);

            for (Player victim : grid.getPotentialColliders(attacker)) {
                if (attacker == victim || !victim.isAlive()) continue;
                handlePvP(attacker, victim);
            }
        }
    }

    private void checkSelfCollision(Player p) {
        if (p.getTrailPoints().size() < 20) return;
        var subList = p.getTrailPoints().subList(0, p.getTrailPoints().size() - 15);
        var oldTrailLine = geoService.createLine(subList);
        var head = factory.createPoint(new Coordinate(p.getX(), p.getY()));

        if (oldTrailLine != null && oldTrailLine.distance(head) < props.combat().selfKillDistance()) {
            p.setAlive(false);
        }
    }

    private void handlePvP(Player attacker, Player victim) {
        Point head = factory.createPoint(new Coordinate(attacker.getX(), attacker.getY()));
        LineString victimTrail = geoService.createLine(victim.getTrailPoints());

        if (victimTrail != null && victimTrail.distance(head) < props.combat().killDistance()) {
            victim.setAlive(false);
        }

        try {
            if (attacker.getTerritory().getEnvelopeInternal().intersects(victim.getTerritory().getEnvelopeInternal())) {
                if (attacker.getTerritory().intersects(victim.getTerritory())) {
                    victim.setTerritory(victim.getTerritory().difference(attacker.getTerritory()));
                    if (victim.getTerritory().isEmpty()) victim.setAlive(false);
                }
            }
        } catch (Exception e) {
            log.error("Collision processing failed", e);
        }
    }
}