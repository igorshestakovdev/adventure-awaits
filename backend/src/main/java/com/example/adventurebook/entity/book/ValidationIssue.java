package com.example.adventurebook.entity.book;

import com.example.adventurebook.enums.Severity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ValidationIssue {

    private Severity severity;
    private String code;
    private String message;
}
