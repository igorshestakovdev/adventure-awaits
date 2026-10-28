package com.example.adventurebook.exception;

import com.example.adventurebook.entity.book.ValidationIssue;

import java.util.List;

import lombok.Getter;

@Getter
public class InvalidBookException extends RuntimeException {

    private final List<ValidationIssue> issues;

    public InvalidBookException(List<ValidationIssue> issues) {

        super("Book validation failed");

        this.issues = List.copyOf(issues);
    }
}
