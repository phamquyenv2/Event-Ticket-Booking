package com.quyen.geekticket.controller;

import com.quyen.geekticket.domain.entity.Booking;
import com.quyen.geekticket.domain.entity.BookingItem;
import com.quyen.geekticket.domain.entity.BookingStatusHistory;
import com.quyen.geekticket.domain.entity.Concert;
import com.quyen.geekticket.domain.entity.TicketCategory;
import com.quyen.geekticket.domain.entity.User;
import com.quyen.geekticket.domain.entity.Voucher;
import com.quyen.geekticket.domain.entity.VoucherRedemption;
import com.quyen.geekticket.repository.BookingRepository;
import com.quyen.geekticket.repository.BookingStatusHistoryRepository;
import com.quyen.geekticket.repository.ConcertRepository;
import com.quyen.geekticket.repository.TicketCategoryRepository;
import com.quyen.geekticket.repository.UserRepository;
import com.quyen.geekticket.repository.VoucherRedemptionRepository;
import com.quyen.geekticket.repository.VoucherRepository;
import com.quyen.geekticket.util.constant.BookingStatus;
import com.quyen.geekticket.util.constant.ConcertStatus;
import com.quyen.geekticket.util.constant.DiscountType;
import com.quyen.geekticket.util.constant.UserRole;
import com.quyen.geekticket.util.constant.VoucherStatus;
import org.junit.jupiter.api.BeforeEach;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class BookingCustomerLifecycleIntegrationTest {

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

    @Autowired
    private VoucherRepository voucherRepository;

    @Autowired
    private VoucherRedemptionRepository voucherRedemptionRepository;

    private User owner;
    private User otherUser;
    private Concert concert;
    private TicketCategory category;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(User.builder()
                .username("owner_" + System.nanoTime())
                .email("owner_" + System.nanoTime() + "@example.com")
                .fullName("Owner User")
                .role(UserRole.CUSTOMER)
                .build());

        otherUser = userRepository.save(User.builder()
                .username("other_" + System.nanoTime())
                .email("other_" + System.nanoTime() + "@example.com")
                .fullName("Other User")
                .role(UserRole.CUSTOMER)
                .build());

        concert = concertRepository.save(Concert.builder()
                .title("Lifecycle Concert " + System.nanoTime())
                .venue("Main Stage")
                .totalCapacity(200)
                .status(ConcertStatus.PUBLISHED)
                .saleStartTime(Instant.now().minus(1, ChronoUnit.HOURS))
                .saleEndTime(Instant.now().plus(1, ChronoUnit.HOURS))
                .concertStartTime(Instant.now().plus(2, ChronoUnit.DAYS))
                .build());

        category = ticketCategoryRepository.save(TicketCategory.builder()
                .concert(concert)
                .name("GA")
                .price(new BigDecimal("100.00"))
                .totalQuantity(50)
                .availableQuantity(50)
                .maxQuantityPerBooking(4)
                .build());
    }

    @Test
    void getBookingByCode_ownerRetrievesBooking_returnsOk() throws Exception {
        Booking booking = createBookingInStatus(owner, BookingStatus.RESERVED, 2);

        mockMvc.perform(get("/api/v1/bookings/" + booking.getBookingCode())
                        .header("X-User-Id", owner.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.bookingCode").value(booking.getBookingCode()))
                .andExpect(jsonPath("$.data.status").value("RESERVED"))
                .andExpect(jsonPath("$.data.subtotal").value(200.00));
    }

    @Test
    void getBookingByCode_otherUserCannotRetrieveIt_returnsNotFound() throws Exception {
        Booking booking = createBookingInStatus(owner, BookingStatus.RESERVED, 2);

        mockMvc.perform(get("/api/v1/bookings/" + booking.getBookingCode())
                        .header("X-User-Id", otherUser.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOKING_NOT_FOUND"));
    }

    @Test
    void cancelBooking_reservedBooking_transitionsStatusAndRestoresInventoryAndWritesHistory() throws Exception {
        // Reserve 2 tickets out of 50 -> available becomes 48
        decrementCategoryQuantity(category.getId(), 2);
        Booking booking = createBookingInStatus(owner, BookingStatus.RESERVED, 2);

        assertThat(getAvailableQuantity(category.getId())).isEqualTo(48);

        mockMvc.perform(post("/api/v1/bookings/" + booking.getId() + "/cancel")
                        .header("X-User-Id", owner.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Cannot attend live event\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        // Inventory restored from 48 -> 50
        assertThat(getAvailableQuantity(category.getId())).isEqualTo(50);

        // Verify status in DB
        Booking reloaded = bookingRepository.findById(booking.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(BookingStatus.CANCELLED);

        // Verify audit status history written
        List<BookingStatusHistory> histories = bookingStatusHistoryRepository.findByBookingId(booking.getId());
        assertThat(histories).hasSize(2); // RESERVED initially, CANCELLED on cancel
        BookingStatusHistory latest = histories.get(histories.size() - 1);
        assertThat(latest.getFromStatus()).isEqualTo("RESERVED");
        assertThat(latest.getToStatus()).isEqualTo("CANCELLED");
        assertThat(latest.getChangedBy()).isEqualTo("USER:" + owner.getId());
        assertThat(latest.getReason()).isEqualTo("Cannot attend live event");
    }

    @Test
    void cancelBooking_repeatedCancel_returnsConflictAndDoesNotRestoreInventoryTwice() throws Exception {
        decrementCategoryQuantity(category.getId(), 2);
        Booking booking = createBookingInStatus(owner, BookingStatus.RESERVED, 2);

        // First cancel succeeds -> inventory restored from 48 to 50
        mockMvc.perform(post("/api/v1/bookings/" + booking.getId() + "/cancel")
                        .header("X-User-Id", owner.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"First cancel\"}"))
                .andExpect(status().isOk());

        assertThat(getAvailableQuantity(category.getId())).isEqualTo(50);

        // Second cancel attempt on already CANCELLED booking
        mockMvc.perform(post("/api/v1/bookings/" + booking.getId() + "/cancel")
                        .header("X-User-Id", owner.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Second cancel\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_BOOKING_STATUS_TRANSITION"));

        // Inventory remains 50 (NOT incremented twice to 52)
        assertThat(getAvailableQuantity(category.getId())).isEqualTo(50);
    }

    @Test
    void cancelBooking_invalidStateConfirmedExpiredFailed_returnsConflictWithoutInventoryChange() throws Exception {
        for (BookingStatus invalidStatus : List.of(BookingStatus.CONFIRMED, BookingStatus.EXPIRED, BookingStatus.FAILED)) {
            decrementCategoryQuantity(category.getId(), 1);
            int availableBefore = getAvailableQuantity(category.getId());
            Booking booking = createBookingInStatus(owner, invalidStatus, 1);

            mockMvc.perform(post("/api/v1/bookings/" + booking.getId() + "/cancel")
                            .header("X-User-Id", owner.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\":\"Attempt cancel on " + invalidStatus + "\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("INVALID_BOOKING_STATUS_TRANSITION"));

            // Verify inventory is NOT changed
            assertThat(getAvailableQuantity(category.getId())).isEqualTo(availableBefore);

            Booking reloaded = bookingRepository.findById(booking.getId()).orElseThrow();
            assertThat(reloaded.getStatus()).isEqualTo(invalidStatus);
        }
    }

    @Test
    void cancelBooking_otherUserCannotCancel_returnsNotFound() throws Exception {
        Booking booking = createBookingInStatus(owner, BookingStatus.RESERVED, 1);

        mockMvc.perform(post("/api/v1/bookings/" + booking.getId() + "/cancel")
                        .header("X-User-Id", otherUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Malicious cancel\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOKING_NOT_FOUND"));
    }

    @Test
    void cancelBooking_withVoucher_restoresVoucherUsageAndRemovesRedemption() throws Exception {
        Voucher voucher = voucherRepository.save(Voucher.builder()
                .code("LIFECYCLE_VOUCHER_" + System.nanoTime())
                .description("Lifecycle voucher")
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(new BigDecimal("20.00"))
                .totalUsageLimit(10)
                .perUserLimit(1)
                .currentUsageCount(1)
                .status(VoucherStatus.ACTIVE)
                .startTime(Instant.now().minus(1, ChronoUnit.HOURS))
                .endTime(Instant.now().plus(1, ChronoUnit.DAYS))
                .concert(concert)
                .build());

        decrementCategoryQuantity(category.getId(), 2);
        Booking booking = createBookingInStatus(owner, BookingStatus.RESERVED, 2);

        VoucherRedemption redemption = voucherRedemptionRepository.save(VoucherRedemption.builder()
                .voucher(voucher)
                .booking(booking)
                .user(owner)
                .discountAmount(new BigDecimal("20.00"))
                .build());
        booking.setVoucherRedemption(redemption);
        bookingRepository.saveAndFlush(booking);

        assertThat(voucherRepository.findById(voucher.getId()).orElseThrow().getCurrentUsageCount()).isEqualTo(1);
        assertThat(voucherRedemptionRepository.existsByVoucherIdAndUserId(voucher.getId(), owner.getId())).isTrue();

        // Perform cancellation
        mockMvc.perform(post("/api/v1/bookings/" + booking.getId() + "/cancel")
                        .header("X-User-Id", owner.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Cancel with voucher\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        // Verify voucher current_usage_count decremented from 1 -> 0
        Voucher reloadedVoucher = voucherRepository.findById(voucher.getId()).orElseThrow();
        assertThat(reloadedVoucher.getCurrentUsageCount()).isZero();

        // Verify redemption deleted from DB
        assertThat(voucherRedemptionRepository.existsByVoucherIdAndUserId(voucher.getId(), owner.getId())).isFalse();
    }

    private Booking createBookingInStatus(User user, BookingStatus status, int quantity) {
        Booking booking = Booking.builder()
                .bookingCode("BK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .user(user)
                .concert(concert)
                .status(status)
                .bookingItems(new ArrayList<>())
                .statusHistories(new ArrayList<>())
                .build();

        BookingItem item = BookingItem.builder()
                .booking(booking)
                .ticketCategory(category)
                .quantity(quantity)
                .unitPrice(category.getPrice())
                .subtotal(category.getPrice().multiply(BigDecimal.valueOf(quantity)))
                .build();

        booking.getBookingItems().add(item);
        booking.calculateAmounts(BigDecimal.ZERO);

        booking.getStatusHistories().add(BookingStatusHistory.builder()
                .booking(booking)
                .toStatus(status.name())
                .changedBy("SYSTEM")
                .reason("Initial status for test")
                .build());

        return bookingRepository.saveAndFlush(booking);
    }

    private void decrementCategoryQuantity(Long categoryId, int qty) {
        TicketCategory tc = ticketCategoryRepository.findById(categoryId).orElseThrow();
        tc.setAvailableQuantity(tc.getAvailableQuantity() - qty);
        ticketCategoryRepository.saveAndFlush(tc);
    }

    private int getAvailableQuantity(Long categoryId) {
        return ticketCategoryRepository.findById(categoryId).orElseThrow().getAvailableQuantity();
    }
}
