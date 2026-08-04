package com.quyen.geekticket.controller;

import com.quyen.geekticket.domain.entity.Booking;
import com.quyen.geekticket.domain.entity.BookingItem;
import com.quyen.geekticket.domain.entity.BookingStatusHistory;
import com.quyen.geekticket.domain.entity.Concert;
import com.quyen.geekticket.domain.entity.TicketCategory;
import com.quyen.geekticket.domain.entity.User;
import com.quyen.geekticket.repository.BookingRepository;
import com.quyen.geekticket.repository.BookingStatusHistoryRepository;
import com.quyen.geekticket.repository.ConcertRepository;
import com.quyen.geekticket.repository.TicketCategoryRepository;
import com.quyen.geekticket.repository.UserRepository;
import com.quyen.geekticket.util.constant.BookingStatus;
import com.quyen.geekticket.util.constant.ConcertStatus;
import com.quyen.geekticket.util.constant.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class OperationBookingWorkflowIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConcertRepository concertRepository;

    @Autowired
    private TicketCategoryRepository ticketCategoryRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingStatusHistoryRepository bookingStatusHistoryRepository;

    private User operator;
    private User customer;
    private Concert concert;
    private TicketCategory vipCategory;
    private Booking reservedBooking;
    private Booking cancelledBooking;

    @BeforeEach
    void setUp() {
        bookingStatusHistoryRepository.deleteAll();
        bookingRepository.deleteAll();
        ticketCategoryRepository.deleteAll();
        concertRepository.deleteAll();
        userRepository.deleteAll();

        operator = userRepository.save(User.builder()
                .username("op_" + UUID.randomUUID().toString().substring(0, 8))
                .email("op_" + UUID.randomUUID() + "@test.com")
                .fullName("Op User")
                .role(UserRole.OPERATOR)
                .build());

        customer = userRepository.save(User.builder()
                .username("cust_" + UUID.randomUUID().toString().substring(0, 8))
                .email("cust_" + UUID.randomUUID() + "@test.com")
                .fullName("Customer User")
                .role(UserRole.CUSTOMER)
                .build());

        concert = concertRepository.save(Concert.builder()
                .title("Test Concert")
                .description("Description")
                .venue("Venue")
                .totalCapacity(100)
                .status(ConcertStatus.PUBLISHED)
                .saleStartTime(Instant.now().minus(1, ChronoUnit.DAYS))
                .saleEndTime(Instant.now().plus(1, ChronoUnit.DAYS))
                .concertStartTime(Instant.now().plus(2, ChronoUnit.DAYS))
                .build());

        vipCategory = ticketCategoryRepository.save(TicketCategory.builder()
                .concert(concert)
                .name("VIP")
                .price(new BigDecimal("100.00"))
                .totalQuantity(50)
                .availableQuantity(48)
                .build());

        // Create a RESERVED booking
        reservedBooking = bookingRepository.save(Booking.builder()
                .bookingCode("BK-RES-" + UUID.randomUUID().toString().substring(0, 8))
                .user(customer)
                .concert(concert)
                .totalAmount(new BigDecimal("200.00"))
                .discountAmount(BigDecimal.ZERO)
                .finalAmount(new BigDecimal("200.00"))
                .status(BookingStatus.RESERVED)
                .suspicious(false)
                .build());

        BookingItem item1 = BookingItem.builder()
                .booking(reservedBooking)
                .ticketCategory(vipCategory)
                .quantity(2)
                .unitPrice(new BigDecimal("100.00"))
                .subtotal(new BigDecimal("200.00"))
                .build();
        reservedBooking.getBookingItems().add(item1);

        bookingStatusHistoryRepository.save(BookingStatusHistory.builder()
                .booking(reservedBooking)
                .toStatus(BookingStatus.RESERVED.name())
                .changedBy("USER:" + customer.getId())
                .reason("Initial booking")
                .build());

        reservedBooking = bookingRepository.saveAndFlush(reservedBooking);

        // Create a CANCELLED booking
        cancelledBooking = bookingRepository.save(Booking.builder()
                .bookingCode("BK-CAN-" + UUID.randomUUID().toString().substring(0, 8))
                .user(customer)
                .concert(concert)
                .totalAmount(new BigDecimal("100.00"))
                .discountAmount(BigDecimal.ZERO)
                .finalAmount(new BigDecimal("100.00"))
                .status(BookingStatus.CANCELLED)
                .suspicious(true)
                .build());

        bookingStatusHistoryRepository.save(BookingStatusHistory.builder()
                .booking(cancelledBooking)
                .fromStatus(BookingStatus.RESERVED.name())
                .toStatus(BookingStatus.CANCELLED.name())
                .changedBy("USER:" + customer.getId())
                .reason("Customer cancelled")
                .build());

        cancelledBooking = bookingRepository.saveAndFlush(cancelledBooking);
    }

    @Test
    @DisplayName("GET /api/v1/operations/bookings with filters and pagination should return matched bookings")
    void getBookings_withFilters_shouldReturnPaginatedBookings() throws Exception {
        mockMvc.perform(get("/api/v1/operations/bookings")
                        .header("X-Operator-Id", operator.getId())
                        .param("status", "RESERVED")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].bookingCode").value(reservedBooking.getBookingCode()))
                .andExpect(jsonPath("$.data.content[0].status").value("RESERVED"));
    }

    @Test
    @DisplayName("GET /api/v1/operations/bookings without X-Operator-Id should fail")
    void getBookings_withoutOperatorHeader_shouldFail() throws Exception {
        mockMvc.perform(get("/api/v1/operations/bookings"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/operations/bookings/{bookingId} should return full detail with status histories")
    void getBookingDetail_shouldReturnFullDetailWithStatusHistory() throws Exception {
        mockMvc.perform(get("/api/v1/operations/bookings/" + reservedBooking.getId())
                        .header("X-Operator-Id", operator.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(reservedBooking.getId()))
                .andExpect(jsonPath("$.data.bookingCode").value(reservedBooking.getBookingCode()))
                .andExpect(jsonPath("$.data.statusHistories.length()").value(1))
                .andExpect(jsonPath("$.data.statusHistories[0].toStatus").value("RESERVED"));
    }

    @Test
    @DisplayName("PATCH /api/v1/operations/bookings/{bookingId}/status should confirm RESERVED booking and log audit")
    void updateBookingStatus_validConfirmation_shouldUpdateStatusAndWriteAuditLog() throws Exception {
        String requestBody = """
                {
                  "status": "CONFIRMED",
                  "reason": "Payment verified manually by operator"
                }
                """;

        mockMvc.perform(patch("/api/v1/operations/bookings/" + reservedBooking.getId() + "/status")
                        .header("X-Operator-Id", operator.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.statusHistories.length()").value(2));

        Booking updated = bookingRepository.findDetailById(reservedBooking.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(BookingStatus.CONFIRMED);

        List<BookingStatusHistory> histories = bookingStatusHistoryRepository.findByBookingId(reservedBooking.getId());
        assertThat(histories).hasSize(2);
        BookingStatusHistory latest = histories.get(histories.size() - 1);
        assertThat(latest.getFromStatus()).isEqualTo("RESERVED");
        assertThat(latest.getToStatus()).isEqualTo("CONFIRMED");
        assertThat(latest.getChangedBy()).isEqualTo("OPERATOR:" + operator.getId());
        assertThat(latest.getReason()).isEqualTo("Payment verified manually by operator");
    }

    @Test
    @DisplayName("PATCH /api/v1/operations/bookings/{bookingId}/status should return 409 for invalid transition CANCELLED -> CONFIRMED")
    void updateBookingStatus_invalidTransition_shouldReturn409Conflict() throws Exception {
        String requestBody = """
                {
                  "status": "CONFIRMED",
                  "reason": "Attempting invalid transition"
                }
                """;

        mockMvc.perform(patch("/api/v1/operations/bookings/" + cancelledBooking.getId() + "/status")
                        .header("X-Operator-Id", operator.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_BOOKING_STATUS_TRANSITION"));
    }

    @Test
    @DisplayName("PATCH /api/v1/operations/bookings/{bookingId}/suspicious should update suspicious flag")
    void updateBookingSuspicious_shouldUpdateFlag() throws Exception {
        String requestBody = """
                {
                  "suspicious": true,
                  "reason": "Flagged due to unusual activity"
                }
                """;

        mockMvc.perform(patch("/api/v1/operations/bookings/" + reservedBooking.getId() + "/suspicious")
                        .header("X-Operator-Id", operator.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.suspicious").value(true));

        Booking updated = bookingRepository.findById(reservedBooking.getId()).orElseThrow();
        assertThat(updated.getSuspicious()).isTrue();
    }
}
