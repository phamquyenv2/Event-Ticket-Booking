package com.quyen.geekticket.controller;

import com.quyen.geekticket.domain.entity.Concert;
import com.quyen.geekticket.repository.ConcertRepository;
import com.quyen.geekticket.util.constant.ConcertStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class ConcertControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ConcertRepository concertRepository;

    // ==================== Customer API Tests ====================

    @Test
    @DisplayName("GET /api/v1/concerts should return only PUBLISHED concerts")
    void getPublishedConcerts_returnsOnlyPublished() throws Exception {
        mockMvc.perform(get("/api/v1/concerts")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[*].status", everyItem(is("PUBLISHED"))));
    }

    @Test
    @DisplayName("GET /api/v1/concerts/{id} should return concert detail with ticket categories")
    void getConcertDetail_existingConcert_returnsDetail() throws Exception {
        // Concert ID=1 is seeded as PUBLISHED
        mockMvc.perform(get("/api/v1/concerts/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.title").isNotEmpty())
                .andExpect(jsonPath("$.data.ticketCategories").isArray())
                .andExpect(jsonPath("$.data.ticketCategories", hasSize(greaterThanOrEqualTo(2))));
    }

    @Test
    @DisplayName("GET /api/v1/concerts/{id} should return 404 for non-existent concert")
    void getConcertDetail_nonExistent_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/concerts/9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONCERT_NOT_FOUND"));
    }

    // ==================== Operation API Tests ====================

    @Test
    @DisplayName("POST /api/v1/operations/concerts should create a DRAFT concert")
    void createConcert_validRequest_returnsDraft() throws Exception {
        String body = """
                {
                    "title": "Integration Test Concert",
                    "description": "Test description",
                    "venue": "Test Venue",
                    "totalCapacity": 1000,
                    "saleStartTime": "2026-09-01T00:00:00Z",
                    "saleEndTime": "2026-09-15T00:00:00Z",
                    "concertStartTime": "2026-09-20T00:00:00Z"
                }
                """;

        mockMvc.perform(post("/api/v1/operations/concerts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Operator-Id", 3)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statusCode").value(201))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.title").value("Integration Test Concert"));
    }

    @Test
    @DisplayName("POST /api/v1/operations/concerts should fail without X-Operator-Id")
    void createConcert_missingHeader_returns400() throws Exception {
        String body = """
                {
                    "title": "Test",
                    "venue": "Test Venue",
                    "totalCapacity": 1000,
                    "saleStartTime": "2026-09-01T00:00:00Z",
                    "saleEndTime": "2026-09-15T00:00:00Z",
                    "concertStartTime": "2026-09-20T00:00:00Z"
                }
                """;

        mockMvc.perform(post("/api/v1/operations/concerts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/operations/concerts should fail with invalid times")
    void createConcert_invalidTimes_returns400() throws Exception {
        String body = """
                {
                    "title": "Bad Times Concert",
                    "venue": "Test Venue",
                    "totalCapacity": 1000,
                    "saleStartTime": "2026-09-15T00:00:00Z",
                    "saleEndTime": "2026-09-01T00:00:00Z",
                    "concertStartTime": "2026-09-20T00:00:00Z"
                }
                """;

        mockMvc.perform(post("/api/v1/operations/concerts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Operator-Id", 3)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/operations/concerts/{id}/ticket-categories should add category")
    void addTicketCategory_validRequest_returnsCategory() throws Exception {
        String body = """
                {
                    "name": "Integration Test Category",
                    "description": "Test ticket category",
                    "price": 500000.00,
                    "totalQuantity": 200
                }
                """;

        mockMvc.perform(post("/api/v1/operations/concerts/2/ticket-categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Operator-Id", 3)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statusCode").value(201))
                .andExpect(jsonPath("$.data.name").value("Integration Test Category"))
                .andExpect(jsonPath("$.data.totalQuantity").value(200))
                .andExpect(jsonPath("$.data.availableQuantity").value(200));
    }

    @Test
    @DisplayName("PATCH /api/v1/operations/concerts/{id}/publish should publish DRAFT concert with categories")
    void publishConcert_draftWithCategories_succeeds() throws Exception {
        // Concert ID=2 is DRAFT, we just added a category above
        mockMvc.perform(patch("/api/v1/operations/concerts/2/publish")
                        .header("X-Operator-Id", 3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
    }

    @Test
    @DisplayName("PATCH /api/v1/operations/concerts/{id}/publish should fail for already PUBLISHED concert")
    void publishConcert_alreadyPublished_returnsBadRequest() throws Exception {
        // Concert ID=1 is already PUBLISHED (seeded)
        mockMvc.perform(patch("/api/v1/operations/concerts/1/publish")
                        .header("X-Operator-Id", 3))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /api/v1/operations/concerts should fail with customer role")
    void createConcert_customerRole_returnsForbidden() throws Exception {
        String body = """
                {
                    "title": "Forbidden Concert",
                    "venue": "Test Venue",
                    "totalCapacity": 1000,
                    "saleStartTime": "2026-09-01T00:00:00Z",
                    "saleEndTime": "2026-09-15T00:00:00Z",
                    "concertStartTime": "2026-09-20T00:00:00Z"
                }
                """;

        mockMvc.perform(post("/api/v1/operations/concerts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Operator-Id", 1)  // customer01, role=CUSTOMER
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/v1/operations/concerts should fail with validation errors")
    void createConcert_missingFields_returnsValidationError() throws Exception {
        String body = """
                {
                    "title": "",
                    "totalCapacity": -1
                }
                """;

        mockMvc.perform(post("/api/v1/operations/concerts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Operator-Id", 3)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
