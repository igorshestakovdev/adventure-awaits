package com.example.adventurebook.dto;

import com.example.adventurebook.entity.book.ValidationIssue;
import com.example.adventurebook.enums.Difficulty;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookSummaryDTO {

    private String id;
    private String title;
    private String author;
    private Difficulty difficulty;
    private boolean playable;
    private List<ValidationIssue> issues;
}
