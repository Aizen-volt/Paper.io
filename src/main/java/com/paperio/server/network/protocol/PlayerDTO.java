package com.paperio.server.network.protocol;

import java.util.List;

public record PlayerDTO(
        String id,
        String name,
        String color,
        double x,
        double y,
        int score,
        double angle,
        boolean isAlive,
        List<List<Double>> trail,
        List<List<List<Double>>> territory
) {}