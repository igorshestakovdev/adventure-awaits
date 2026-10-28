package com.example.adventurebook.service.impl;

import com.example.adventurebook.dto.GameStateDTO;
import com.example.adventurebook.entity.GameSave;
import com.example.adventurebook.entity.GameSession;
import com.example.adventurebook.entity.SaveSummary;
import com.example.adventurebook.repository.SaveRepository;
import com.example.adventurebook.service.GameService;
import com.example.adventurebook.service.SaveService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

/**
 * Persists a consistent session snapshot in the database and restores it as a new session.
 */
@Service
@RequiredArgsConstructor
public class SaveServiceImpl implements SaveService {

    private final SaveRepository repository;
    private final GameService games;

    /**
     * Copies fields from one revision under the session monitor, then persists the snapshot outside the lock.
     */
    @Override
    public SaveSummary save(UUID sessionId, String name) {

        GameSession session = games.requireForSave(sessionId);
        GameSave save;

        // A save must capture all fields from the same revision of the session.
        synchronized (session) {
            save =
                new GameSave(
                    UUID.randomUUID(),
                    name.strip(),
                    session.getBookId(),
                    session.getBookVersion(),
                    session.getSectionId(),
                    session.getHealth(),
                    session.getStatus(),
                    session.getRevision(),
                    OffsetDateTime.now());
        }

        repository.save(save);

        return new SaveSummary(save.getId(), save.getName(), save.getBookId(), save.getSavedAt());
    }

    /**
     * Retrieves save summaries from the repository.
     */
    @Override
    public List<SaveSummary> list() {

        return repository.findAllSummaries();
    }

    /**
     * Loads a save, delegates compatibility checks to the game service, and returns the new session state.
     */
    @Override
    public GameStateDTO restore(UUID saveId) {

        GameSave save =
            repository.findById(saveId)
                .orElseThrow(() -> new NoSuchElementException("Save not found: " + saveId));

        return games.restore(save);
    }

    /**
     * Deletes a save or throws if it does not exist.
     */
    @Override
    public void delete(UUID saveId) {

        if (!repository.deleteById(saveId)) {
            throw new NoSuchElementException("Save not found: " + saveId);
        }
    }
}
