package com.quyen.geekticket.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quyen.geekticket.domain.entity.Concert;
import com.quyen.geekticket.domain.entity.IdempotencyRecord;
import com.quyen.geekticket.domain.entity.TicketCategory;
import com.quyen.geekticket.domain.request.BookingItemRequest;
import com.quyen.geekticket.domain.request.CreateBookingRequest;
import com.quyen.geekticket.domain.response.booking.BookingResponse;
import com.quyen.geekticket.repository.BookingRepository;
import com.quyen.geekticket.repository.ConcertRepository;
import com.quyen.geekticket.repository.IdempotencyRecordRepository;
import com.quyen.geekticket.repository.TicketCategoryRepository;
import com.quyen.geekticket.service.BookingService;
import com.quyen.geekticket.util.constant.ConcertStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class BookingIdempotencyIntegrationTest {

    private static final int CONCURRENT_ATTEMPTS = 20;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private BookingService bookingService;

    @Autowired
    private ConcertRepository concertRepository;

    @Autowired
    private TicketCategoryRepository ticketCategoryRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Test
    void sequentialReplay_sameNormalizedPayload_returnsOriginalBookingAndDecrementsOnce() throws Exception {
        Concert concert = createOnSaleConcert();
        TicketCategory first = createCategory(concert, "Replay A", 10, 4);
        TicketCategory second = createCategory(concert, "Replay B", 10, 4);
        String key = UUID.randomUUID().toString();

        MvcResult original = performBooking(1L, key, request(concert.getId(),
                item(first.getId(), 1) + "," + item(second.getId(), 1)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode originalData = responseData(original);

        MvcResult replay = performBooking(1L, key, """
                {
                  "items": [
                    {"quantity": 1, "ticketCategoryId": %d},
                    {"ticketCategoryId": %d, "quantity": 1}
                  ],
                  "concertId": %d
                }
                """.formatted(second.getId(), first.getId(), concert.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(originalData.get("id").asLong()))
                .andExpect(jsonPath("$.data.bookingCode").value(originalData.get("bookingCode").asText()))
                .andReturn();

        assertThat(responseData(replay)).isEqualTo(originalData);
        assertThat(available(first)).isEqualTo(9);
        assertThat(available(second)).isEqualTo(9);
        assertThat(bookingRepository.countByConcertId(concert.getId())).isEqualTo(1);
        assertThat(idempotencyRecordRepository.countByUserIdAndIdempotencyKey(1L, key)).isEqualTo(1);

        IdempotencyRecord record = idempotencyRecordRepository
                .findByUserIdAndIdempotencyKey(1L, key).orElseThrow();
        assertThat(record.getRequestHash()).hasSize(64);
        assertThat(record.getBooking()).isNotNull();
        assertThat(record.getCreatedAt()).isNotNull();
        assertThat(record.getUpdatedAt()).isNotNull();
    }

    @Test
    void sameKey_differentPayload_returnsConflictWithoutSecondDecrement() throws Exception {
        Concert concert = createOnSaleConcert();
        TicketCategory category = createCategory(concert, "Conflict", 5, 4);
        String key = UUID.randomUUID().toString();

        performBooking(1L, key, request(concert.getId(), item(category.getId(), 1)))
                .andExpect(status().isCreated());
        performBooking(1L, key, request(concert.getId(), item(category.getId(), 2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_CONFLICT"));

        assertThat(available(category)).isEqualTo(4);
        assertThat(bookingRepository.countByConcertId(concert.getId())).isEqualTo(1);
        assertThat(idempotencyRecordRepository.countByUserIdAndIdempotencyKey(1L, key)).isEqualTo(1);
    }

    @Test
    void sameKey_differentUsers_createsIndependentBookings() throws Exception {
        Concert concert = createOnSaleConcert();
        TicketCategory category = createCategory(concert, "User scoped", 5, 4);
        String key = UUID.randomUUID().toString();
        String body = request(concert.getId(), item(category.getId(), 1));

        String firstCode = responseData(performBooking(1L, key, body)
                .andExpect(status().isCreated()).andReturn()).get("bookingCode").asText();
        String secondCode = responseData(performBooking(2L, key, body)
                .andExpect(status().isCreated()).andReturn()).get("bookingCode").asText();

        assertThat(firstCode).isNotEqualTo(secondCode);
        assertThat(available(category)).isEqualTo(3);
        assertThat(bookingRepository.countByConcertId(concert.getId())).isEqualTo(2);
        assertThat(idempotencyRecordRepository.countByUserIdAndIdempotencyKey(1L, key)).isEqualTo(1);
        assertThat(idempotencyRecordRepository.countByUserIdAndIdempotencyKey(2L, key)).isEqualTo(1);
    }

    @Test
    void missingOrBlankKey_returnsIdempotencyKeyRequired() throws Exception {
        Concert concert = createOnSaleConcert();
        TicketCategory category = createCategory(concert, "Required key", 5, 4);
        String body = request(concert.getId(), item(category.getId(), 1));

        mockMvc.perform(post("/api/v1/bookings")
                        .header("X-User-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));

        performBooking(1L, "   ", body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));

        assertThat(available(category)).isEqualTo(5);
        assertThat(bookingRepository.countByConcertId(concert.getId())).isZero();
    }

    @Test
    void malformedKey_returnsValidationError() throws Exception {
        Concert concert = createOnSaleConcert();
        TicketCategory category = createCategory(concert, "Malformed key", 5, 4);

        performBooking(1L, "not-a-uuid", request(concert.getId(), item(category.getId(), 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        assertThat(available(category)).isEqualTo(5);
        assertThat(bookingRepository.countByConcertId(concert.getId())).isZero();
    }

    @Test
    void twentyConcurrentRequests_sameKey_createExactlyOneBooking() throws Exception {
        Concert concert = createOnSaleConcert();
        TicketCategory category = createCategory(concert, "Concurrent replay", 10, 4);
        String key = UUID.randomUUID().toString();
        CreateBookingRequest request = CreateBookingRequest.builder()
                .concertId(concert.getId())
                .items(List.of(BookingItemRequest.builder()
                        .ticketCategoryId(category.getId())
                        .quantity(1)
                        .build()))
                .build();

        CountDownLatch ready = new CountDownLatch(CONCURRENT_ATTEMPTS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(CONCURRENT_ATTEMPTS);
        Set<Long> bookingIds = ConcurrentHashMap.newKeySet();
        Set<String> bookingCodes = ConcurrentHashMap.newKeySet();
        Queue<Throwable> failures = new ConcurrentLinkedQueue<>();
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_ATTEMPTS);

        try {
            for (int attempt = 0; attempt < CONCURRENT_ATTEMPTS; attempt++) {
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        BookingResponse response = bookingService.createBooking(1L, key, request);
                        bookingIds.add(response.getId());
                        bookingCodes.add(response.getBookingCode());
                    } catch (Throwable throwable) {
                        failures.add(throwable);
                    } finally {
                        finished.countDown();
                    }
                });
            }

            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(finished.await(90, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(failures).isEmpty();
        assertThat(bookingIds).hasSize(1);
        assertThat(bookingCodes).hasSize(1);
        assertThat(available(category)).isEqualTo(9);
        assertThat(bookingRepository.countByConcertId(concert.getId())).isEqualTo(1);
        assertThat(idempotencyRecordRepository.countByUserIdAndIdempotencyKey(1L, key)).isEqualTo(1);
    }

    @Test
    void failedFirstTransaction_doesNotPoisonKeyAndValidRetryCanClaimIt() throws Exception {
        Concert concert = createOnSaleConcert();
        TicketCategory category = createCategory(concert, "Retry after rollback", 1, 2);
        String key = UUID.randomUUID().toString();

        performBooking(1L, key, request(concert.getId(), item(category.getId(), 2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_TICKET_QUANTITY"));

        assertThat(idempotencyRecordRepository.countByUserIdAndIdempotencyKey(1L, key)).isZero();
        assertThat(bookingRepository.countByConcertId(concert.getId())).isZero();
        assertThat(available(category)).isEqualTo(1);

        performBooking(1L, key, request(concert.getId(), item(category.getId(), 1)))
                .andExpect(status().isCreated());

        assertThat(idempotencyRecordRepository.countByUserIdAndIdempotencyKey(1L, key)).isEqualTo(1);
        assertThat(bookingRepository.countByConcertId(concert.getId())).isEqualTo(1);
        assertThat(available(category)).isZero();
    }

    @Test
    void openApi_documentsRequiredIdempotencyHeader() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"name\":\"Idempotency-Key\"")))
                .andExpect(content().string(containsString("\"required\":true")));
    }

    private org.springframework.test.web.servlet.ResultActions performBooking(
            long userId, String key, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/bookings")
                .header("X-User-Id", userId)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private JsonNode responseData(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    private Concert createOnSaleConcert() {
        Instant now = Instant.now();
        return concertRepository.save(Concert.builder()
                .title("Idempotency test concert " + System.nanoTime())
                .venue("Test venue")
                .totalCapacity(100)
                .status(ConcertStatus.PUBLISHED)
                .saleStartTime(now.minus(1, ChronoUnit.HOURS))
                .saleEndTime(now.plus(1, ChronoUnit.HOURS))
                .concertStartTime(now.plus(1, ChronoUnit.DAYS))
                .build());
    }

    private TicketCategory createCategory(Concert concert, String name, int available, int maxPerBooking) {
        return ticketCategoryRepository.save(TicketCategory.builder()
                .concert(concert)
                .name(name)
                .price(new BigDecimal("100.00"))
                .totalQuantity(available)
                .availableQuantity(available)
                .maxQuantityPerBooking(maxPerBooking)
                .build());
    }

    private int available(TicketCategory category) {
        return ticketCategoryRepository.findById(category.getId()).orElseThrow().getAvailableQuantity();
    }

    private String request(long concertId, String items) {
        return "{\"concertId\":" + concertId + ",\"items\":[" + items + "]}";
    }

    private String item(long categoryId, int quantity) {
        return "{\"ticketCategoryId\":" + categoryId + ",\"quantity\":" + quantity + "}";
    }
}
