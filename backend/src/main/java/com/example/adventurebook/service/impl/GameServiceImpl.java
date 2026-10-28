package com.example.adventurebook.service.impl;

import com.example.adventurebook.dto.ChoiceDTO;
import com.example.adventurebook.dto.ConsequenceResultDTO;
import com.example.adventurebook.dto.GameStateDTO;
import com.example.adventurebook.entity.book.Book;
import com.example.adventurebook.entity.GameSession;
import com.example.adventurebook.entity.GameSave;
import com.example.adventurebook.entity.book.Consequence;
import com.example.adventurebook.entity.book.Option;
import com.example.adventurebook.entity.book.Section;
import com.example.adventurebook.enums.GameStatus;
import com.example.adventurebook.enums.SectionType;
import com.example.adventurebook.exception.InvalidBookException;
import com.example.adventurebook.exception.RevisionConflictException;
import com.example.adventurebook.exception.VersionConflictException;
import com.example.adventurebook.service.BookCatalogService;
import com.example.adventurebook.service.ConsequenceStrategy;
import com.example.adventurebook.service.GameService;
import com.example.adventurebook.utils.BookUtils;
import com.github.benmanes.caffeine.cache.Cache;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

/**
 * Stores game sessions in memory and applies server-authoritative move rules.
 * Session mutations are protected by the session monitor; requestId identifies duplicate requests.
 */
@Service
@RequiredArgsConstructor
public class GameServiceImpl implements GameService {

    private static final int INITIAL_HEALTH = 10;
    private static final int MAX_HEALTH = 10;
    private static final int MAX_IDEMPOTENCY_ENTRIES = 50;
    private final BookCatalogService books;
    private final ConsequenceStrategy consequenceStrategy;
    private final Cache<UUID, GameSession> sessions;

    /**
     * Starts a new game at the beginning of a validated book.
     */
    @Override
    public GameStateDTO start(String bookId) {

        Book book = books.require(bookId);

        if (!BookUtils.isPlayable(book)) {
            throw new InvalidBookException(book.getIssues());
        }

        GameSession session =
            new GameSession(
                UUID.randomUUID(),
                book.getId(),
                book.getVersion(),
                book.getBeginningId(),
                INITIAL_HEALTH,
                GameStatus.ACTIVE,
                0,
                null,
                0);

        sessions.put(session.getId(), session);

        return view(session, book);
    }

    /**
     * Refreshes the access time and builds the current state while holding the session lock.
     */
    @Override
    public GameStateDTO get(UUID id) {

        GameSession session = require(id);

        synchronized (session) {
            return view(session, books.require(session.getBookId()));
        }
    }

    /**
     * Checks duplicate requests, revision, and game status under one lock, then applies the move.
     * Duplicate detection precedes the revision check because an accepted move has already advanced it.
     */
    @Override
    public GameStateDTO choose(UUID id, String optionId, long expectedRevision, UUID requestId) {

        GameSession session = require(id);

        // Revision checks and the state change must share one lock, including save snapshots.
        synchronized (session) {
            Book book = books.require(session.getBookId());

            // Retry detection comes first: an accepted request has already advanced the revision.
            if (session.getCompletedRequests().contains(requestId)) {
                return view(session, book);
            }

            if (session.getRevision() != expectedRevision) {
                throw new RevisionConflictException(view(session, book));
            }

            if (session.getStatus() != GameStatus.ACTIVE) {
                throw new IllegalStateException("The game has already finished");
            }

            Section current = book.getSections().get(session.getSectionId());
            Option option = requireOption(current, optionId);
            Section next = book.getSections().get(option.getTargetSectionId());

            applyChoice(session, option, next, requestId);

            return view(session, book);
        }
    }

