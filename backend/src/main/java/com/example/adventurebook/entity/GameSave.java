package com.example.adventurebook.entity;

import com.example.adventurebook.enums.GameStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameSave {

    private UUID id;
    private String name;
    private String bookId;
    private String bookVersion;
    private long sectionId;
    private int health;
    private GameStatus status;
    private long revision;
    private OffsetDateTime savedAt;
}
