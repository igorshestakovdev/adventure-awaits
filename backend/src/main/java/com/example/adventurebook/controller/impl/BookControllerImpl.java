package com.example.adventurebook.controller.impl;

import com.example.adventurebook.controller.BookController;
import com.example.adventurebook.dto.BookSummaryDTO;
import com.example.adventurebook.enums.Difficulty;
import com.example.adventurebook.service.BookCatalogService;
import com.example.adventurebook.utils.BookMapper;

import java.io.IOException;
import java.util.List;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/books")
@RequiredArgsConstructor
public class BookControllerImpl implements BookController {

    private final BookCatalogService bookCatalogService;

    @Override
    @GetMapping
    public List<BookSummaryDTO> list(@RequestParam(name = "q", required = false) String query,
                                     @RequestParam(required = false) String difficulty) {

        Difficulty parsedDifficulty = difficulty == null ? null : Difficulty.valueOf(difficulty);

        return
            bookCatalogService
                .search(query, parsedDifficulty)
                .stream()
                .map(BookMapper::toSummary)
                .toList();
    }

    @Override
    @GetMapping("/{id}")
    public BookSummaryDTO getById(@PathVariable String id) {

        return BookMapper.toSummary(bookCatalogService.require(id));
    }

    @Override
    @PostMapping(path = "/import", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public BookSummaryDTO importBook(@RequestPart("file") MultipartFile file) throws IOException {

        return BookMapper.toSummary(bookCatalogService.importBook(file));
    }
}
