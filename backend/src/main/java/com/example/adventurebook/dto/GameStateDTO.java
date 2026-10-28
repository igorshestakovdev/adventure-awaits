package com.example.adventurebook.dto;

import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameStateDTO {

    private UUID sessionId;
    private String bookId;
    private String bookTitle;
    private long sectionId;
    private String text;
    private List<ChoiceDTO> choices;
    private int health;
    private String status;
    private long revision;
    private ConsequenceResultDTO consequence;
}
