package com.paperio.server.engine;

import com.paperio.server.config.GameProperties;
import com.paperio.server.model.Player;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Point;

import java.util.List;
import java.util.Random;

@RequiredArgsConstructor
public class BotController {
    private final Player player;
    private final GameProperties.MapConfig mapConfig;
    private final GameProperties.BotConfig botConfig;
    private final Random random = new Random();

    private BotState state = BotState.ROAMING;
    private int stateTimer = 0;

    private enum BotState { ROAMING, EXPANDING, RETURNING, EVADING }

    public void tick() {
        if (checkImmediateDanger()) {
            state = BotState.EVADING;
            stateTimer = botConfig.reactionTimeFrames();
        }

        switch (state) {
            case EVADING -> handleEvasion();
            case RETURNING -> handleReturn();
            case EXPANDING -> handleExpansion();
            case ROAMING -> handleRoaming();
        }

        if (stateTimer > 0) stateTimer--;
    }

    private void handleRoaming() {
        if (stateTimer <= 0) {
            double angle = random.nextDouble() * 2 * Math.PI;
            setTargetInDirection(angle, 500);
            state = BotState.EXPANDING;
            stateTimer = 100;
        }
    }

    private void handleExpansion() {
        if (player.getTrailPoints().size() > botConfig.maxTrailLength()) {
            state = BotState.RETURNING;
            return;
        }

        if (stateTimer <= 0) {
            double currentAngle = player.getAngle();
            double turn = (random.nextBoolean() ? 0.5 : -0.5);
            setTargetInDirection(currentAngle + turn, 500);
            stateTimer = 30;
        }
    }

    private void handleReturn() {
        Point center = player.getTerritory().getCentroid();
        player.setTargetX(center.getX());
        player.setTargetY(center.getY());

        if (player.getTrailPoints().isEmpty()) {
            state = BotState.ROAMING;
            stateTimer = 20;
        }
    }

    private void handleEvasion() {
        if (stateTimer <= 0) {
            state = player.getTrailPoints().isEmpty() ? BotState.EXPANDING : BotState.RETURNING;
        }
    }

    private boolean checkImmediateDanger() {
        double dist = botConfig.lookaheadDist();
        double lookX = player.getX() + Math.cos(player.getAngle()) * dist;
        double lookY = player.getY() + Math.sin(player.getAngle()) * dist;

        if (lookX < 0 || lookX > mapConfig.width() || lookY < 0 || lookY > mapConfig.height()) {
            avoidWall(lookX, lookY);
            return true;
        }

        List<Coordinate> trail = player.getTrailPoints();
        if (trail.size() > 10) {
            for (int i = 0; i < trail.size() - 5; i++) {
                if (trail.get(i).distance(new Coordinate(lookX, lookY)) < 15.0) {
                    setTargetInDirection(player.getAngle() + Math.PI / 2, 200);
                    return true;
                }
            }
        }
        return false;
    }

    private void avoidWall(double lookX, double lookY) {
        double angle = player.getAngle();
        if (lookX < 0 || lookX > mapConfig.width()) angle = Math.PI - angle;
        if (lookY < 0 || lookY > mapConfig.height()) angle = -angle;
        setTargetInDirection(angle, 300);
    }

    private void setTargetInDirection(double angle, double dist) {
        player.setTargetX(player.getX() + Math.cos(angle) * dist);
        player.setTargetY(player.getY() + Math.sin(angle) * dist);
    }
}