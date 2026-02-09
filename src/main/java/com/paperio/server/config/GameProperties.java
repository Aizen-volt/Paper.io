package com.paperio.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "game")
public record GameProperties(
        MapConfig map,
        PhysicsConfig physics,
        RoomConfig room,
        BotConfig bot,
        CombatConfig combat
) {
    public record MapConfig(int width, int height, int gridCellSize) {}

    public record PhysicsConfig(double speed, double turnSpeed, double startRadius) {}

    public record RoomConfig(int maxPlayers, int botTarget, long gracePeriodMs, double visibilityRadius) {}

    public record BotConfig(int maxTrailLength, double lookaheadDist, double randomTurnChance, int reactionTimeFrames) {}

    public record CombatConfig(double killDistance, double selfKillDistance, double trailSafetyBuffer) {}
}