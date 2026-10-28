package com.example.adventurebook.entity.book;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Option {

    private String id;
    private String description;
    private long targetSectionId;
    private Consequence consequence;
}
