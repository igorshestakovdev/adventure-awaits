package com.example.adventurebook.controller;

import com.example.adventurebook.dto.GameStateDTO;
import com.example.adventurebook.dto.SaveRequestDTO;
import com.example.adventurebook.entity.SaveSummary;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.UUID;

@Tag(name = "Saves", description = "Manage saved game states")
public interface SaveController {

    @Operation(summary = "List all saves")
    @ApiResponse(responseCode = "200", description = "List of saves")
    List<SaveSummary> list();

    @Operation(summary = "Create a save")
    @ApiResponse(responseCode = "201", description = "Save created")
    @ApiResponse(responseCode = "404", description = "Session not found", content = @Content(schema = @Schema(hidden = true)))
    SaveSummary save(SaveRequestDTO request);

    @Operation(summary = "Restore a save", description = "Starts a new session from a previously saved state")
    @ApiResponse(responseCode = "200", description = "Restored game state")
    @ApiResponse(responseCode = "404", description = "Save not found", content = @Content(schema = @Schema(hidden = true)))
    @ApiResponse(responseCode = "409", description = "Book version conflict", content = @Content(schema = @Schema(hidden = true)))
    GameStateDTO restore(@Parameter(description = "Save ID") UUID id);

    @Operation(summary = "Delete a save")
    @ApiResponse(responseCode = "204", description = "Save deleted")
    @ApiResponse(responseCode = "404", description = "Save not found", content = @Content(schema = @Schema(hidden = true)))
    void delete(@Parameter(description = "Save ID") UUID id);
}
