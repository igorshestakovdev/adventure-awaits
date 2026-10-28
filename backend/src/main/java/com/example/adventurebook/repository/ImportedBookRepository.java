package com.example.adventurebook.repository;

import com.example.adventurebook.entity.ImportedBook;

import java.time.OffsetDateTime;
import java.util.List;

public interface ImportedBookRepository {

    void save(String id, String content, OffsetDateTime importedAt);

    List<ImportedBook> findAll();
}
