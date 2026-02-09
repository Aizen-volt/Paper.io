package com.paperio.server.network.protocol;

import java.util.List;

public record WorldStateDTO(
        long timestamp,
        int allPlayers,
        List<PlayerDTO> visiblePlayers,
        List<LeaderboardEntryDTO> leaderboard
) {}