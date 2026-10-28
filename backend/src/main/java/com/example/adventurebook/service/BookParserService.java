package com.example.adventurebook.service;

import com.example.adventurebook.entity.book.Book;

/**
 * Parses book JSON while collecting diagnostic messages.
 */
public interface BookParserService {

    /**
     * Parses JSON fields, then validates section links and reachability.
     * Empty files and malformed JSON produce a book containing a diagnostic error.
     * @param id external book identifier
     * @param content original JSON bytes, also used to calculate the SHA-256 version
     * @return the book model with collected errors and warnings
     */
    Book parse(String id, byte[] content);
}
