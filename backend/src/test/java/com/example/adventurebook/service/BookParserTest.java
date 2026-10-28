package com.example.adventurebook.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.adventurebook.entity.book.Book;
import com.example.adventurebook.entity.book.ValidationIssue;
import com.example.adventurebook.service.impl.BookParserServiceImpl;
import com.example.adventurebook.service.impl.BookValidationServiceImpl;
import com.example.adventurebook.utils.BookUtils;

import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

class BookParserTest {

    private final BookParserService parser =
        new BookParserServiceImpl(new ObjectMapper(), new BookValidationServiceImpl());

    @Test
    void acceptsNumericAndStringIdsAndAValidCycle() {
        Book book =
            parse(
                """
                    {"title":"Cycle","author":"Tester","difficulty":"EASY","sections":[
                        {"id":1,"text":"Start","type":"BEGIN","options":[{"description":"Loop","gotoId":"2"}]},
                        {"id":"2","text":"Loop","type":"NODE","options":[{"description":"Finish","gotoId":3},{"description":"Again","gotoId":1}]},
                        {"id":3,"text":"Done","type":"END"}
                    ]}
                    """);

        assertThat(BookUtils.isPlayable(book)).isTrue();
        assertThat(book.getSections()).containsKeys(1L, 2L, 3L);
    }

    @Test
    void detectsDuplicateIdsAfterNormalization() {
        Book book =
            parse(
                """
                    {"title":"Duplicate","author":"Tester","difficulty":"EASY","sections":[
                        {"id":1,"text":"Start","type":"BEGIN","options":[{"description":"Go","gotoId":2}]},
                        {"id":"1","text":"Again","type":"NODE","options":[{"description":"Go","gotoId":2}]},
                        {"id":2,"text":"Done","type":"END"}]}
                    """);

        assertThat(book.getIssues())
            .extracting(ValidationIssue::getCode)
            .contains("DUPLICATE_SECTION_ID");
    }

    @Test
    void validatesUnreachableSectionsAndBrokenReferences() {
        Book book =
            parse(
                """
                    {"title":"Broken","author":"Tester","difficulty":"EASY","sections":[
                        {"id":1,"text":"Start","type":"BEGIN","options":[{"description":"Go","gotoId":2}]},
                        {"id":2,"text":"Done","type":"END"},
                        {"id":9,"text":"Hidden","type":"NODE","options":[{"description":"Missing","gotoId":404}]}]}
                    """);

        assertThat(book.getIssues())
            .extracting(ValidationIssue::getCode)
            .contains("INVALID_TARGET", "UNREACHABLE_SECTION");
        assertThat(BookUtils.isPlayable(book)).isFalse();
    }

    @Test
    void rejectsEmptyAndInvalidJson() {
        assertThat(parser.parse("empty", new byte[0]).getIssues())
            .extracting(ValidationIssue::getCode)
            .contains("EMPTY_FILE");

        assertThat(parser.parse("bad", "{".getBytes(StandardCharsets.UTF_8)).getIssues())
            .extracting(ValidationIssue::getCode)
            .contains("INVALID_JSON");
    }

    @Test
    void rejectsNegativeOrNonNumericConsequence() {
        Book negative =
            parse(
                """
                    {"title":"Bad value","author":"Tester","difficulty":"EASY","sections":[
                        {"id":1,"text":"Start","type":"BEGIN","options":[{"description":"Go","gotoId":2,"consequence":{"type":"LOSE_HEALTH","value":-1,"text":"Ouch"}}]},
                        {"id":2,"text":"Done","type":"END"}]}
                    """);

        assertThat(negative.getIssues())
            .extracting(ValidationIssue::getCode)
            .contains("INVALID_CONSEQUENCE_VALUE");
    }

    @Test
    void rejectsIdsOutsideLongRange() {
        for (String id : new String[] {"9223372036854775808", "\"9223372036854775808\""}) {
            Book book = parse("""
                {"title":"Overflow","author":"Tester","difficulty":"EASY","sections":[
                    {"id":%s,"text":"Start","type":"BEGIN","options":[{"description":"Go","gotoId":2}]},
                    {"id":2,"text":"Done","type":"END"}]}
                """.formatted(id));

            assertThat(BookUtils.isPlayable(book)).isFalse();
            assertThat(book.getIssues()).extracting(ValidationIssue::getCode).contains("INVALID_INTEGER");
        }
    }

    @Test
    void cycleWithoutReachableEndOnlyProducesWarnings() {
        Book book = parse("""
            {"title":"Loop","author":"Tester","difficulty":"EASY","sections":[
                {"id":1,"text":"Start","type":"BEGIN","options":[{"description":"Again","gotoId":1}]},
                {"id":2,"text":"Hidden ending","type":"END"}]}
            """);

        assertThat(BookUtils.isPlayable(book)).isTrue();
        assertThat(book.getIssues()).extracting(ValidationIssue::getCode)
            .containsExactly("UNREACHABLE_SECTION", "NO_REACHABLE_END");
    }

    @Test
    void reachableMissingTargetDoesNotInterruptTraversalOrHideAnEnding() {
        Book book = parse("""
            {"title":"Broken branch","author":"Tester","difficulty":"EASY","sections":[
                {"id":1,"text":"Start","type":"BEGIN","options":[
                    {"description":"Missing","gotoId":404},
                    {"description":"Finish","gotoId":2},
                    {"description":"Another way","gotoId":2}]},
                {"id":2,"text":"Done","type":"END"},
                {"id":9,"text":"Hidden ending","type":"END"}]}
            """);

        assertThat(book.getIssues()).extracting(ValidationIssue::getCode)
            .containsExactly("INVALID_TARGET", "UNREACHABLE_SECTION");
    }

    @Test
    void traversesTheMaximumLengthStoryWithoutRecursion() {
        String sections = IntStream.range(1, 2_000)
            .mapToObj(id -> """
                {"id":%d,"text":"Next","type":"%s","options":[{"description":"Continue","gotoId":%d}]}
                """.formatted(id, id == 1 ? "BEGIN" : "NODE", id + 1))
            .collect(Collectors.joining(","));
        Book book = parse("""
            {"title":"Long story","author":"Tester","difficulty":"EASY","sections":[
                %s,{"id":2000,"text":"Done","type":"END"}]}
            """.formatted(sections));

        assertThat(book.getSections()).hasSize(2_000);
        assertThat(book.getIssues()).isEmpty();
    }

    private Book parse(String json) {
        return parser.parse("test", json.getBytes(StandardCharsets.UTF_8));
    }
}
