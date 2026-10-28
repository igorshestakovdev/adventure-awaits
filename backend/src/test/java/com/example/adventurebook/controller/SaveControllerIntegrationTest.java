package com.example.adventurebook.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.adventurebook.dto.GameStateDTO;
import com.example.adventurebook.entity.SaveSummary;
import com.example.adventurebook.service.BookCatalogService;
import com.example.adventurebook.service.GameService;
import com.example.adventurebook.service.SaveService;
import com.example.adventurebook.utils.BookUtils;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:save-controller-test;DB_CLOSE_DELAY=-1")
class SaveControllerIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private BookCatalogService books;

    @Autowired
    private GameService games;

    @Autowired
    private SaveService saves;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void savesListsAndRestoresGameThroughHttp() throws Exception {
        String bookId = books.search(null, null).stream()
            .filter(BookUtils::isPlayable)
            .findFirst().orElseThrow().getId();
        GameStateDTO original = games.start(bookId);
        String name = "checkpoint-" + UUID.randomUUID();

        mvc.perform(post("/api/saves")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"sessionId":"%s","name":"  %s  "}
                    """.formatted(original.getSessionId(), name)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.name").value(name))
            .andExpect(jsonPath("$.bookId").value(original.getBookId()));

        SaveSummary save = saves.list().stream()
            .filter(item -> item.getName().equals(name))
            .findFirst().orElseThrow();

        mvc.perform(get("/api/saves"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].id", hasItem(save.getId().toString())));

        games.stop(original.getSessionId());

        mvc.perform(post("/api/saves/{id}/restore", save.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sessionId", not(original.getSessionId().toString())))
            .andExpect(jsonPath("$.bookId").value(original.getBookId()))
            .andExpect(jsonPath("$.sectionId").value(original.getSectionId()))
            .andExpect(jsonPath("$.health").value(original.getHealth()))
            .andExpect(jsonPath("$.status").value(original.getStatus()));
    }

    @Test
    void rejectsInvalidSaveRequests() throws Exception {
        for (String body : new String[] {
            "{}",
            "{\"sessionId\":\"%s\",\"name\":\" \"}".formatted(UUID.randomUUID()),
            "{\"sessionId\":\"%s\",\"name\":\"%s\"}".formatted(UUID.randomUUID(), "a".repeat(121))
        }) {
            mvc.perform(post("/api/saves")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isBadRequest());
        }
    }

    @Test
    void returnsNotFoundForMissingSave() throws Exception {
        mvc.perform(post("/api/saves/{id}/restore", UUID.randomUUID()))
            .andExpect(status().isNotFound());
    }

    @Test
    void deletesExistingSaveAndReturnsNotFoundAfterwards() throws Exception {
        String bookId = books.search(null, null).stream()
            .filter(BookUtils::isPlayable)
            .findFirst().orElseThrow().getId();
        GameStateDTO original = games.start(bookId);
        String name = "delete-me-" + UUID.randomUUID();

        SaveSummary created = saves.save(original.getSessionId(), name);

        mvc.perform(delete("/api/saves/{id}", created.getId()))
            .andExpect(status().isNoContent());

        mvc.perform(delete("/api/saves/{id}", created.getId()))
            .andExpect(status().isNotFound());

        mvc.perform(post("/api/saves/{id}/restore", created.getId()))
            .andExpect(status().isNotFound());
    }
}
