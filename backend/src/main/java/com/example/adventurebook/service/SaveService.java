package com.example.adventurebook.service;

import com.example.adventurebook.dto.GameStateDTO;
import com.example.adventurebook.entity.SaveSummary;

import java.util.List;
import java.util.UUID;

/**
 * Creates persistent saves and restores game sessions.
 */
public interface SaveService {

    /**
     * Captures a consistent session snapshot under its lock and persists it in the database.
     * @param sessionId ID of the session to save
     * @param name save name; leading and trailing whitespace is removed
     * @return a summary of the created save
     */
    SaveSummary save(UUID sessionId, String name);

    /**
     * Returns save summaries from persistent storage.
     */
    List<SaveSummary> list();

    /**
     * Loads a save and creates a new game session from it.
     * @param saveId save ID
     * @return the restored game state
     * @throws java.util.NoSuchElementException if the save is missing
     * @throws com.example.adventurebook.exception.VersionConflictException if the saved book version is unavailable
     */
    GameStateDTO restore(UUID saveId);

    /**
     * Deletes a save from persistent storage.
     * @param saveId save ID
     * @throws java.util.NoSuchElementException if the save is missing
     */
    void delete(UUID saveId);
}
