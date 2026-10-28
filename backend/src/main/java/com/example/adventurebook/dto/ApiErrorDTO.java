package com.example.adventurebook.dto;

import java.time.OffsetDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiErrorDTO {

    private String code;
    private String message;
    private int status;
    private String path;
    private OffsetDateTime timestamp;
    private List<String> details;
}
