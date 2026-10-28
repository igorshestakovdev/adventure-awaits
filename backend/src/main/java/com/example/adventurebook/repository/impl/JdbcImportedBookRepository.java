package com.example.adventurebook.repository.impl;

import com.example.adventurebook.entity.ImportedBook;
import com.example.adventurebook.repository.ImportedBookRepository;

import java.time.OffsetDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcImportedBookRepository implements ImportedBookRepository {

    private final JdbcClient jdbc;

    @Override
    public void save(String id, String content, OffsetDateTime importedAt) {
        jdbc.sql("MERGE INTO imported_books KEY(id) VALUES (:id, :content, :importedAt)")
            .param("id", id)
            .param("content", content)
            .param("importedAt", importedAt)
            .update();
    }

    @Override
    public List<ImportedBook> findAll() {
        return jdbc.sql("SELECT id, content, imported_at FROM imported_books ORDER BY imported_at")
            .query(
                (rs, rowNum) ->
                    new ImportedBook(
                        rs.getString("id"),
                        rs.getString("content"),
                        rs.getObject("imported_at", OffsetDateTime.class)))
            .list();
    }
}
