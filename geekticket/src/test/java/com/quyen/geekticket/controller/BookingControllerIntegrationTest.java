package com.quyen.geekticket.controller;

import com.quyen.geekticket.domain.entity.Concert;
import com.quyen.geekticket.domain.entity.TicketCategory;
import com.quyen.geekticket.repository.BookingItemRepository;
import com.quyen.geekticket.repository.BookingRepository;
import com.quyen.geekticket.repository.BookingStatusHistoryRepository;
import com.quyen.geekticket.repository.ConcertRepository;
import com.quyen.geekticket.repository.TicketCategoryRepository;
import com.quyen.geekticket.util.constant.ConcertStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class BookingControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ConcertRepository concertRepository;

    @Autowired
    private TicketCategoryRepository ticketCategoryRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingItemRepository bookingItemRepository;

    @Autowired
    private BookingStatusHistoryRepository bookingStatusHistoryRepository;

    @Test
    void createBooking_singleItem_persistsAggregateAndDecrementsInventory() throws Exception {
        Concert concert = createOnSaleConcert();
        TicketCategory category = createCategory(concert, "Single", "100.00", 10, 4);

        mockMvc.perform(post("/api/v1/bookings")
                        .header("X-User-Id", 1)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(concert.getId(), item(category.getId(), 2))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statusCode").value(201))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.bookingCode", startsWith("BK-")))
                .andExpect(jsonPath("$.data.status").value("RESERVED"))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].ticketCategoryId").value(category.getId()))
                .andExpect(jsonPath("$.data.items[0].unitPrice").value(100.00))
                .andExpect(jsonPath("$.data.items[0].subtotal").value(200.00))
                .andExpect(jsonPath("$.data.subtotal").value(200.00))
                .andExpect(jsonPath("$.data.discountAmount").value(0))
                .andExpect(jsonPath("$.data.totalAmount").value(200.00))
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty());

        assertThat(available(category)).isEqualTo(8);
        assertThat(bookingRepository.countByConcertId(concert.getId())).isEqualTo(1);
        assertThat(bookingItemRepository.countByBookingConcertId(concert.getId())).isEqualTo(1);
        assertThat(bookingStatusHistoryRepository.countByBookingConcertId(concert.getId())).isEqualTo(1);
    }

    @Test
    void createBooking_multipleItems_calculatesTotalsAndDecrementsEveryCategory() throws Exception {
        Concert concert = createOnSaleConcert();
        TicketCategory first = createCategory(concert, "Multi A", "100.00", 10, 4);
        TicketCategory second = createCategory(concert, "Multi B", "250.00", 10, 5);

        mockMvc.perform(post("/api/v1/bookings")
                        .header("X-User-Id", 1)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(concert.getId(),
                                item(first.getId(), 2) + "," + item(second.getId(), 3))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.subtotal").value(950.00))
                .andExpect(jsonPath("$.data.discountAmount").value(0))
                .andExpect(jsonPath("$.data.totalAmount").value(950.00));

        assertThat(available(first)).isEqualTo(8);
        assertThat(available(second)).isEqualTo(7);
        assertThat(bookingRepository.countByConcertId(concert.getId())).isEqualTo(1);
        assertThat(bookingItemRepository.countByBookingConcertId(concert.getId())).isEqualTo(2);
    }

    @Test
    void createBooking_categoryFromAnotherConcert_isRejectedBeforePersistence() throws Exception {
        Concert selected = createOnSaleConcert();
        Concert other = createOnSaleConcert();
        TicketCategory otherCategory = createCategory(other, "Other", "100.00", 10, 4);

        performBooking(selected.getId(), item(otherCategory.getId(), 1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TICKET_CATEGORY_NOT_FOUND"));

        assertThat(available(otherCategory)).isEqualTo(10);
        assertThat(bookingRepository.countByConcertId(selected.getId())).isZero();
    }

    @Test
    void createBooking_maxQuantityExceeded_isRejectedBeforePersistence() throws Exception {
        Concert concert = createOnSaleConcert();
        TicketCategory category = createCategory(concert, "Limited", "100.00", 10, 4);

        performBooking(concert.getId(), item(category.getId(), 5))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BOOKING_LIMIT_EXCEEDED"));

        assertThat(available(category)).isEqualTo(10);
        assertThat(bookingRepository.countByConcertId(concert.getId())).isZero();
    }

    @Test
    void createBooking_insufficientQuantity_returnsConflictWithoutBooking() throws Exception {
        Concert concert = createOnSaleConcert();
        TicketCategory category = createCategory(concert, "Scarce", "100.00", 3, 4);

        performBooking(concert.getId(), item(category.getId(), 4))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_TICKET_QUANTITY"));

        assertThat(available(category)).isEqualTo(3);
        assertThat(bookingRepository.countByConcertId(concert.getId())).isZero();
    }

    @Test
    void createBooking_secondInventoryUpdateFails_rollsBackFirstDecrementAndAllInserts() throws Exception {
        Concert concert = createOnSaleConcert();
        TicketCategory first = createCategory(concert, "Rollback A", "100.00", 5, 4);
        TicketCategory second = createCategory(concert, "Rollback B", "200.00", 1, 4);

        performBooking(concert.getId(), item(first.getId(), 1) + "," + item(second.getId(), 2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_TICKET_QUANTITY"));

        assertThat(available(first)).isEqualTo(5);
        assertThat(available(second)).isEqualTo(1);
        assertThat(bookingRepository.countByConcertId(concert.getId())).isZero();
        assertThat(bookingItemRepository.countByBookingConcertId(concert.getId())).isZero();
        assertThat(bookingStatusHistoryRepository.countByBookingConcertId(concert.getId())).isZero();
    }

    @Test
    void createBooking_duplicateCategory_isRejectedConsistently() throws Exception {
        Concert concert = createOnSaleConcert();
        TicketCategory category = createCategory(concert, "Duplicate", "100.00", 10, 4);

        performBooking(concert.getId(), item(category.getId(), 1) + "," + item(category.getId(), 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        assertThat(available(category)).isEqualTo(10);
        assertThat(bookingRepository.countByConcertId(concert.getId())).isZero();
    }

    @Test
    void createBooking_userDoesNotExist_returnsNotFound() throws Exception {
        Concert concert = createOnSaleConcert();
        TicketCategory category = createCategory(concert, "Missing user", "100.00", 10, 4);

        mockMvc.perform(post("/api/v1/bookings")
                        .header("X-User-Id", 999999)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(concert.getId(), item(category.getId(), 1))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void createBooking_unpublishedConcert_returnsNotOnSale() throws Exception {
        Concert concert = createConcert(ConcertStatus.DRAFT,
                Instant.now().minus(1, ChronoUnit.HOURS),
                Instant.now().plus(1, ChronoUnit.HOURS));
        TicketCategory category = createCategory(concert, "Draft", "100.00", 10, 4);

        performBooking(concert.getId(), item(category.getId(), 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCERT_NOT_ON_SALE"));
    }

    @Test
    void createBooking_outsideUtcSalePeriod_returnsNotOnSale() throws Exception {
        Concert concert = createConcert(ConcertStatus.PUBLISHED,
                Instant.now().plus(1, ChronoUnit.HOURS),
                Instant.now().plus(2, ChronoUnit.HOURS));
        TicketCategory category = createCategory(concert, "Future sale", "100.00", 10, 4);

        performBooking(concert.getId(), item(category.getId(), 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCERT_NOT_ON_SALE"));
    }

    @Test
    void createBooking_nonPositiveQuantity_returnsValidationError() throws Exception {
        Concert concert = createOnSaleConcert();
        TicketCategory category = createCategory(concert, "Quantity validation", "100.00", 10, 4);

        performBooking(concert.getId(), item(category.getId(), 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        assertThat(available(category)).isEqualTo(10);
    }

    private org.springframework.test.web.servlet.ResultActions performBooking(long concertId, String items)
            throws Exception {
        return mockMvc.perform(post("/api/v1/bookings")
                .header("X-User-Id", 1)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(request(concertId, items)));
    }

    private Concert createOnSaleConcert() {
        return createConcert(ConcertStatus.PUBLISHED,
                Instant.now().minus(1, ChronoUnit.HOURS),
                Instant.now().plus(1, ChronoUnit.HOURS));
    }

    private Concert createConcert(ConcertStatus status, Instant saleStart, Instant saleEnd) {
        return concertRepository.save(Concert.builder()
                .title("Booking test concert " + System.nanoTime())
                .venue("Test venue")
                .totalCapacity(100)
                .status(status)
                .saleStartTime(saleStart)
                .saleEndTime(saleEnd)
                .concertStartTime(saleEnd.plus(1, ChronoUnit.DAYS))
                .build());
    }

    private TicketCategory createCategory(Concert concert, String name, String price,
                                            int available, int maxPerBooking) {
        return ticketCategoryRepository.save(TicketCategory.builder()
                .concert(concert)
                .name(name)
                .price(new BigDecimal(price))
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
