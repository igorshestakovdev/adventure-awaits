package com.example.adventurebook.service.impl;

import com.example.adventurebook.entity.book.Book;
import com.example.adventurebook.enums.Difficulty;
import com.example.adventurebook.enums.Severity;
import com.example.adventurebook.exception.InvalidBookException;
import com.example.adventurebook.repository.ImportedBookRepository;
import com.example.adventurebook.service.BookCatalogService;
import com.example.adventurebook.service.BookParserService;
import com.example.adventurebook.service.BookProvider;
import com.example.adventurebook.utils.BookUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Combines books from configured sources into a catalog and persists imported books.
 * Readers see a consistent catalog snapshot; imports publish a new snapshot after the database write.
 */
@Service
@RequiredArgsConstructor
public class BookCatalogServiceImpl implements BookCatalogService {

    private static final Logger log = LoggerFactory.getLogger(BookCatalogServiceImpl.class);

    private static final long MAX_IMPORT_BYTES = 1_000_000;

    private final ClasspathBookProvider classpathProvider;

    private final DatabaseBookProvider databaseProvider;

    private final ImportedBookRepository importedRepository;

    private final BookParserService parser;

    // Readers use a complete snapshot while an import publishes a new catalog.
    private volatile Map<String, Book> catalog = Map.of();

    @PostConstruct
    /**
     * Loads bundled books followed by imported books and publishes an immutable catalog snapshot.
     * For duplicate IDs, an imported book replaces the bundled book.
     */
    void load() {

        Map<String, Book> loaded = new LinkedHashMap<>();

        loadBooks(classpathProvider, loaded);
        loadBooks(databaseProvider, loaded);

        catalog = Map.copyOf(loaded);
    }

    /**
     * Adds books from a source to the catalog being assembled and logs their validation results.
     */
    private void loadBooks(BookProvider provider, Map<String, Book> loaded) {

        for (Book book : provider.loadBooks().values()) {

            loaded.put(book.getId(), book);
            logResult(book, provider.name());
        }
    }

    /**
     * Filters the current catalog snapshot by difficulty and normalized search text.
     */
    @Override
    public List<Book> search(String query, Difficulty difficulty) {

        String term = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);

        return catalog.values().stream()
            .filter(book -> difficulty == null || book.getDifficulty() == difficulty)
            .filter(book -> matchesSearch(book, term))
            .sorted(Comparator.comparing(Book::getTitle))
            .toList();
    }

    /**
     * Checks whether the already normalized search text occurs in the book title or author.
     */
    private boolean matchesSearch(Book book, String term) {

        return term.isEmpty()
            || book.getTitle().toLowerCase(Locale.ROOT).contains(term)
            || book.getAuthor().toLowerCase(Locale.ROOT).contains(term);
    }

    /**
     * Returns a book by ID or reports that it is missing from the catalog.
     */
    @Override
    public Book require(String id) {

        Book book = catalog.get(id);

        if (book == null) {
            throw new NoSuchElementException("Book not found: " + id);
        }

        return book;
    }

    /**
     * Checks the file, parses the book, persists its JSON, and publishes the updated catalog in sequence.
     * Synchronization prevents lost catalog updates during concurrent imports.
     */
    @Override
    public synchronized Book importBook(MultipartFile file) throws IOException {

        if (file.isEmpty()) {
            throw new IllegalArgumentException("The uploaded file is empty");
        }

        if (file.getSize() > MAX_IMPORT_BYTES) {
            throw new IllegalArgumentException("Book exceeds the 1 MB limit");
        }

        byte[] bytes = file.getBytes();
        Book book = parser.parse("import", bytes);

        if (!BookUtils.isPlayable(book)) {
            throw new InvalidBookException(book.getIssues());
        }

        String id = importId(book);
        book.setId(id);
        importedRepository.save(id, new String(bytes, StandardCharsets.UTF_8), OffsetDateTime.now());

        Map<String, Book> updated = new LinkedHashMap<>(catalog);
        updated.put(id, book);
        catalog = Map.copyOf(updated);

        return book;
    }

    /**
     * Builds an ID from an ASCII title slug and the first eight characters of the content hash.
     * Uses the prefix book when the slug is empty.
     */
    private String importId(Book book) {

        String slug =
            book.getTitle().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");

        if (slug.isBlank()) {
            slug = "book";
        }

        // The content hash keeps repeated imports of the same book on the same ID.
        return slug + "-" + book.getVersion().substring(0, 8);
    }

    /**
     * Logs whether the loaded book contains validation errors or is playable.
     */
    private void logResult(Book book, String source) {

        long errors =
            book.getIssues().stream().filter(i -> i.getSeverity() == Severity.ERROR).count();

        if (errors > 0) {
            log.warn("Loaded {} book {} with {} validation error(s)", source, book.getId(), errors);
        } else {
            log.info("Loaded playable {} book {}", source, book.getId());
        }
    }
}
