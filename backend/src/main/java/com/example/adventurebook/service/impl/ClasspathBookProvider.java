package com.example.adventurebook.service.impl;

import com.example.adventurebook.entity.book.Book;
import com.example.adventurebook.service.BookParserService;
import com.example.adventurebook.service.BookProvider;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import lombok.RequiredArgsConstructor;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Loads bundled JSON books from books/*.json resources, using file names as book IDs.
 */
@Component
@RequiredArgsConstructor
public class ClasspathBookProvider implements BookProvider {

    private final BookParserService parser;

    /**
     * Returns the bundled book source label for loading logs.
     */
    @Override
    public String name() {

        return "bundled";
    }

    /**
     * Finds book JSON resources, derives IDs from file names, and passes their content to the parser.
     * @throws IllegalStateException if resources cannot be discovered or read
     */
    @Override
    public Map<String, Book> loadBooks() {

        Map<String, Book> loaded = new LinkedHashMap<>();

        try {
            Resource[] resources =
                new PathMatchingResourcePatternResolver().getResources("classpath*:books/*.json");

            for (Resource resource : resources) {

                String filename = Objects.requireNonNullElse(resource.getFilename(), "unknown.json");
                String id = filename.replaceFirst("\\.json$", "");

                loaded.put(id, parser.parse(id, resource.getInputStream().readAllBytes()));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not scan bundled books", exception);
        }

        return loaded;
    }
}
