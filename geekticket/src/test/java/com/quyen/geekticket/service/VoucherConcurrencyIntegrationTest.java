package com.quyen.geekticket.service;

import com.quyen.geekticket.domain.entity.Concert;
import com.quyen.geekticket.domain.entity.TicketCategory;
import com.quyen.geekticket.domain.entity.User;
import com.quyen.geekticket.domain.entity.Voucher;
import com.quyen.geekticket.domain.request.BookingItemRequest;
import com.quyen.geekticket.domain.request.CreateBookingRequest;
import com.quyen.geekticket.domain.response.booking.BookingResponse;
import com.quyen.geekticket.repository.ConcertRepository;
import com.quyen.geekticket.repository.TicketCategoryRepository;
import com.quyen.geekticket.repository.UserRepository;
import com.quyen.geekticket.repository.VoucherRedemptionRepository;
import com.quyen.geekticket.repository.VoucherRepository;
import com.quyen.geekticket.util.constant.ConcertStatus;
import com.quyen.geekticket.util.constant.DiscountType;
import com.quyen.geekticket.util.constant.UserRole;
import com.quyen.geekticket.util.constant.VoucherStatus;
import com.quyen.geekticket.util.error.BusinessException;
import com.quyen.geekticket.util.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class VoucherConcurrencyIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private BookingService bookingService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConcertRepository concertRepository;

    @Autowired
    private TicketCategoryRepository ticketCategoryRepository;

    @Autowired
    private VoucherRepository voucherRepository;

    @Autowired
    private VoucherRedemptionRepository voucherRedemptionRepository;

    @Test
    void createBooking_lastVoucherConcurrentRequests_onlyOneSucceeds() throws Exception {
        Concert concert = concertRepository.save(Concert.builder()
                .title("Concurrent Voucher Concert " + System.nanoTime())
                .venue("Venue")
                .totalCapacity(500)
                .status(ConcertStatus.PUBLISHED)
                .saleStartTime(Instant.now().minus(1, ChronoUnit.HOURS))
                .saleEndTime(Instant.now().plus(1, ChronoUnit.HOURS))
                .concertStartTime(Instant.now().plus(2, ChronoUnit.DAYS))
                .build());

        TicketCategory category = ticketCategoryRepository.save(TicketCategory.builder()
                .concert(concert)
                .name("GA")
                .price(new BigDecimal("100.00"))
                .totalQuantity(100)
                .availableQuantity(100)
                .maxQuantityPerBooking(4)
                .build());

        String voucherCode = "LAST_VOUCHER_" + System.nanoTime();
        Voucher voucher = voucherRepository.save(Voucher.builder()
                .code(voucherCode)
                .description("Last voucher concurrency test")
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(new BigDecimal("10.00"))
                .totalUsageLimit(1)
                .perUserLimit(1)
                .currentUsageCount(0)
                .status(VoucherStatus.ACTIVE)
                .startTime(Instant.now().minus(1, ChronoUnit.HOURS))
                .endTime(Instant.now().plus(1, ChronoUnit.DAYS))
                .concert(concert)
                .build());

        int threadCount = 20;
        List<User> users = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            users.add(userRepository.save(User.builder()
                    .username("vuser" + i + "_" + System.nanoTime())
                    .email("vuser" + i + "_" + System.nanoTime() + "@example.com")
                    .fullName("Voucher User " + i)
                    .role(UserRole.CUSTOMER)
                    .build()));
        }

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<Result>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final User user = users.get(i);
            futures.add(executor.submit(() -> {
                startLatch.await();
                try {
                    CreateBookingRequest request = CreateBookingRequest.builder()
                            .concertId(concert.getId())
                            .voucherCode(voucherCode)
                            .items(List.of(BookingItemRequest.builder()
                                    .ticketCategoryId(category.getId())
                                    .quantity(1)
                                    .build()))
                            .build();

                    BookingResponse response = bookingService.createBooking(
                            user.getId(), UUID.randomUUID().toString(), request);
                    return new Result(true, null, response);
                } catch (Exception e) {
                    return new Result(false, e, null);
                }
            }));
        }

        startLatch.countDown();
        executor.shutdown();

        int successes = 0;
        int limitReachedErrors = 0;

        for (Future<Result> future : futures) {
            Result res = future.get();
            if (res.success) {
                successes++;
            } else if (res.exception instanceof BusinessException be
                    && be.getErrorCode() == ErrorCode.VOUCHER_USAGE_LIMIT_REACHED) {
                limitReachedErrors++;
            }
        }

        assertThat(successes).isEqualTo(1);
        assertThat(limitReachedErrors).isEqualTo(threadCount - 1);

        Voucher reloadedVoucher = voucherRepository.findById(voucher.getId()).orElseThrow();
        assertThat(reloadedVoucher.getCurrentUsageCount()).isEqualTo(1);
        assertThat(voucherRedemptionRepository.countByVoucherId(voucher.getId())).isEqualTo(1);
    }

    private record Result(boolean success, Exception exception, BookingResponse response) {
    }
}
