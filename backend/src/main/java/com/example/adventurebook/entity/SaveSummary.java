package com.example.adventurebook.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SaveSummary {

    private UUID id;
    private String name;
    private String bookId;
    private OffsetDateTime savedAt;
}
