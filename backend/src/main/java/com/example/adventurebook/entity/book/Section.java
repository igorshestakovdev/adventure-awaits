package com.example.adventurebook.entity.book;

import com.example.adventurebook.enums.SectionType;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Section {

    private long id;
    private String text;
    private SectionType type;
    private List<Option> options;
}
