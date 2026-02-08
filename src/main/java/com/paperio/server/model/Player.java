package com.paperio.server.model;

import com.paperio.server.config.GameProperties;
import com.paperio.server.util.ColorGenerator;
import lombok.Getter;
import lombok.Setter;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class Player {
    private final String id;
    private final String name;
    private final String color;
    private final GameProperties.PhysicsConfig physics;

    private boolean isAlive = true;
    private int score = 0;
    private double x;
    private double y;
    private double angle;
    private double targetX;
    private double targetY;

    private Geometry territory;
    private final List<Coordinate> trailPoints = new ArrayList<>();

    public Player(String id, String name, double startX, double startY,
                  GameProperties.PhysicsConfig physics, Geometry initialTerritory) {
        this.id = id;
        this.name = name;
        this.physics = physics;
        this.x = startX;
        this.y = startY;
        this.targetX = startX;
        this.targetY = startY;
        this.color = ColorGenerator.nextColor();
        this.territory = initialTerritory;
    }
}