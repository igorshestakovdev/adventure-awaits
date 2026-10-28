package com.example.adventurebook.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.example.adventurebook.entity.book.Book;
import com.example.adventurebook.entity.book.ValidationIssue;
import com.example.adventurebook.enums.Difficulty;
import com.example.adventurebook.enums.Severity;
import com.example.adventurebook.exception.InvalidBookException;
import com.example.adventurebook.repository.ImportedBookRepository;
import com.example.adventurebook.service.impl.BookCatalogServiceImpl;
import com.example.adventurebook.service.impl.ClasspathBookProvider;
import com.example.adventurebook.service.impl.DatabaseBookProvider;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class BookCatalogServiceTest {

    private final BookParserService parser = mock(BookParserService.class);
    private final ImportedBookRepository repository = mock(ImportedBookRepository.class);
    private final BookCatalogService catalog = new BookCatalogServiceImpl(
        mock(ClasspathBookProvider.class), mock(DatabaseBookProvider.class), repository, parser);

    @Test
    void importsWithOneParseAndUsesTheSameIdInStorageAndCatalog() throws Exception {
        byte[] content = "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Book parsed = new Book("import", "Demo Book", "Tester", Difficulty.EASY,
            "12345678abcdef", Map.of(), 1, List.of());
        when(parser.parse("import", content)).thenReturn(parsed);

        Book imported = catalog.importBook(new MockMultipartFile("file", content));

        assertThat(imported.getId()).isEqualTo("demo-book-12345678");
        assertThat(catalog.require(imported.getId())).isSameAs(imported);
        verify(parser).parse("import", content);
        verifyNoMoreInteractions(parser);
        verify(repository).save(eq(imported.getId()), eq("{}"), any());
    }

    @Test
    void invalidBookIsNeitherStoredNorPublished() {
        byte[] content = "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Book invalid = new Book("import", "Broken", "Tester", Difficulty.EASY,
            "12345678abcdef", Map.of(), 1,
            List.of(new ValidationIssue(Severity.ERROR, "EMPTY_BOOK", "No sections")));
        when(parser.parse("import", content)).thenReturn(invalid);

        assertThatThrownBy(() -> catalog.importBook(new MockMultipartFile("file", content)))
            .isInstanceOf(InvalidBookException.class);
        assertThat(catalog.search(null, null)).isEmpty();
        verifyNoInteractions(repository);
    }
}
