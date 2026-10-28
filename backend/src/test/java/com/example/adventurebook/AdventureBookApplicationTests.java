package com.example.adventurebook;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    properties = "spring.datasource.url=jdbc:h2:mem:adventure-book-test;DB_CLOSE_DELAY=-1")
class AdventureBookApplicationTests {

    @Test
    void contextLoads() {
    }
}
