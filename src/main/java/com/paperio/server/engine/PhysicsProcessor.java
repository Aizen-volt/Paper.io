package com.paperio.server.engine;

import com.paperio.server.config.GameProperties;
import com.paperio.server.model.Player;
import com.paperio.server.service.GeometryService;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PhysicsProcessor {
    private final GeometryService geoService;
    private final GeometryFactory factory = new GeometryFactory();

    public void movePlayer(Player p, GameProperties.MapConfig map) {
        double targetAngle = Math.atan2(p.getTargetY() - p.getY(), p.getTargetX() - p.getX());
        double diff = targetAngle - p.getAngle();
        while (diff <= -Math.PI) diff += 2 * Math.PI;
        while (diff > Math.PI) diff -= 2 * Math.PI;

        double turnSpeed = p.getPhysics().turnSpeed();
        p.setAngle(p.getAngle() + Math.clamp(diff, -turnSpeed, turnSpeed));

        p.setX(p.getX() + Math.cos(p.getAngle()) * p.getPhysics().speed());
        p.setY(p.getY() + Math.sin(p.getAngle()) * p.getPhysics().speed());

        p.setX(Math.clamp(p.getX(), 0, map.width()));
        p.setY(Math.clamp(p.getY(), 0, map.height()));

        processTrail(p);
    }

    private void processTrail(Player p) {
        Coordinate currentPos = new Coordinate(p.getX(), p.getY());
        var headPoint = factory.createPoint(currentPos);

        if (!p.getTerritory().intersects(headPoint.buffer(2.0))) {
            if (p.getTrailPoints().isEmpty() || currentPos.distance(p.getTrailPoints().getLast()) > 2.0) {
                p.getTrailPoints().add(currentPos);
            }
        } else if (!p.getTrailPoints().isEmpty()) {
            p.setTerritory(geoService.conquer(p.getTerritory(), p.getTrailPoints()));
            p.getTrailPoints().clear();
            p.setScore((int)(p.getTerritory().getArea() / 300.0));
        }
    }
}