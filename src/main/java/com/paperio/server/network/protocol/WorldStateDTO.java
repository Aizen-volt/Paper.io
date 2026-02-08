package com.paperio.server.network.protocol;

import java.util.List;

public record WorldStateDTO(
        long timestamp,
        List<PlayerDTO> players,
        List<LeaderboardEntryDTO> leaderboard
) {}