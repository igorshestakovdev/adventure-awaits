package com.example.adventurebook.config;

import com.example.adventurebook.entity.GameSession;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.UUID;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration namespace for Adventure Book application components.
 */
@Configuration
public class BookConfiguration {

    @Bean
    public Cache<UUID, GameSession> gameSessions() {
        return Caffeine.newBuilder()
            .maximumSize(1_000)
            .expireAfterAccess(Duration.ofHours(12))
            .build();
    }
}
