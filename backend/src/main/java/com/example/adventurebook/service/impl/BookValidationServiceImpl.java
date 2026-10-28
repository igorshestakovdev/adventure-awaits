package com.example.adventurebook.service.impl;

import com.example.adventurebook.entity.book.Option;
import com.example.adventurebook.entity.book.Section;
import com.example.adventurebook.entity.book.ValidationIssue;
import com.example.adventurebook.enums.SectionType;
import com.example.adventurebook.enums.Severity;
import com.example.adventurebook.service.BookValidationService;
import com.google.common.collect.ImmutableSet;
import com.google.common.graph.Traverser;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

/**
 * Validates book beginnings, endings, transition options, and story connectivity.
 * Reports structural violations as errors and unreachable sections or endings as warnings.
 */
@Service
public class BookValidationServiceImpl implements BookValidationService {

    /**
     * Checks the structure of all parsed sections and the existence of transition targets.
     * Runs reachability traversal only with exactly one BEGIN so the traversal root is unambiguous.
     */
    @Override
    public void validate(Map<Long, Section> sections, List<ValidationIssue> issues) {

        // Collect structural errors throughout the book, including unreachable sections.
        sections.values().forEach(section -> validateSection(section, sections, issues));

        List<Section> beginnings = sections.values().stream()
            .filter(section -> section.getType() == SectionType.BEGIN)
            .toList();

        if (beginnings.size() != 1) {
            issues.add(new ValidationIssue(Severity.ERROR, "BEGIN_COUNT",
                "Book must contain exactly one BEGIN section; found " + beginnings.size()));
        }

        if (sections.values().stream().noneMatch(section -> section.getType() == SectionType.END)) {
            issues.add(new ValidationIssue(Severity.ERROR, "NO_END", "Book must contain at least one END section"));
        }

        // Zero or multiple BEGIN sections leave the graph without an unambiguous traversal root.
        if (beginnings.size() == 1) {
            validateReachability(sections, beginnings.getFirst().getId(), issues);
        }
    }

    private void validateSection(Section section, Map<Long, Section> sections, List<ValidationIssue> issues) {

        if (section.getType() != SectionType.END && section.getOptions().isEmpty()) {
            issues.add(new ValidationIssue(Severity.ERROR, "NO_OPTIONS",
                "Non-ending section " + section.getId() + " has no options"));
        }

        for (Option option : section.getOptions()) {
            if (!sections.containsKey(option.getTargetSectionId())) {
                issues.add(new ValidationIssue(Severity.ERROR, "INVALID_TARGET",
                    "Section " + section.getId() + " points to missing section " + option.getTargetSectionId()));
            }
        }
    }

    /**
     * Traverses the graph breadth-first from BEGIN, visiting each section at most once.
     * Skips missing targets because structural validation has already reported them as errors.
     * Adds warnings for unreachable sections and the absence of a reachable END.
     * Traversal considers section links only, without simulating player health or game status.
     */
    private void validateReachability(
        Map<Long, Section> sections, long beginningId, List<ValidationIssue> issues) {

        // Missing targets already have ERROR diagnostics; traverse only existing sections.
        Traverser<Long> graph = Traverser.forGraph(id -> sections.get(id).getOptions().stream()
            .map(Option::getTargetSectionId)
            .filter(sections::containsKey)
            .toList());
        Set<Long> reachable = ImmutableSet.copyOf(graph.breadthFirst(beginningId));

        sections.keySet().stream()
            .filter(id -> !reachable.contains(id))
            .sorted()
            .forEach(id -> issues.add(new ValidationIssue(Severity.WARNING, "UNREACHABLE_SECTION",
                "Section " + id + " is unreachable")));

        // A disconnected ending is a warning: the book is still allowed to run.
        if (reachable.stream().noneMatch(id -> sections.get(id).getType() == SectionType.END)) {
            issues.add(new ValidationIssue(Severity.WARNING, "NO_REACHABLE_END",
                "No END section is reachable from BEGIN"));
        }
    }
}
