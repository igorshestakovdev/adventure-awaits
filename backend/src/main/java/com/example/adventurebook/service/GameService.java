package com.example.adventurebook.service;

import com.example.adventurebook.dto.GameStateDTO;
import com.example.adventurebook.entity.GameSession;
import com.example.adventurebook.entity.GameSave;

import java.util.UUID;

/**
 * Manages server-authoritative game session state and move execution.
 */
public interface GameService {

    /**
     * Creates a session at BEGIN with 10 health points and revision 0.
     * @param bookId ID of a book without validation errors
     * @return the initial game state
     */
    GameStateDTO start(String bookId);

    /**
     * Returns the server-side session state and refreshes its last access time.
     * @throws java.util.NoSuchElementException if the session is missing or expired
     */
    GameStateDTO get(UUID id);

    /**
     * Applies an available option to an active session while holding its state lock.
     * A repeated requestId retained in history returns the current state without another move.
     * @param id session ID
     * @param optionId option ID in the current section
     * @param expectedRevision revision on which the client bases this move
     * @param requestId request ID used to suppress duplicate moves
     * @return the state after the move, or the current state for a duplicate request
     * @throws com.example.adventurebook.exception.RevisionConflictException if the revision is stale
     */
    GameStateDTO choose(UUID id, String optionId, long expectedRevision, UUID requestId);

    /**
     * Removes a session from memory; a missing session does not cause an error.
     */
    void stop(UUID id);

    /**
     * Creates a new session from a save after checking the book version and section existence.
     * @return the restored state with a fresh session UUID and the saved revision
     * @throws com.example.adventurebook.exception.VersionConflictException if the version or section is unavailable
     */
    GameStateDTO restore(GameSave save);

    /**
     * Returns an existing, unexpired session for snapshot creation.
     * The caller must hold the session monitor while reading its fields for a save.
     */
    GameSession requireForSave(UUID id);
}
