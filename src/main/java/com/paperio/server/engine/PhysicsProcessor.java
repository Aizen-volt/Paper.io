package com.paperio.server.engine;

import com.paperio.server.config.GameProperties;
import com.paperio.server.model.Player;
import com.paperio.server.service.GeometryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;

@Component
@RequiredArgsConstructor
@Slf4j
public class PhysicsProcessor {
    private final GeometryService geoService;
    private final GameProperties props;
    private final GeometryFactory factory = new GeometryFactory();

    public void movePlayer(Player p, GameProperties.MapConfig map, Collection<Player> allPlayers) {
        double targetAngle = Math.atan2(p.getTargetY() - p.getY(), p.getTargetX() - p.getX());
        double diff = targetAngle - p.getAngle();
        while (diff <= -Math.PI) diff += 2 * Math.PI;
        while (diff > Math.PI) diff -= 2 * Math.PI;

        double turnSpeed = props.physics().turnSpeed();
        p.setAngle(p.getAngle() + Math.clamp(diff, -turnSpeed, turnSpeed));

        p.setX(p.getX() + Math.cos(p.getAngle()) * props.physics().speed());
        p.setY(p.getY() + Math.sin(p.getAngle()) * props.physics().speed());

        p.setX(Math.clamp(p.getX(), 0, map.width()));
        p.setY(Math.clamp(p.getY(), 0, map.height()));

        processTrail(p, allPlayers);
    }

    private void processTrail(Player p, Collection<Player> allPlayers) {
        Coordinate currentPos = new Coordinate(p.getX(), p.getY());
        var headPoint = factory.createPoint(currentPos);
        double buffer = props.combat().trailSafetyBuffer();

        if (!p.getTerritory().intersects(headPoint.buffer(buffer))) {
            if (p.getTrailPoints().isEmpty() || currentPos.distance(p.getTrailPoints().getLast()) > buffer) {
                p.getTrailPoints().add(currentPos);
            }
        } else if (!p.getTrailPoints().isEmpty()) {
            try {
                Geometry oldTerritory = p.getTerritory();

                Geometry newTerritory = geoService.conquer(oldTerritory, p.getTrailPoints());

                Geometry gainedTerritory = newTerritory.difference(oldTerritory);

                p.setTerritory(newTerritory);
                p.getTrailPoints().clear();
                p.setScore((int) (newTerritory.getArea() / 300.0));

                if (!gainedTerritory.isEmpty()) {
                    stealTerritory(p, gainedTerritory, allPlayers);
                }

            } catch (Exception e) {
                log.error("Error processing trail closure for player {}", p.getName(), e);
                p.getTrailPoints().clear();
            }
        }
    }

    private void stealTerritory(Player attacker, Geometry gainedTerritory, Collection<Player> allPlayers) {
        for (Player victim : allPlayers) {
            if (victim.getId().equals(attacker.getId())) continue;
            if (!victim.isAlive()) continue;

            try {
                if (victim.getTerritory().getEnvelopeInternal().intersects(gainedTerritory.getEnvelopeInternal())) {
                    Geometry reduced = victim.getTerritory().difference(gainedTerritory);

                    victim.setTerritory(reduced);

                    if (victim.getTerritory().isEmpty()) {
                        victim.setAlive(false);
                    } else {
                        victim.setScore((int) (victim.getTerritory().getArea() / 300.0));
                    }
                }
            } catch (Exception e) {
                log.debug("Topology error during territory stealing", e);
            }
        }
    }
}