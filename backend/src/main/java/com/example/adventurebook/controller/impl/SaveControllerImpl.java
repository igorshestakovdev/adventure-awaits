package com.example.adventurebook.controller.impl;

import com.example.adventurebook.controller.SaveController;
import com.example.adventurebook.dto.GameStateDTO;
import com.example.adventurebook.dto.SaveRequestDTO;
import com.example.adventurebook.entity.SaveSummary;
import com.example.adventurebook.service.SaveService;

import jakarta.validation.Valid;

import java.util.List;
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
@RequestMapping("/api/saves")
@RequiredArgsConstructor
public class SaveControllerImpl implements SaveController {

    private final SaveService saveService;

    @Override
    @GetMapping
    public List<SaveSummary> list() {
        return saveService.list();
    }

    @Override
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SaveSummary save(@Valid @RequestBody SaveRequestDTO request) {
        return saveService.save(request.getSessionId(), request.getName());
    }

    @Override
    @PostMapping("/{id}/restore")
    public GameStateDTO restore(@PathVariable UUID id) {
        return saveService.restore(id);
    }

    @Override
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        saveService.delete(id);
    }
}
