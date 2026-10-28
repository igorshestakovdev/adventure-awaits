package com.example.adventurebook.entity;

import com.example.adventurebook.enums.GameStatus;

import java.util.LinkedHashSet;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * Mutable state of an active game session.
 * Callers must hold this session's monitor when reading or changing gameplay state.
 * Session lifetime is managed by the session cache.
 */
@Getter
@Setter
@AllArgsConstructor
public class GameSession {

    private final UUID id;
    private final String bookId;
    private final String bookVersion;
    private long sectionId;
    private int health;
    private GameStatus status;
    private long revision;
    private String consequenceText;
    private int healthDelta;
    private final LinkedHashSet<UUID> completedRequests = new LinkedHashSet<>();
}
