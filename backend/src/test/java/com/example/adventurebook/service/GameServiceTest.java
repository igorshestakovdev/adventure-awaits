package com.example.adventurebook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.adventurebook.entity.book.Book;
import com.example.adventurebook.entity.GameSave;
import com.example.adventurebook.entity.book.Consequence;
import com.example.adventurebook.entity.book.Option;
import com.example.adventurebook.entity.book.Section;
import com.example.adventurebook.enums.ConsequenceType;
import com.example.adventurebook.enums.Difficulty;
import com.example.adventurebook.enums.GameStatus;
import com.example.adventurebook.enums.SectionType;
import com.example.adventurebook.exception.RevisionConflictException;
import com.example.adventurebook.exception.VersionConflictException;
import com.example.adventurebook.repository.SaveRepository;
import com.example.adventurebook.service.impl.DefaultConsequenceStrategy;
import com.example.adventurebook.service.impl.GameServiceImpl;
import com.example.adventurebook.service.impl.SaveServiceImpl;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GameServiceTest {

    private GameService games;
    private Book book;
    private AtomicLong time;

    @BeforeEach
    void setUp() {
        book = book();
        BookCatalogService catalog = mock(BookCatalogService.class);
        when(catalog.require("demo")).thenReturn(book);

        time = new AtomicLong();
        games = new GameServiceImpl(catalog, new DefaultConsequenceStrategy(),
            Caffeine.newBuilder()
                .expireAfterAccess(Duration.ofHours(12))
                .ticker(time::get)
                .build());
    }

    @Test
    void startsWithTenHealthAndCompletesOnEnd() {
        var start = games.start("demo");

        assertThat(start.getHealth()).isEqualTo(10);

        var result = games.choose(start.getSessionId(), "1", 0, UUID.randomUUID());

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(result.getHealth()).isEqualTo(7);
    }

    @Test
    void deathHasPriorityOverEndAndDuplicateRequestIsIdempotent() {
        var start = games.start("demo");
        UUID requestId = UUID.randomUUID();

        var result = games.choose(start.getSessionId(), "2", 0, requestId);

        assertThat(result.getStatus()).isEqualTo("DEAD");
        assertThat(result.getHealth()).isZero();

        assertThat(games.choose(start.getSessionId(), "2", 0, requestId).getRevision()).isEqualTo(1);

        assertThatThrownBy(() -> games.choose(start.getSessionId(), "2", 1, UUID.randomUUID()))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void staleRevisionDoesNotChangeState() {
        var start = games.start("demo");

        assertThatThrownBy(() -> games.choose(start.getSessionId(), "1", 5, UUID.randomUUID()))
            .isInstanceOf(RevisionConflictException.class);
        assertThat(games.get(start.getSessionId()).getRevision()).isZero();
    }

    @Test
    void maximumHealingIsCappedWithoutOverflow() {
        book.getSections().get(1L).getOptions().getFirst()
            .setConsequence(new Consequence(ConsequenceType.GAIN_HEALTH, Integer.MAX_VALUE, "Healed"));
        var start = games.start("demo");

        var result = games.choose(start.getSessionId(), "1", 0, UUID.randomUUID());

        assertThat(result.getHealth()).isEqualTo(10);
        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(result.getConsequence().getHealthDelta()).isZero();
    }

    @Test
    void choiceWithoutConsequenceClearsPreviousResultAndRetryDoesNotApplyAgain() {
        Section beginning = book.getSections().get(1L);
        beginning.setOptions(List.of(
            new Option("1", "Loop", 1, beginning.getOptions().getFirst().getConsequence()),
            new Option("2", "Finish", 2, null)));
        var start = games.start("demo");
        UUID firstRequest = UUID.randomUUID();

        var first = games.choose(start.getSessionId(), "1", 0, firstRequest);
        var result = games.choose(start.getSessionId(), "2", 1, UUID.randomUUID());
        var retry = games.choose(start.getSessionId(), "1", 0, firstRequest);

        assertThat(first.getHealth()).isEqualTo(7);
        assertThat(first.getConsequence()).isNotNull();
        assertThat(result.getHealth()).isEqualTo(7);
        assertThat(result.getConsequence()).isNull();
        assertThat(retry.getRevision()).isEqualTo(2);
        assertThat(retry.getHealth()).isEqualTo(7);
        assertThat(retry.getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void concurrentRequestsWithSameRevisionApplyOnlyOne() throws Exception {
        var start = games.start("demo");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);

        Callable<Object> action =
            () -> {
                ready.countDown();
                go.await();

                try {
                    return games.choose(start.getSessionId(), "1", 0, UUID.randomUUID());
                } catch (RuntimeException exception) {
                    return exception;
                }
            };

        Future<Object> first = executor.submit(action);
        Future<Object> second = executor.submit(action);
        ready.await(2, TimeUnit.SECONDS);
        go.countDown();
        List<Object> results = List.of(first.get(), second.get());
        executor.shutdownNow();

        assertThat(
            results.stream().filter(com.example.adventurebook.dto.GameStateDTO.class::isInstance))
            .hasSize(1);
        assertThat(results.stream().filter(RevisionConflictException.class::isInstance)).hasSize(1);
        assertThat(games.get(start.getSessionId()).getHealth()).isEqualTo(7);
    }

    @Test
    void saveReleasesSessionBeforePersistenceAndRetainsCapturedRevision() {
        var start = games.start("demo");
        var session = games.requireForSave(start.getSessionId());
        SaveRepository repository = mock(SaveRepository.class);
        SaveService saves = new SaveServiceImpl(repository, games);

        doAnswer(invocation -> {
            assertThat(Thread.holdsLock(session)).isFalse();
            GameSave snapshot = invocation.getArgument(0);

            var result = games.choose(start.getSessionId(), "1", 0, UUID.randomUUID());

            assertThat(result.getRevision()).isEqualTo(1);
            assertThat(snapshot.getRevision()).isZero();
            assertThat(snapshot.getSectionId()).isEqualTo(start.getSectionId());
            assertThat(snapshot.getHealth()).isEqualTo(10);
            assertThat(snapshot.getStatus().name()).isEqualTo("ACTIVE");

            return null;
        }).when(repository).save(any(GameSave.class));

        var summary = saves.save(start.getSessionId(), " Checkpoint ");

        assertThat(summary.getName()).isEqualTo("Checkpoint");
        assertThat(games.get(start.getSessionId()).getRevision()).isEqualTo(1);
    }

    @Test
    void expiresSessionAfterTwelveHoursWithoutAccess() {
        var start = games.start("demo");
        time.addAndGet(Duration.ofHours(12).toNanos());

        assertThatThrownBy(() -> games.get(start.getSessionId()))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void accessExtendsSessionLifetimeAndStopRemovesIt() {
        var start = games.start("demo");
        time.addAndGet(Duration.ofHours(11).toNanos());
        games.get(start.getSessionId());
        time.addAndGet(Duration.ofHours(11).toNanos());

        assertThat(games.get(start.getSessionId()).getHealth()).isEqualTo(10);

        games.stop(start.getSessionId());

        assertThatThrownBy(() -> games.get(start.getSessionId()))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void restoresSavedStateAndRejectsIncompatibleBooks() {
        GameSave save = new GameSave(UUID.randomUUID(), "Checkpoint", "demo", "v1",
            2, 7, GameStatus.COMPLETED, 4, OffsetDateTime.now());

        var restored = games.restore(save);

        assertThat(restored.getSectionId()).isEqualTo(2);
        assertThat(restored.getHealth()).isEqualTo(7);
        assertThat(restored.getRevision()).isEqualTo(4);
        assertThat(restored.getStatus()).isEqualTo("COMPLETED");
        assertThat(restored.getChoices()).isEmpty();
        assertThat(games.get(restored.getSessionId()).getRevision()).isEqualTo(4);

        save.setBookVersion("old");
        assertThatThrownBy(() -> games.restore(save)).isInstanceOf(VersionConflictException.class);

        save.setBookVersion("v1");
        save.setSectionId(404);
        assertThatThrownBy(() -> games.restore(save)).isInstanceOf(VersionConflictException.class);
    }

    private Book book() {
        var mild = new Consequence(ConsequenceType.LOSE_HEALTH, 3, "A scratch");
        var fatal = new Consequence(ConsequenceType.LOSE_HEALTH, 20, "A fatal fall");

        var start =
            new Section(
                1,
                "Start",
                SectionType.BEGIN,
                List.of(
                    new Option("1", "Safe", 2, mild), new Option("2", "Risky", 2, fatal)));
        var end = new Section(2, "End", SectionType.END, List.of());

        return new Book(
            "demo",
            "Demo",
            "Tester",
            Difficulty.EASY,
            "v1",
            Map.of(1L, start, 2L, end),
            1,
            List.of());
    }
}
