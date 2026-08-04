package com.quyen.geekticket.service;

import com.quyen.geekticket.domain.entity.Concert;
import com.quyen.geekticket.domain.entity.TicketCategory;
import com.quyen.geekticket.domain.request.BookingItemRequest;
import com.quyen.geekticket.domain.request.CreateBookingRequest;
import com.quyen.geekticket.repository.BookingItemRepository;
import com.quyen.geekticket.repository.BookingRepository;
import com.quyen.geekticket.repository.BookingStatusHistoryRepository;
import com.quyen.geekticket.repository.ConcertRepository;
import com.quyen.geekticket.repository.TicketCategoryRepository;
import com.quyen.geekticket.util.constant.ConcertStatus;
import com.quyen.geekticket.util.error.BusinessException;
import com.quyen.geekticket.util.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class BookingConcurrencyIntegrationTest {

    private static final int AVAILABLE_TICKETS = 10;
    private static final int ATTEMPTS = 50;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private BookingService bookingService;

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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void fiftyConcurrentAttemptsForTenTickets_sellExactlyTenWithoutOverselling() throws Exception {
        Concert concert = concertRepository.save(Concert.builder()
                .title("Concurrency test concert")
                .venue("Test venue")
                .totalCapacity(AVAILABLE_TICKETS)
                .status(ConcertStatus.PUBLISHED)
                .saleStartTime(Instant.now().minus(1, ChronoUnit.HOURS))
                .saleEndTime(Instant.now().plus(1, ChronoUnit.HOURS))
                .concertStartTime(Instant.now().plus(1, ChronoUnit.DAYS))
                .build());
        TicketCategory category = ticketCategoryRepository.save(TicketCategory.builder()
                .concert(concert)
                .name("Exactly ten tickets")
                .price(new BigDecimal("100.00"))
                .totalQuantity(AVAILABLE_TICKETS)
                .availableQuantity(AVAILABLE_TICKETS)
                .maxQuantityPerBooking(1)
                .build());

        assertThat(category.getAvailableQuantity()).isEqualTo(AVAILABLE_TICKETS);

        CountDownLatch ready = new CountDownLatch(ATTEMPTS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(ATTEMPTS);
        AtomicInteger successful = new AtomicInteger();
        AtomicInteger insufficient = new AtomicInteger();
        Queue<Throwable> unexpectedFailures = new ConcurrentLinkedQueue<>();
        ExecutorService executor = Executors.newFixedThreadPool(ATTEMPTS);

        try {
            for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        bookingService.createBooking(1L, UUID.randomUUID().toString(),
                                CreateBookingRequest.builder()
                                .concertId(concert.getId())
                                .items(List.of(BookingItemRequest.builder()
                                        .ticketCategoryId(category.getId())
                                        .quantity(1)
                                        .build()))
                                .build());
                        successful.incrementAndGet();
                    } catch (BusinessException exception) {
                        if (exception.getErrorCode() == ErrorCode.INSUFFICIENT_TICKET_QUANTITY) {
                            insufficient.incrementAndGet();
                        } else {
                            unexpectedFailures.add(exception);
                        }
                    } catch (Throwable throwable) {
                        unexpectedFailures.add(throwable);
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

        TicketCategory reloaded = ticketCategoryRepository.findById(category.getId()).orElseThrow();
        Integer negativeRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ticket_categories WHERE id = ? AND available_quantity < 0",
                Integer.class,
                category.getId());

        assertThat(unexpectedFailures).isEmpty();
        assertThat(successful).hasValue(AVAILABLE_TICKETS);
        assertThat(insufficient).hasValue(ATTEMPTS - AVAILABLE_TICKETS);
        assertThat(reloaded.getAvailableQuantity()).isZero();
        assertThat(negativeRows).isZero();
        assertThat(bookingRepository.countByConcertId(concert.getId())).isEqualTo(successful.get());
        assertThat(bookingItemRepository.countByBookingConcertId(concert.getId())).isEqualTo(successful.get());
        assertThat(bookingStatusHistoryRepository.countByBookingConcertId(concert.getId()))
                .isEqualTo(successful.get());
    }
}
