package com.example.adventurebook.service;

import com.example.adventurebook.entity.book.Section;
import com.example.adventurebook.entity.book.ValidationIssue;

import java.util.List;
import java.util.Map;

/**
 * Validates links and section reachability in an already parsed book.
 */
public interface BookValidationService {

    /**
     * Adds diagnostics for the BEGIN count, END presence, options, and transition targets.
     * Checks reachability when there is exactly one BEGIN; traversal does not account for player health.
     * @param sections parsed sections indexed by ID
     * @param issues mutable list to which errors and warnings are appended
     */
    void validate(Map<Long, Section> sections, List<ValidationIssue> issues);
}
