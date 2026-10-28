package com.example.adventurebook.controller;

import com.example.adventurebook.dto.ChoiceRequestDTO;
import com.example.adventurebook.dto.GameStateDTO;
import com.example.adventurebook.dto.StartGameRequestDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.UUID;

@Tag(name = "Games", description = "Start and play game sessions")
public interface GameController {

    @Operation(summary = "Start a new game session")
    @ApiResponse(responseCode = "201", description = "Session created")
    @ApiResponse(responseCode = "404", description = "Book not found", content = @Content(schema = @Schema(hidden = true)))
    GameStateDTO start(StartGameRequestDTO request);

    @Operation(summary = "Get current game state")
    @ApiResponse(responseCode = "200", description = "Current state")
    @ApiResponse(responseCode = "404", description = "Session not found", content = @Content(schema = @Schema(hidden = true)))
    GameStateDTO getById(@Parameter(description = "Session ID") UUID id);

    @Operation(summary = "Make a choice", description = "Choose an option for the current section; idempotent within the same requestId")
    @ApiResponse(responseCode = "200", description = "New game state")
    @ApiResponse(responseCode = "404", description = "Session or option not found", content = @Content(schema = @Schema(hidden = true)))
    @ApiResponse(responseCode = "409", description = "Revision conflict or game finished", content = @Content(schema = @Schema(hidden = true)))
    GameStateDTO choose(@Parameter(description = "Session ID") UUID id, ChoiceRequestDTO request);

    @Operation(summary = "Stop (abandon) a game session")
    @ApiResponse(responseCode = "204", description = "Session removed")
    @ApiResponse(responseCode = "404", description = "Session not found", content = @Content(schema = @Schema(hidden = true)))
    void stop(@Parameter(description = "Session ID") UUID id);
}
