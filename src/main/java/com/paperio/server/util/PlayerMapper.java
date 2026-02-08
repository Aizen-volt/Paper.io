package com.paperio.server.util;

import com.paperio.server.model.Player;
import com.paperio.server.network.protocol.PlayerDTO;
import org.locationtech.jts.geom.Polygon;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

public class PlayerMapper {
    private PlayerMapper() {}


    public static PlayerDTO toDTO(Player p) {
        var trail = p.getTrailPoints().stream()
                .map(c -> List.of(c.x, c.y))
                .toList();

        var territory = IntStream.range(0, p.getTerritory().getNumGeometries())
                .mapToObj(p.getTerritory()::getGeometryN)
                .filter(Polygon.class::isInstance)
                .map(Polygon.class::cast)
                .map(poly -> Arrays.stream(poly.getExteriorRing().getCoordinates())
                        .map(c -> List.of(c.x, c.y))
                        .toList())
                .toList();

        return new PlayerDTO(
                p.getId(),
                p.getName(),
                p.getColor(),
                p.getX(),
                p.getY(),
                p.getScore(),
                p.getAngle(),
                p.isAlive(),
                trail,
                territory
        );
    }
}