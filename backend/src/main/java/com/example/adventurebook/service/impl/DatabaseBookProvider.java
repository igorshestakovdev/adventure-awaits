package com.example.adventurebook.service.impl;

import com.example.adventurebook.entity.book.Book;
import com.example.adventurebook.entity.ImportedBook;
import com.example.adventurebook.repository.ImportedBookRepository;
import com.example.adventurebook.service.BookParserService;
import com.example.adventurebook.service.BookProvider;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

/**
 * Loads imported books from the database and reparses their stored JSON.
 */
@Component
@RequiredArgsConstructor
public class DatabaseBookProvider implements BookProvider {

    private final ImportedBookRepository repository;
    private final BookParserService parser;

    /**
     * Returns the imported book source label for loading logs.
     */
    @Override
    public String name() {

        return "imported";
    }

    /**
     * Reads stored JSON from the database and reparses it to obtain book models and diagnostics.
     */
    @Override
    public Map<String, Book> loadBooks() {

        Map<String, Book> loaded = new LinkedHashMap<>();

        for (ImportedBook record : repository.findAll()) {

            loaded.put(
                record.getId(),
                parser.parse(record.getId(), record.getContent().getBytes(StandardCharsets.UTF_8)));
        }

        return loaded;
    }
}
