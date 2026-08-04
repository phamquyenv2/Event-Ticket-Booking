package com.quyen.geekticket.controller;

import com.quyen.geekticket.domain.entity.Concert;
import com.quyen.geekticket.domain.entity.TicketCategory;
import com.quyen.geekticket.domain.entity.Voucher;

import com.quyen.geekticket.repository.BookingRepository;
import com.quyen.geekticket.repository.ConcertRepository;
import com.quyen.geekticket.repository.TicketCategoryRepository;
import com.quyen.geekticket.repository.VoucherRedemptionRepository;
import com.quyen.geekticket.repository.VoucherRepository;
import com.quyen.geekticket.util.constant.ConcertStatus;
import com.quyen.geekticket.util.constant.DiscountType;
import com.quyen.geekticket.util.constant.VoucherStatus;
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
class VoucherIntegrationTest {

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
    private VoucherRepository voucherRepository;

    @Autowired
    private VoucherRedemptionRepository voucherRedemptionRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Test
    void createBooking_activeValidVoucher_appliesDiscountAndIncrementsUsage() throws Exception {
        Concert concert = createOnSaleConcert();
        TicketCategory category = createCategory(concert, "VIP", "1000.00", 10, 4);
        Voucher voucher = createVoucher("ACTIVE10", VoucherStatus.ACTIVE, DiscountType.PERCENTAGE,
                new BigDecimal("10.00"), new BigDecimal("200.00"), new BigDecimal("500.00"),
                100, 0, concert);

        mockMvc.perform(post("/api/v1/bookings")
                        .header("X-User-Id", 1)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(concert.getId(), "ACTIVE10", item(category.getId(), 1))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.bookingCode", startsWith("BK-")))
                .andExpect(jsonPath("$.data.subtotal").value(1000.00))
                .andExpect(jsonPath("$.data.discountAmount").value(100.00))
                .andExpect(jsonPath("$.data.totalAmount").value(900.00))
                .andExpect(jsonPath("$.data.voucherCode").value("ACTIVE10"));

        Voucher updated = voucherRepository.findById(voucher.getId()).orElseThrow();
        assertThat(updated.getCurrentUsageCount()).isEqualTo(1);
        assertThat(voucherRedemptionRepository.existsByVoucherIdAndUserId(voucher.getId(), 1L)).isTrue();
    }

    @Test
    void createBooking_expiredVoucher_returnsVoucherExpired() throws Exception {
        Concert concert = createOnSaleConcert();
        TicketCategory category = createCategory(concert, "ExpiredCat", "100.00", 10, 4);
        createExpiredVoucher("EXPIRED_VOUCHER", concert);

        performBooking(1L, concert.getId(), "EXPIRED_VOUCHER", item(category.getId(), 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VOUCHER_EXPIRED"));
    }

    @Test
    void createBooking_inactiveVoucher_returnsVoucherNotApplicable() throws Exception {
        Concert concert = createOnSaleConcert();
        TicketCategory category = createCategory(concert, "InactiveCat", "100.00", 10, 4);
        createVoucher("INACTIVE_VOUCHER", VoucherStatus.INACTIVE, DiscountType.FIXED_AMOUNT,
                new BigDecimal("50.00"), null, null, 100, 0, concert);

        performBooking(1L, concert.getId(), "INACTIVE_VOUCHER", item(category.getId(), 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VOUCHER_NOT_APPLICABLE"));
    }

    @Test
    void createBooking_wrongConcert_returnsVoucherNotApplicable() throws Exception {
        Concert concertA = createOnSaleConcert();
        Concert concertB = createOnSaleConcert();
        TicketCategory categoryA = createCategory(concertA, "Cat A", "100.00", 10, 4);
        createVoucher("CONCERT_B_VOUCHER", VoucherStatus.ACTIVE, DiscountType.FIXED_AMOUNT,
                new BigDecimal("50.00"), null, null, 100, 0, concertB);

        performBooking(1L, concertA.getId(), "CONCERT_B_VOUCHER", item(categoryA.getId(), 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VOUCHER_NOT_APPLICABLE"));
    }

    @Test
    void createBooking_minimumAmountNotMet_returnsVoucherNotApplicable() throws Exception {
        Concert concert = createOnSaleConcert();
        TicketCategory category = createCategory(concert, "CheapCat", "200.00", 10, 4);
        createVoucher("MIN500", VoucherStatus.ACTIVE, DiscountType.FIXED_AMOUNT,
                new BigDecimal("50.00"), null, new BigDecimal("500.00"), 100, 0, concert);

        performBooking(1L, concert.getId(), "MIN500", item(category.getId(), 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VOUCHER_NOT_APPLICABLE"));
    }

    @Test
    void createBooking_percentageMaxDiscount_capsDiscountAmount() throws Exception {
        Concert concert = createOnSaleConcert();
        TicketCategory category = createCategory(concert, "Expensive", "1000.00", 10, 4);
        createVoucher("MAXCAP50", VoucherStatus.ACTIVE, DiscountType.PERCENTAGE,
                new BigDecimal("50.00"), new BigDecimal("100.00"), new BigDecimal("100.00"), 100, 0, concert);

        mockMvc.perform(post("/api/v1/bookings")
                        .header("X-User-Id", 1)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(concert.getId(), "MAXCAP50", item(category.getId(), 1))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.subtotal").value(1000.00))
                .andExpect(jsonPath("$.data.discountAmount").value(100.00))
                .andExpect(jsonPath("$.data.totalAmount").value(900.00));
    }

    @Test
    void createBooking_fixedDiscountLargerThanSubtotal_capsDiscountToSubtotal() throws Exception {
        Concert concert = createOnSaleConcert();
        TicketCategory category = createCategory(concert, "SmallItem", "200.00", 10, 4);
        createVoucher("FIXED300", VoucherStatus.ACTIVE, DiscountType.FIXED_AMOUNT,
                new BigDecimal("300.00"), null, null, 100, 0, concert);

        mockMvc.perform(post("/api/v1/bookings")
                        .header("X-User-Id", 1)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(concert.getId(), "FIXED300", item(category.getId(), 1))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.subtotal").value(200.00))
                .andExpect(jsonPath("$.data.discountAmount").value(200.00))
                .andExpect(jsonPath("$.data.totalAmount").value(0.00));
    }

    @Test
    void createBooking_userReusesVoucher_returnsVoucherAlreadyUsed() throws Exception {
        Concert concert = createOnSaleConcert();
        TicketCategory category = createCategory(concert, "ReuseCat", "500.00", 10, 4);
        createVoucher("ONCE_ONLY", VoucherStatus.ACTIVE, DiscountType.FIXED_AMOUNT,
                new BigDecimal("50.00"), null, null, 100, 0, concert);

        // First use by user 1
        performBooking(1L, concert.getId(), "ONCE_ONLY", item(category.getId(), 1))
                .andExpect(status().isCreated());

        // Second use by same user 1 (different idempotency key)
        performBooking(1L, concert.getId(), "ONCE_ONLY", item(category.getId(), 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VOUCHER_ALREADY_USED"));
    }

    @Test
    void createBooking_voucherGlobalLimitReached_returnsVoucherUsageLimitReached() throws Exception {
        Concert concert = createOnSaleConcert();
        TicketCategory category = createCategory(concert, "LimitCat", "500.00", 10, 4);
        createVoucher("EXHAUSTED", VoucherStatus.ACTIVE, DiscountType.FIXED_AMOUNT,
                new BigDecimal("50.00"), null, null, 1, 1, concert);

        performBooking(1L, concert.getId(), "EXHAUSTED", item(category.getId(), 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VOUCHER_USAGE_LIMIT_REACHED"));
    }

    @Test
    void createBooking_voucherFailureRollsBackInventory() throws Exception {
        Concert concert = createOnSaleConcert();
        TicketCategory category = createCategory(concert, "RollbackCat", "500.00", 10, 4);
        createExpiredVoucher("FAIL_VOUCHER", concert);

        performBooking(1L, concert.getId(), "FAIL_VOUCHER", item(category.getId(), 2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VOUCHER_EXPIRED"));

        // Verify inventory is NOT decremented
        TicketCategory reloaded = ticketCategoryRepository.findById(category.getId()).orElseThrow();
        assertThat(reloaded.getAvailableQuantity()).isEqualTo(10);
        assertThat(bookingRepository.countByConcertId(concert.getId())).isZero();
    }

    @Test
    void createBooking_idempotentReplay_doesNotIncrementVoucherUsageAgain() throws Exception {
        Concert concert = createOnSaleConcert();
        TicketCategory category = createCategory(concert, "IdemCat", "500.00", 10, 4);
        Voucher voucher = createVoucher("IDEM_VOUCHER", VoucherStatus.ACTIVE, DiscountType.FIXED_AMOUNT,
                new BigDecimal("50.00"), null, null, 100, 0, concert);

        String idempotencyKey = UUID.randomUUID().toString();
        String requestBody = request(concert.getId(), "IDEM_VOUCHER", item(category.getId(), 1));

        // Original request
        mockMvc.perform(post("/api/v1/bookings")
                        .header("X-User-Id", 1)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.voucherCode").value("IDEM_VOUCHER"));

        Voucher afterFirst = voucherRepository.findById(voucher.getId()).orElseThrow();
        assertThat(afterFirst.getCurrentUsageCount()).isEqualTo(1);

        // Replay same request
        mockMvc.perform(post("/api/v1/bookings")
                        .header("X-User-Id", 1)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.voucherCode").value("IDEM_VOUCHER"));

        Voucher afterReplay = voucherRepository.findById(voucher.getId()).orElseThrow();
        assertThat(afterReplay.getCurrentUsageCount()).isEqualTo(1);
    }

    private org.springframework.test.web.servlet.ResultActions performBooking(long userId, long concertId,
                                                                               String voucherCode, String items) throws Exception {
        return mockMvc.perform(post("/api/v1/bookings")
                .header("X-User-Id", userId)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(request(concertId, voucherCode, items)));
    }

    private Concert createOnSaleConcert() {
        return concertRepository.save(Concert.builder()
                .title("Voucher test concert " + System.nanoTime())
                .venue("Voucher venue")
                .totalCapacity(100)
                .status(ConcertStatus.PUBLISHED)
                .saleStartTime(Instant.now().minus(1, ChronoUnit.HOURS))
                .saleEndTime(Instant.now().plus(1, ChronoUnit.HOURS))
                .concertStartTime(Instant.now().plus(2, ChronoUnit.DAYS))
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

    private Voucher createVoucher(String code, VoucherStatus status, DiscountType discountType,
                                  BigDecimal discountValue, BigDecimal maxDiscount, BigDecimal minOrder,
                                  Integer totalLimit, Integer currentUsage, Concert concert) {
        return voucherRepository.save(Voucher.builder()
                .code(code)
                .description("Test voucher " + code)
                .discountType(discountType)
                .discountValue(discountValue)
                .maxDiscountAmount(maxDiscount)
                .minOrderAmount(minOrder)
                .totalUsageLimit(totalLimit)
                .perUserLimit(1)
                .currentUsageCount(currentUsage)
                .status(status)
                .startTime(Instant.now().minus(1, ChronoUnit.HOURS))
                .endTime(Instant.now().plus(1, ChronoUnit.DAYS))
                .concert(concert)
                .build());
    }

    private Voucher createExpiredVoucher(String code, Concert concert) {
        return voucherRepository.save(Voucher.builder()
                .code(code)
                .description("Expired voucher " + code)
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(new BigDecimal("50.00"))
                .totalUsageLimit(100)
                .perUserLimit(1)
                .currentUsageCount(0)
                .status(VoucherStatus.ACTIVE)
                .startTime(Instant.now().minus(10, ChronoUnit.DAYS))
                .endTime(Instant.now().minus(1, ChronoUnit.DAYS))
                .concert(concert)
                .build());
    }

    private String request(long concertId, String voucherCode, String items) {
        if (voucherCode != null) {
            return "{\"concertId\":" + concertId + ",\"voucherCode\":\"" + voucherCode + "\",\"items\":[" + items + "]}";
        }
        return "{\"concertId\":" + concertId + ",\"items\":[" + items + "]}";
    }

    private String item(long categoryId, int quantity) {
        return "{\"ticketCategoryId\":" + categoryId + ",\"quantity\":" + quantity + "}";
    }
}
