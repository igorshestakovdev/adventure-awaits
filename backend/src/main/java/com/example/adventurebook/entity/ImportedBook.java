package com.example.adventurebook.entity;

import java.time.OffsetDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImportedBook {

    private String id;
    private String content;
    private OffsetDateTime importedAt;
}
