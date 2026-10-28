package com.example.adventurebook.service;

import com.example.adventurebook.entity.book.Book;

import java.util.Map;

/**
 * Source of books loaded into the catalog at application startup.
 */
public interface BookProvider {

    /**
     * Returns the source name used in loading logs.
     */
    String name();

    /**
     * Loads and parses books from this source, preserving their diagnostic messages.
     * @return books indexed by identifier
     */
    Map<String, Book> loadBooks();
}
