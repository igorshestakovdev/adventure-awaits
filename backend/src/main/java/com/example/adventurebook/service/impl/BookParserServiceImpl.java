package com.example.adventurebook.service.impl;

import com.example.adventurebook.entity.book.Book;
import com.example.adventurebook.entity.book.Consequence;
import com.example.adventurebook.entity.book.Option;
import com.example.adventurebook.entity.book.Section;
import com.example.adventurebook.entity.book.ValidationIssue;
import com.example.adventurebook.enums.ConsequenceType;
import com.example.adventurebook.enums.Difficulty;
import com.example.adventurebook.enums.SectionType;
import com.example.adventurebook.enums.Severity;
import com.example.adventurebook.service.BookParserService;
import com.example.adventurebook.service.BookValidationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts JSON into a book model and collects field and story structure errors.
 * Content errors are returned as book diagnostics so the catalog can explain why a book is unplayable.
 */
@Service
@RequiredArgsConstructor
public class BookParserServiceImpl implements BookParserService {

    private static final int MAX_SECTIONS = 2_000;
    private static final int MAX_OPTIONS = 50;
    private final ObjectMapper mapper;
    private final BookValidationService validator;

    /**
     * Builds the model and diagnostics in two stages: field parsing, then section graph validation.
     * Calculates the version from the original bytes, so JSON formatting changes also change the version.
     */
    @Override
    public Book parse(String id, byte[] content) {

        if (content.length == 0) {
            return invalid(id, "Empty file", "EMPTY_FILE");
        }

        try {
            JsonNode root = mapper.readTree(content);

            List<ValidationIssue> issues = new ArrayList<>();

            String title = text(root, "title", issues, "Book title");
            String author = text(root, "author", issues, "Author");
            Difficulty difficulty = enumValue(root.get("difficulty"), Difficulty.class, issues, "difficulty");

            // Collect all sections first: forward references require the complete ID map for validation.
            Map<Long, Section> sections = parseSections(root.get("sections"), issues);
            validator.validate(sections, issues);

            // A missing BEGIN is already reported; a sentinel ID lets us return the model with its errors.
            long beginning = sections.values().stream()
                .filter(section -> section.getType() == SectionType.BEGIN)
                .mapToLong(Section::getId)
                .findFirst()
                .orElse(Long.MIN_VALUE);

            return new Book(id, title, author, difficulty, hash(content), sections, beginning, issues);

        } catch (JacksonException exception) {
            return invalid(id, "Invalid JSON: " + exception.getMessage(), "INVALID_JSON");
        }
    }

    /**
     * Checks for a nonempty section array and the section limit, then indexes sections by numeric ID.
     * Skips sections with invalid IDs or types; keeps the first section when an ID is duplicated.
     */
    private Map<Long, Section> parseSections(JsonNode nodes, List<ValidationIssue> issues) {

        Map<Long, Section> sections = new LinkedHashMap<>();

        if (nodes == null || !nodes.isArray() || nodes.isEmpty()) {
            issues.add(error("EMPTY_BOOK", "Book must contain sections"));
            return sections;
        }

        if (nodes.size() > MAX_SECTIONS) {
            issues.add(error("TOO_MANY_SECTIONS", "Book contains more than " + MAX_SECTIONS + " sections"));
            return sections;
        }

        for (int index = 0; index < nodes.size(); index++) {

            Section section = parseSection(nodes.get(index), issues, index);

            if (section == null) {
                continue;
            }
            // The number 1 and the string "1" normalize to the same long ID; do not overwrite the first section.
            if (sections.putIfAbsent(section.getId(), section) != null) {
                issues.add(error("DUPLICATE_SECTION_ID",
                    "Duplicate section id after normalization: " + section.getId()));
            }
        }

        return sections;
    }

    /**
     * Parses section fields and options while collecting all available diagnostics.
     * @return the section, or null if its ID or type cannot be determined
     */
    private Section parseSection(JsonNode node, List<ValidationIssue> issues, int index) {

        Long id = integer(node.get("id"), issues, "sections[" + index + "].id");

        String text = text(node, "text", issues, "Section text");

        SectionType type = enumValue(node.get("type"), SectionType.class, issues, "section type");

        List<Option> options = parseOptions(node.get("options"), issues, index);

        // Keep collecting diagnostics even when this section cannot be added to the book.
        if (id == null || type == null) {
            return null;
        }

        return new Section(id, text, type, options);
    }

