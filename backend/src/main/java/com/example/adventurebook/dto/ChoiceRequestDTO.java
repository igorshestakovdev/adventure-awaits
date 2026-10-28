package com.example.adventurebook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChoiceRequestDTO {

    @NotBlank
    private String optionId;
    @PositiveOrZero
    private long expectedRevision;
    @NotNull
    private UUID requestId;
}
