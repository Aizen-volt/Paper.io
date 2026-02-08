package com.paperio.server.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paperio.server.engine.GameEngine;
import com.paperio.server.network.protocol.InitPacket;
import com.paperio.server.network.protocol.InputPacket;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;

@Slf4j
@Component
public class GameSocketHandler extends TextWebSocketHandler {

    private final GameEngine gameEngine;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GameSocketHandler(GameEngine gameEngine) {
        this.gameEngine = gameEngine;
    }

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) throws IOException {
        String playerName = extractNameFromSession(session);
        log.info("New connection: SessionID={} Name={}", session.getId(), playerName);
        gameEngine.joinGame(session, playerName);

        var init = new InitPacket("INIT", session.getId());
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(init)));

        log.info("Player joined: {} with ID: {}", playerName, session.getId());
    }

    @Override
    protected void handleTextMessage(@NonNull WebSocketSession session, TextMessage message) throws Exception {
        var packet = objectMapper.readValue(message.getPayload(), InputPacket.class);
        gameEngine.handleInput(session.getId(), packet.x(), packet.y());
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        log.info("Connection closed: SessionID={} Status={}", session.getId(), status);
        gameEngine.leaveGame(session.getId());
    }

    private String extractNameFromSession(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri != null && uri.getQuery() != null) {
            String query = uri.getQuery();
            if (query.startsWith("name=")) {
                return query.split("=")[1];
            }
        }
        return "Guest-" + session.getId().substring(0, 4);
    }
}