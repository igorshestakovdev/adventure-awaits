package com.example.adventurebook.exception;

import com.example.adventurebook.dto.GameStateDTO;

import lombok.Getter;

@Getter
public class RevisionConflictException extends RuntimeException {

    private final GameStateDTO current;

    public RevisionConflictException(GameStateDTO current) {

        super("Game state revision is stale");

        this.current = current;
    }
}
