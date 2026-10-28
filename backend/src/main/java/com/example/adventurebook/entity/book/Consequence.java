package com.example.adventurebook.entity.book;

import com.example.adventurebook.enums.ConsequenceType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Consequence {

    private ConsequenceType type;
    private int value;
    private String text;
}
