package com.paperio.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "game")
public record GameProperties(MapConfig map, PhysicsConfig physics) {
    public record MapConfig(int width, int height) {}
    public record PhysicsConfig(double speed, double turnSpeed, double startRadius) {}
}