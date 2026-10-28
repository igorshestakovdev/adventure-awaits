package com.example.adventurebook.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.adventurebook.entity.GameSave;
import com.example.adventurebook.entity.ImportedBook;
import com.example.adventurebook.enums.GameStatus;
import com.example.adventurebook.repository.impl.JdbcImportedBookRepository;
import com.example.adventurebook.repository.impl.JdbcSaveRepository;

import java.time.OffsetDateTime;
import java.util.UUID;

import liquibase.integration.spring.SpringLiquibase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.junit.jupiter.api.AfterEach;

class JdbcRepositoriesTest {

    private EmbeddedDatabase database;
    private JdbcSaveRepository saves;
    private JdbcImportedBookRepository books;
    private final OffsetDateTime now = OffsetDateTime.parse("2026-10-28T10:00:00+03:00");

    @BeforeEach
    void setUp() throws Exception {
        database = new EmbeddedDatabaseBuilder().generateUniqueName(true)
            .setType(EmbeddedDatabaseType.H2).build();

        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(database);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
        liquibase.afterPropertiesSet();

        JdbcClient jdbc = JdbcClient.create(database);
        saves = new JdbcSaveRepository(jdbc);
        books = new JdbcImportedBookRepository(jdbc);
    }

    @AfterEach
    void tearDown() {
        database.shutdown();
    }

    @Test
    void emptyDatabaseHasNoRecords() {
        assertThat(saves.findAllSummaries()).isEmpty();
        assertThat(saves.findById(UUID.randomUUID())).isEmpty();
        assertThat(books.findAll()).isEmpty();
    }

    @Test
    void savesRoundTripAllFieldsAndListNewestFirst() {
        GameSave older = save("Older", GameStatus.ACTIVE, now.minusDays(1));
        GameSave newer = save("New chapter", GameStatus.DEAD, now);
        saves.save(newer);
        saves.save(older);

        assertThat(saves.findById(older.getId())).hasValue(older);
        assertThat(saves.findById(newer.getId())).hasValue(newer);
        assertThat(saves.findAllSummaries()).extracting(item -> item.getId())
            .containsExactly(newer.getId(), older.getId());
        assertThat(saves.findAllSummaries().getFirst()).satisfies(summary -> {
            assertThat(summary.getName()).isEqualTo(newer.getName());
            assertThat(summary.getBookId()).isEqualTo(newer.getBookId());
            assertThat(summary.getSavedAt()).isEqualTo(now);
        });
    }

    @Test
    void duplicateSaveIdFailsAndPreservesOriginal() {
        GameSave original = save("Original", GameStatus.COMPLETED, now);
        saves.save(original);
        GameSave duplicate = save("Changed", GameStatus.ACTIVE, now);
        duplicate.setId(original.getId());

        assertThatThrownBy(() -> saves.save(duplicate)).isInstanceOf(DuplicateKeyException.class);
        assertThat(saves.findById(original.getId())).hasValue(original);
    }

    @Test
    void deleteByIdRemovesSaveAndReturnsStatus() {
        GameSave record = save("To Delete", GameStatus.ACTIVE, now);
        saves.save(record);

        assertThat(saves.deleteById(record.getId())).isTrue();
        assertThat(saves.findById(record.getId())).isEmpty();
        assertThat(saves.deleteById(record.getId())).isFalse();
    }

    @Test
    void importedBooksRoundTripLargeUnicodeContentAndUpdateById() {
        String content = "{\"title\":\"Adventure\",\"text\":\"" + "Story — ".repeat(1000) + "\"}";
        books.save("new", content, now);
        books.save("old", "{}", now.minusDays(1));

        assertThat(books.findAll()).containsExactly(
            new ImportedBook("old", "{}", now.minusDays(1)), new ImportedBook("new", content, now));

        books.save("old", "{\"title\":\"Updated\"}", now.plusDays(1));

        assertThat(books.findAll()).containsExactly(new ImportedBook("new", content, now),
            new ImportedBook("old", "{\"title\":\"Updated\"}", now.plusDays(1)));
    }

    private GameSave save(String name, GameStatus status, OffsetDateTime timestamp) {
        return new GameSave(UUID.randomUUID(), name, "book-id", "version-1", Long.MAX_VALUE,
            status == GameStatus.DEAD ? 0 : 7, status, 5_000_000_000L, timestamp);
    }
}
