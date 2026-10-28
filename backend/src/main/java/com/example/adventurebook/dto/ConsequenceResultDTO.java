package com.example.adventurebook.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConsequenceResultDTO {

    private String text;
    private int healthDelta;
}
