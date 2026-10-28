package com.example.adventurebook.utils;

import com.example.adventurebook.entity.book.Book;
import com.example.adventurebook.enums.Severity;

public final class BookUtils {

    private BookUtils() {
    }

    public static boolean isPlayable(Book book) {
        return book.getIssues().stream().noneMatch(issue -> issue.getSeverity() == Severity.ERROR);
    }
}
