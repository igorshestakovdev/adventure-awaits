package com.example.adventurebook.utils;

import com.example.adventurebook.dto.BookSummaryDTO;
import com.example.adventurebook.entity.book.Book;

public final class BookMapper {

    private BookMapper() {}

    public static BookSummaryDTO toSummary(Book book) {
        return new BookSummaryDTO(
            book.getId(),
            book.getTitle(),
            book.getAuthor(),
            book.getDifficulty(),
            BookUtils.isPlayable(book),
            book.getIssues());
    }
}
