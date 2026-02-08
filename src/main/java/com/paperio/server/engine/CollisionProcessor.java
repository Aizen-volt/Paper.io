package com.paperio.server.engine;

import com.paperio.server.model.Player;
import com.paperio.server.service.GeometryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.*;
import org.springframework.stereotype.Component;
import java.util.Collection;

@Component
@RequiredArgsConstructor
@Slf4j
public class CollisionProcessor {
    private final GeometryService geoService;
    private final GeometryFactory factory = new GeometryFactory();

    public void processCollisions(Collection<Player> players) {
        players.stream().filter(Player::isAlive).forEach(attacker -> {
            checkSelfCollision(attacker);
            players.stream()
                    .filter(v -> v.isAlive() && !v.getId().equals(attacker.getId()))
                    .forEach(victim -> handlePvP(attacker, victim));
        });
    }

    private void checkSelfCollision(Player p) {
        if (p.getTrailPoints().size() < 20) return;

        var oldTrailLine = geoService.createLine(p.getTrailPoints().subList(0, p.getTrailPoints().size() - 15));
        var head = factory.createPoint(new Coordinate(p.getX(), p.getY()));

        if (oldTrailLine != null && oldTrailLine.distance(head) < 5.0) {
            p.setAlive(false);
        }
    }

    private void handlePvP(Player attacker, Player victim) {
        Point head = factory.createPoint(new Coordinate(attacker.getX(), attacker.getY()));
        LineString victimTrail = geoService.createLine(victim.getTrailPoints());

        if (victimTrail != null && victimTrail.distance(head) < 15.0) {
            victim.setAlive(false);
        }

        try {
            if (attacker.getTerritory().intersects(victim.getTerritory())) {
                victim.setTerritory(victim.getTerritory().difference(attacker.getTerritory()));
                if (victim.getTerritory().isEmpty()) victim.setAlive(false);
            }
        } catch (Exception e) {
            log.error("Collision processing failed: ", e);
        }
    }
}