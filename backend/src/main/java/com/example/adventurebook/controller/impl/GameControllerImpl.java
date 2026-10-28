package com.example.adventurebook.controller.impl;

import com.example.adventurebook.controller.GameController;
import com.example.adventurebook.dto.ChoiceRequestDTO;
import com.example.adventurebook.dto.GameStateDTO;
import com.example.adventurebook.dto.StartGameRequestDTO;
import com.example.adventurebook.service.GameService;

import jakarta.validation.Valid;

import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
public class GameControllerImpl implements GameController {

    private final GameService gameService;

    @Override
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GameStateDTO start(@Valid @RequestBody StartGameRequestDTO request) {

        return gameService.start(request.getBookId());
    }

    @Override
    @GetMapping("/{id}")
    public GameStateDTO getById(@PathVariable UUID id) {

        return gameService.get(id);
    }

    @Override
    @PostMapping("/{id}/choices")
    public GameStateDTO choose(@PathVariable UUID id, @Valid @RequestBody ChoiceRequestDTO request) {

        return
            gameService.choose(id, request.getOptionId(), request.getExpectedRevision(), request.getRequestId());
    }

    @Override
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void stop(@PathVariable UUID id) {

        gameService.stop(id);
    }
}