    // The caller holds the session monitor throughout validation, mutation and response creation.
    /**
     * Applies the consequence, clamps health to 0 through 10, and updates the section and status.
     * Death takes precedence over an ending; advances the revision once and retains the request ID.
     * The caller must hold the session monitor.
     */
    private void applyChoice(
        GameSession session, Option option, Section nextSection, UUID requestId) {

        Consequence consequence = option.getConsequence();

        int oldHealth = session.getHealth();
        int delta = consequenceStrategy.calculateHealthDelta(consequence);

        // Use long arithmetic: a valid consequence can contain Integer.MAX_VALUE.
        int health = (int) Math.clamp((long) oldHealth + delta, 0, MAX_HEALTH);

        session.setHealth(health);
        session.setHealthDelta(health - oldHealth);
        session.setConsequenceText(consequence == null ? null : consequence.getText());

        session.setSectionId(option.getTargetSectionId());

        // A lethal choice remains fatal even when it leads to an ending.
        if (session.getHealth() == 0) {
            session.setStatus(GameStatus.DEAD);
        } else if (nextSection.getType() == SectionType.END) {
            session.setStatus(GameStatus.COMPLETED);
        }

        session.setRevision(session.getRevision() + 1);

        // Retain recent request IDs in insertion order and bound memory per session.
        var completedRequests = session.getCompletedRequests();
        completedRequests.add(requestId);

        if (completedRequests.size() > MAX_IDEMPOTENCY_ENTRIES) {
            completedRequests.removeFirst();
        }
    }

    /**
     * Finds an option among those available in the current section or rejects the move.
     */
    private Option requireOption(Section section, String optionId) {

        for (Option option : section.getOptions()) {

            if (option.getId().equals(optionId)) {
                return option;
            }
        }

        throw new IllegalArgumentException("The option is not available in the current section");
    }

    /**
     * Removes a session from memory.
     */
    @Override
    public void stop(UUID id) {

        sessions.invalidate(id);
    }

    /**
     * Checks save compatibility with the book and registers a session with a fresh UUID.
     */
    @Override
    public GameStateDTO restore(GameSave save) {

        Book book = books.require(save.getBookId());

        if (!book.getVersion().equals(save.getBookVersion())) {
            throw new VersionConflictException("The saved book version is no longer available");
        }

        if (!book.getSections().containsKey(save.getSectionId())) {
            throw new VersionConflictException("The saved section no longer exists");
        }

        GameSession session =
            new GameSession(
                UUID.randomUUID(), save.getBookId(), save.getBookVersion(), save.getSectionId(),
                save.getHealth(), save.getStatus(), save.getRevision(), null, 0);

        sessions.put(session.getId(), session);

        return view(session, book);
    }

    /**
     * Returns the session for saving; the caller must lock it to capture a consistent snapshot.
     */
    @Override
    public GameSession requireForSave(UUID id) {

        return require(id);
    }

    /**
     * Looks up a session; the cache expires it after 12 hours without access.
     */
    private GameSession require(UUID id) {

        GameSession session = sessions.getIfPresent(id);

        if (session == null) {
            throw new NoSuchElementException("Game session not found or expired");
        }

        return session;
    }

    /**
     * Converts a server-side session into a DTO; finished games receive an empty option list.
     * The consequence reports the actual health change after clamping.
     */
    private GameStateDTO view(GameSession session, Book book) {

        Section section = book.getSections().get(session.getSectionId());
        List<ChoiceDTO> choices = List.of();

        if (session.getStatus() == GameStatus.ACTIVE) {
            choices = section.getOptions().stream()
                .map(option -> new ChoiceDTO(option.getId(), option.getDescription()))
                .toList();
        }

        ConsequenceResultDTO result = null;

        if (session.getConsequenceText() != null) {
            result = new ConsequenceResultDTO(session.getConsequenceText(), session.getHealthDelta());
        }

        return new GameStateDTO(
            session.getId(),
            book.getId(),
            book.getTitle(),
            section.getId(),
            section.getText(),
            choices,
            session.getHealth(),
            session.getStatus().name(),
            session.getRevision(),
            result);
    }

}
