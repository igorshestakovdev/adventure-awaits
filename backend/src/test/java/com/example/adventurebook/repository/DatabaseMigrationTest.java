package com.example.adventurebook.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.adventurebook.entity.GameSave;
import com.example.adventurebook.entity.ImportedBook;
import com.example.adventurebook.enums.GameStatus;
import com.example.adventurebook.repository.impl.JdbcImportedBookRepository;
import com.example.adventurebook.repository.impl.JdbcSaveRepository;
import java.time.OffsetDateTime;
import java.util.UUID;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class DatabaseMigrationTest {

    @Test
    void createsDatabaseAndPreservesDataOnRepeatedStartup() throws Exception {
        EmbeddedDatabase database = new EmbeddedDatabaseBuilder().generateUniqueName(true)
            .setType(EmbeddedDatabaseType.H2).build();

        try {
            migrate(database);

            JdbcClient jdbc = JdbcClient.create(database);
            JdbcImportedBookRepository books = new JdbcImportedBookRepository(jdbc);
            JdbcSaveRepository saves = new JdbcSaveRepository(jdbc);
            OffsetDateTime now = OffsetDateTime.parse("2026-10-28T10:00:00+03:00");
            ImportedBook book = new ImportedBook("test-book", "{\"title\":\"Story\"}", now);
            GameSave save = new GameSave(UUID.randomUUID(), "Test save", book.getId(),
                "version-1", 42L, 7, GameStatus.ACTIVE, 3L, now);
            books.save(book.getId(), book.getContent(), book.getImportedAt());
            saves.save(save);

            migrate(database);

            assertThat(books.findAll()).containsExactly(book);
            assertThat(saves.findById(save.getId())).hasValue(save);
            assertThat(jdbc.sql("SELECT COUNT(*) FROM DATABASECHANGELOG")
                .query(Long.class).single()).isEqualTo(1L);
            assertThat(jdbc.sql("SELECT LOCKED FROM DATABASECHANGELOGLOCK WHERE ID = 1")
                .query(Boolean.class).single()).isFalse();
        } finally {
            database.shutdown();
        }
    }

    private void migrate(DataSource database) throws Exception {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(database);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
        liquibase.afterPropertiesSet();
    }
}