    /**
     * Checks the option array and its size limit, then parses targets and optional consequences.
     * Each option ID is its original array index plus one; an invalid target excludes the option.
     */
    private List<Option> parseOptions(

        JsonNode nodes, List<ValidationIssue> issues, int sectionIndex) {

        // Missing options are valid for END; the validator checks that other section types have options.
        if (nodes == null || nodes.isNull()) {
            return List.of();
        }

        if (!nodes.isArray()) {
            issues.add(error("INVALID_OPTIONS", "Options must be an array"));
            return List.of();
        }

        if (nodes.size() > MAX_OPTIONS) {
            issues.add(
                error("TOO_MANY_OPTIONS", "A section contains more than " + MAX_OPTIONS + " options"));
            return List.of();
        }

        List<Option> options = new ArrayList<>();

        for (int optionIndex = 0; optionIndex < nodes.size(); optionIndex++) {

            JsonNode node = nodes.get(optionIndex);

            String description = text(node, "description", issues, "Option description");

            Long target =
                integer(
                    node.get("gotoId"),
                    issues,
                    "sections[" + sectionIndex + "].options[" + optionIndex + "].gotoId");

            Consequence consequence = parseConsequence(node.get("consequence"), issues);

            // Use the original array position so skipping an invalid target does not renumber other options.
            if (target != null) {
                options.add(new Option(String.valueOf(optionIndex + 1), description, target, consequence));
            }
        }

        return options;
    }

    /**
     * Parses an optional consequence and checks its value against the range from 0 to Integer.MAX_VALUE.
     * @return the consequence, or null if absent or its type or value is invalid; errors remain in issues
     */
    private Consequence parseConsequence(JsonNode node, List<ValidationIssue> issues) {

        if (node == null || node.isNull()) {
            return null;
        }

        ConsequenceType type =
            enumValue(node.get("type"), ConsequenceType.class, issues, "consequence type");
        Long parsed = integer(node.get("value"), issues, "consequence value");
        String text = text(node, "text", issues, "Consequence text");

        // Validate as long before narrowing to int to avoid overflow.
        if (parsed != null && (parsed < 0 || parsed > Integer.MAX_VALUE)) {
            issues.add(
                error(
                    "INVALID_CONSEQUENCE_VALUE",
                    "Consequence value must be between 0 and " + Integer.MAX_VALUE));
            parsed = null;
        }

        if (type == null || parsed == null) {
            return null;
        }

        return new Consequence(type, parsed.intValue(), text);
    }

    /**
     * Reads a required nonblank string and trims leading and trailing whitespace.
     * @return trimmed text, or an empty string with an error appended to issues
     */
    private static String text(

        JsonNode parent, String field, List<ValidationIssue> issues, String label) {

        JsonNode node = parent == null ? null : parent.get(field);

        if (node == null || !node.isTextual() || node.asText().isBlank()) {
            issues.add(error("REQUIRED_TEXT", label + " is required"));
            return "";
        }

        return node.asText().trim();
    }

    /**
     * Accepts an integral JSON number or a digit string with an optional minus sign.
     * Rejects fractions and values outside the long range without rounding or truncation.
     * @return the long value, or null with a diagnostic identifying the supplied path
     */
    private static Long integer(JsonNode node, List<ValidationIssue> issues, String path) {

        if (node != null && (node.isIntegralNumber() || (node.isTextual() && node.asText().matches("-?\\d+")))) {

            try {
                return Long.parseLong(node.asText());
            } catch (NumberFormatException ignored) {
                // Both numeric IDs and integer strings must fit in the long range.
            }
        }

        issues.add(
            error(
                "INVALID_INTEGER", path + " must be an integer or integer string within 64-bit range"));

        return null;
    }

    /**
     * Reads a string as an exact, case-sensitive enum constant name.
     * @return the constant, or null with a diagnostic for a missing or unsupported value
     */
    private static <E extends Enum<E>> E enumValue(

        JsonNode node, Class<E> type, List<ValidationIssue> issues, String label) {

        if (node != null && node.isTextual()) {
            try {
                return Enum.valueOf(type, node.asText());
            } catch (IllegalArgumentException ignored) {
                // Report an unknown constant as a diagnostic, just like a missing field.
            }
        }

        issues.add(error("UNSUPPORTED_VALUE", "Unsupported or missing " + label));

        return null;
    }

    /**
     * Creates a placeholder book with one error for an empty file or malformed JSON.
     */
    private Book invalid(String id, String message, String code) {
        return new Book(
            id,
            id,
            "Unknown",
            Difficulty.MEDIUM,
            hash(new byte[0]),
            Map.of(),
            Long.MIN_VALUE,
            List.of(error(code, message)));
    }

    /**
     * Creates an ERROR diagnostic that prevents the book from being played.
     */
    private static ValidationIssue error(String code, String message) {
        return new ValidationIssue(Severity.ERROR, code, message);
    }

    /**
     * Calculates the hexadecimal SHA-256 hash used as the book content version.
     */
    private static String hash(byte[] content) {

        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }

    }
}
