package com.example.adventurebook.repository.impl;

import com.example.adventurebook.entity.GameSave;
import com.example.adventurebook.entity.SaveSummary;
import com.example.adventurebook.enums.GameStatus;
import com.example.adventurebook.repository.SaveRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcSaveRepository implements SaveRepository {

    private final JdbcClient jdbc;

    @Override
    public void save(GameSave save) {
        jdbc.sql(
                "INSERT INTO game_saves (id, name, book_id, book_version, section_id, health, status,"
                    + " revision, saved_at) VALUES (:id, :name, :bookId, :version, :sectionId, :health,"
                    + " :status, :revision, :savedAt)")
            .param("id", save.getId())
            .param("name", save.getName())
            .param("bookId", save.getBookId())
            .param("version", save.getBookVersion())
            .param("sectionId", save.getSectionId())
            .param("health", save.getHealth())
            .param("status", save.getStatus().name())
            .param("revision", save.getRevision())
            .param("savedAt", save.getSavedAt())
            .update();
    }

    @Override
    public List<SaveSummary> findAllSummaries() {
        return jdbc.sql("SELECT id, name, book_id, saved_at FROM game_saves ORDER BY saved_at DESC")
            .query(
                (rs, row) ->
                    new SaveSummary(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getString("book_id"),
                        rs.getObject("saved_at", OffsetDateTime.class)))
            .list();
    }

    @Override
    public Optional<GameSave> findById(UUID id) {
        return jdbc.sql("SELECT * FROM game_saves WHERE id = :id")
            .param("id", id)
            .query(
                (rs, number) ->
                    new GameSave(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getString("book_id"),
                        rs.getString("book_version"),
                        rs.getLong("section_id"),
                        rs.getInt("health"),
                        GameStatus.valueOf(rs.getString("status")),
                        rs.getLong("revision"),
                        rs.getObject("saved_at", OffsetDateTime.class)))
            .optional();
    }

    @Override
    public boolean deleteById(UUID id) {
        return jdbc.sql("DELETE FROM game_saves WHERE id = :id")
            .param("id", id)
            .update() > 0;
    }
}
