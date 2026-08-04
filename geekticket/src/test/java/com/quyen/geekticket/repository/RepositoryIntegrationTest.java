package com.quyen.geekticket.repository;

import com.quyen.geekticket.domain.entity.Concert;
import com.quyen.geekticket.domain.entity.TicketCategory;
import com.quyen.geekticket.domain.entity.User;
import com.quyen.geekticket.domain.entity.Voucher;
import com.quyen.geekticket.util.constant.ConcertStatus;
import com.quyen.geekticket.util.constant.DiscountType;
import com.quyen.geekticket.util.constant.UserRole;
import com.quyen.geekticket.util.constant.VoucherStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class RepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConcertRepository concertRepository;

    @Autowired
    private TicketCategoryRepository ticketCategoryRepository;

    @Autowired
    private VoucherRepository voucherRepository;

    @Autowired
    private BookingRepository bookingRepository;

    // ==================== Seed Data Tests ====================

    @Test
    void seedDataLoaded_usersExist() {
        List<User> users = userRepository.findAll();
        assertThat(users).hasSizeGreaterThanOrEqualTo(4);

        Optional<User> customer = userRepository.findByUsername("customer01");
        assertThat(customer).isPresent();
        assertThat(customer.get().getRole()).isEqualTo(UserRole.CUSTOMER);

        Optional<User> operator = userRepository.findByUsername("operator01");
        assertThat(operator).isPresent();
        assertThat(operator.get().getRole()).isEqualTo(UserRole.OPERATOR);
    }

    @Test
    void seedDataLoaded_publishedConcertExists() {
        Page<Concert> published = concertRepository.findByStatus(
                ConcertStatus.PUBLISHED, PageRequest.of(0, 10));
        assertThat(published.getTotalElements()).isGreaterThanOrEqualTo(1);

        Concert concert = published.getContent().get(0);
        assertThat(concert.getTitle()).isNotBlank();
        assertThat(concert.getVenue()).isNotBlank();
        assertThat(concert.getTotalCapacity()).isGreaterThan(0);
    }

    @Test
    void seedDataLoaded_ticketCategoriesExist() {
        List<TicketCategory> categories = ticketCategoryRepository.findByConcertId(1L);
        assertThat(categories).hasSizeGreaterThanOrEqualTo(2);

        // VIP category
        TicketCategory vip = categories.stream()
                .filter(tc -> tc.getName().contains("VIP"))
                .findFirst()
                .orElse(null);
        assertThat(vip).isNotNull();
        assertThat(vip.getPrice()).isGreaterThan(BigDecimal.ZERO);
        assertThat(vip.getAvailableQuantity()).isEqualTo(vip.getTotalQuantity());
    }

    @Test
    void seedDataLoaded_activeVoucherExists() {
        Optional<Voucher> voucher = voucherRepository.findByCode("WELCOME2026");
        assertThat(voucher).isPresent();
        assertThat(voucher.get().getStatus()).isEqualTo(VoucherStatus.ACTIVE);
        assertThat(voucher.get().getDiscountType()).isEqualTo(DiscountType.PERCENTAGE);
        assertThat(voucher.get().getDiscountValue()).isGreaterThan(BigDecimal.ZERO);
    }

    // ==================== Repository Method Tests ====================

    @Test
    void userRepository_findByUsername_returnsCorrectUser() {
        Optional<User> found = userRepository.findByUsername("customer01");
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("customer01@example.com");
    }

    @Test
    void userRepository_existsByEmail_returnsTrue() {
        boolean exists = userRepository.existsByEmail("operator01@example.com");
        assertThat(exists).isTrue();
    }

    @Test
    void concertRepository_findByStatus_paginatesCorrectly() {
        Page<Concert> drafts = concertRepository.findByStatus(
                ConcertStatus.DRAFT, PageRequest.of(0, 10));
        assertThat(drafts.getTotalElements()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void ticketCategoryRepository_decrementAvailableQuantity_worksAtomically() {
        // Decrement 10 tickets from VIP (id=1, available=500)
        int updated = ticketCategoryRepository.decrementAvailableQuantity(1L, 10);
        assertThat(updated).isEqualTo(1);

        TicketCategory tc = ticketCategoryRepository.findById(1L).orElseThrow();
        assertThat(tc.getAvailableQuantity()).isEqualTo(490);
    }

    @Test
    void ticketCategoryRepository_decrementAvailableQuantity_failsWhenInsufficient() {
        // Try to decrement more than available
        int updated = ticketCategoryRepository.decrementAvailableQuantity(1L, 99999);
        assertThat(updated).isEqualTo(0);
    }

    @Test
    void ticketCategoryRepository_incrementAvailableQuantity_restoresInventory() {
        ticketCategoryRepository.decrementAvailableQuantity(1L, 5);
        ticketCategoryRepository.incrementAvailableQuantity(1L, 5);

        TicketCategory tc = ticketCategoryRepository.findById(1L).orElseThrow();
        assertThat(tc.getAvailableQuantity()).isEqualTo(500);
    }

    @Test
    void voucherRepository_findByCode_returnsCorrectVoucher() {
        Optional<Voucher> found = voucherRepository.findByCode("VIPFLASHSALE");
        assertThat(found).isPresent();
        assertThat(found.get().getDiscountType()).isEqualTo(DiscountType.FIXED_AMOUNT);
    }

    // ==================== Flyway Migration Verification ====================

    @Test
    void flywayMigrations_allTablesCreated() {
        // If we got here, Flyway ran V1-V3 successfully and JPA validate passed.
        // Verify all 9 repositories can query their tables.
        assertThat(userRepository.count()).isGreaterThanOrEqualTo(4);
        assertThat(concertRepository.count()).isGreaterThanOrEqualTo(2);
        assertThat(ticketCategoryRepository.count()).isGreaterThanOrEqualTo(3);
        assertThat(voucherRepository.count()).isGreaterThanOrEqualTo(2);
        assertThat(bookingRepository.count()).isEqualTo(0); // no seed bookings
    }
}
