package com.example.adventurebook.entity.book;

import com.example.adventurebook.enums.Difficulty;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Book {

    private String id;
    private String title;
    private String author;
    private Difficulty difficulty;
    private String version;
    private Map<Long, Section> sections;
    private long beginningId;
    private List<ValidationIssue> issues;
}
