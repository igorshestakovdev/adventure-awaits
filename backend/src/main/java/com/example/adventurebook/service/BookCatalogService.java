package com.example.adventurebook.service;

import com.example.adventurebook.entity.book.Book;
import com.example.adventurebook.enums.Difficulty;

import java.io.IOException;
import java.util.List;

import org.springframework.web.multipart.MultipartFile;

/**
 * Catalog of bundled and imported books, supporting search, lookup, and import.
 */
public interface BookCatalogService {

    /**
     * Searches titles and authors by a case-insensitive substring and sorts results by title.
     * @param query search text; null or an empty string disables the text filter
     * @param difficulty difficulty filter; null disables this filter
     * @return matching books, including books with validation errors
     */
    List<Book> search(String query, Difficulty difficulty);

    /**
     * Returns a book from the catalog.
     * @param id book identifier
     * @return the matching book
     * @throws java.util.NoSuchElementException if the book is missing
     */
    Book require(String id);

    /**
     * Checks the file size, parses the book, and persists its JSON before updating the catalog.
     * Warnings allow import; validation errors prevent it.
     * @param file uploaded JSON file containing at most 1,000,000 bytes
     * @return the imported book with its persistent identifier
     * @throws IOException if the file content cannot be read
     * @throws IllegalArgumentException if the file is empty or exceeds the size limit
     * @throws com.example.adventurebook.exception.InvalidBookException if the book has validation errors
     */
    Book importBook(MultipartFile file) throws IOException;
}
