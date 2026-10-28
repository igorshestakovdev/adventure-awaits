package com.example.adventurebook.controller;

import com.example.adventurebook.dto.BookSummaryDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.io.IOException;
import java.util.List;

import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Books", description = "Browse and import adventure books")
public interface BookController {

    @Operation(summary = "List books", description = "Returns all available books, optionally filtered by search query and difficulty")
    @ApiResponse(responseCode = "200", description = "List of books")
    List<BookSummaryDTO> list(
            @Parameter(description = "Free-text search query") String query,
            @Parameter(description = "Difficulty filter: EASY, MEDIUM, HARD") String difficulty);

    @Operation(summary = "Get book by ID")
    @ApiResponse(responseCode = "200", description = "Book found")
    @ApiResponse(responseCode = "404", description = "Book not found", content = @Content(schema = @Schema(hidden = true)))
    BookSummaryDTO getById(String id);

    @Operation(summary = "Import book", description = "Upload a JSON adventure book file (max 1 MB)")
    @ApiResponse(responseCode = "201", description = "Book imported")
    @ApiResponse(responseCode = "413", description = "File too large", content = @Content(schema = @Schema(hidden = true)))
    @ApiResponse(responseCode = "422", description = "Invalid book structure", content = @Content(schema = @Schema(hidden = true)))
    BookSummaryDTO importBook(MultipartFile file) throws IOException;
}
