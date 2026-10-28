package com.example.adventurebook.repository;

import com.example.adventurebook.entity.GameSave;
import com.example.adventurebook.entity.SaveSummary;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SaveRepository {

    void save(GameSave save);

    List<SaveSummary> findAllSummaries();

    Optional<GameSave> findById(UUID id);

    boolean deleteById(UUID id);
}
