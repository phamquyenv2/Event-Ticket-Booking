package com.quyen.geekticket.domain;

import com.quyen.geekticket.domain.entity.Booking;
import com.quyen.geekticket.domain.entity.BookingItem;
import com.quyen.geekticket.domain.entity.TicketCategory;
import com.quyen.geekticket.util.constant.BookingStatus;
import com.quyen.geekticket.util.error.BusinessException;
import com.quyen.geekticket.util.error.ErrorCode;
import com.quyen.geekticket.util.generator.BookingCodeGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingDomainTest {

    private Booking booking;

    @BeforeEach
    void setUp() {
        booking = Booking.builder()
                .id(100L)
                .bookingCode("BK-20260804-ABC123")
                .status(BookingStatus.RESERVED)
                .bookingItems(new ArrayList<>())
                .statusHistories(new ArrayList<>())
                .build();
    }
    @Nested
    @DisplayName("Allowed Status Transitions")
    class AllowedTransitions {

        @Test
        @DisplayName("RESERVED -> CONFIRMED should succeed and record history")
        void confirm_fromReserved_succeeds() {
            booking.confirm("customer01", "Payment successful");

            assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
            assertThat(booking.getStatusHistories()).hasSize(1);
            assertThat(booking.getStatusHistories().get(0).getFromStatus()).isEqualTo("RESERVED");
            assertThat(booking.getStatusHistories().get(0).getToStatus()).isEqualTo("CONFIRMED");
            assertThat(booking.getStatusHistories().get(0).getChangedBy()).isEqualTo("customer01");
            assertThat(booking.getStatusHistories().get(0).getReason()).isEqualTo("Payment successful");
        }

        @Test
        @DisplayName("RESERVED -> CANCELLED (Customer) should succeed and record history")
        void cancel_fromReservedByCustomer_succeeds() {
            booking.cancel("customer01", "User cancelled", false);

            assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
            assertThat(booking.getStatusHistories()).hasSize(1);
            assertThat(booking.getStatusHistories().get(0).getToStatus()).isEqualTo("CANCELLED");
        }

        @Test
        @DisplayName("RESERVED -> CANCELLED (Operator) should succeed")
        void cancel_fromReservedByOperator_succeeds() {
            booking.cancel("operator01", "Operator cancelled", true);

            assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
            assertThat(booking.getStatusHistories()).hasSize(1);
        }

        @Test
        @DisplayName("CONFIRMED -> CANCELLED (Operator) should succeed")
        void cancel_fromConfirmedByOperator_succeeds() {
            booking.setStatus(BookingStatus.CONFIRMED);
            booking.cancel("operator01", "Concert cancelled", true);

            assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
            assertThat(booking.getStatusHistories()).hasSize(1);
            assertThat(booking.getStatusHistories().get(0).getFromStatus()).isEqualTo("CONFIRMED");
            assertThat(booking.getStatusHistories().get(0).getToStatus()).isEqualTo("CANCELLED");
        }

        @Test
        @DisplayName("RESERVED -> EXPIRED should succeed")
        void expire_fromReserved_succeeds() {
            booking.expire("Reservation timeout");

            assertThat(booking.getStatus()).isEqualTo(BookingStatus.EXPIRED);
            assertThat(booking.getStatusHistories()).hasSize(1);
            assertThat(booking.getStatusHistories().get(0).getChangedBy()).isEqualTo("SYSTEM");
        }

        @Test
        @DisplayName("RESERVED -> FAILED should succeed")
        void markFailed_fromReserved_succeeds() {
            booking.markFailed("Payment gateway error");

            assertThat(booking.getStatus()).isEqualTo(BookingStatus.FAILED);
            assertThat(booking.getStatusHistories()).hasSize(1);
            assertThat(booking.getStatusHistories().get(0).getToStatus()).isEqualTo("FAILED");
        }
    }

    @Nested
    @DisplayName("Forbidden Status Transitions")
    class ForbiddenTransitions {

        @Test
        @DisplayName("CONFIRMED -> CANCELLED by Customer should fail")
        void cancel_fromConfirmedByCustomer_throwsException() {
            booking.setStatus(BookingStatus.CONFIRMED);

            assertThatThrownBy(() -> booking.cancel("customer01", "Customer attempt", false))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Cannot cancel booking from status CONFIRMED");
        }

        @Test
        @DisplayName("CONFIRMED -> CONFIRMED should fail")
        void confirm_fromConfirmed_throwsException() {
            booking.setStatus(BookingStatus.CONFIRMED);

            assertThatThrownBy(() -> booking.confirm("customer01", "Re-confirm"))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("CANCELLED -> CONFIRMED should fail")
        void confirm_fromCancelled_throwsException() {
            booking.setStatus(BookingStatus.CANCELLED);

            assertThatThrownBy(() -> booking.confirm("customer01", "Un-cancel"))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("EXPIRED -> CONFIRMED should fail")
        void confirm_fromExpired_throwsException() {
            booking.setStatus(BookingStatus.EXPIRED);

            assertThatThrownBy(() -> booking.confirm("customer01", "Try confirm after expire"))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("FAILED -> CONFIRMED should fail")
        void confirm_fromFailed_throwsException() {
            booking.setStatus(BookingStatus.FAILED);

            assertThatThrownBy(() -> booking.confirm("customer01", "Try confirm after fail"))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("CANCELLED -> EXPIRED should fail")
        void expire_fromCancelled_throwsException() {
            booking.setStatus(BookingStatus.CANCELLED);

            assertThatThrownBy(() -> booking.expire("Expire cancelled"))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("EXPIRED -> CANCELLED should fail")
        void cancel_fromExpired_throwsException() {
            booking.setStatus(BookingStatus.EXPIRED);

            assertThatThrownBy(() -> booking.cancel("customer01", "Cancel expired", false))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("FAILED -> CANCELLED should fail")
        void cancel_fromFailed_throwsException() {
            booking.setStatus(BookingStatus.FAILED);

            assertThatThrownBy(() -> booking.cancel("customer01", "Cancel failed", false))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("Price Calculations")
    class PriceCalculations {

        @Test
        @DisplayName("Multi-item subtotal should be calculated correctly")
        void calculateAmounts_multiItem_calculatesCorrectSubtotal() {
            TicketCategory vipCat = TicketCategory.builder().id(1L).name("VIP").build();
            TicketCategory stdCat = TicketCategory.builder().id(2L).name("Standard").build();

            BookingItem item1 = BookingItem.builder()
                    .ticketCategory(vipCat)
                    .quantity(2)
                    .unitPrice(new BigDecimal("2500000.00"))
                    .build();

            BookingItem item2 = BookingItem.builder()
                    .ticketCategory(stdCat)
                    .quantity(3)
                    .unitPrice(new BigDecimal("600000.00"))
                    .build();

            booking.setBookingItems(List.of(item1, item2));

            // totalAmount = (2 * 2.5M) + (3 * 0.6M) = 5.0M + 1.8M = 6.8M
            booking.calculateAmounts(BigDecimal.ZERO);

            assertThat(booking.getTotalAmount()).isEqualByComparingTo("6800000.00");
            assertThat(booking.getDiscountAmount()).isEqualByComparingTo("0.00");
            assertThat(booking.getFinalAmount()).isEqualByComparingTo("6800000.00");
        }

        @Test
        @DisplayName("Discount application should reduce final amount")
        void calculateAmounts_withDiscount_reducesFinalAmount() {
            BookingItem item = BookingItem.builder()
                    .quantity(2)
                    .unitPrice(new BigDecimal("1000000.00"))
                    .build();

            booking.setBookingItems(List.of(item));

            // total = 2.0M, discount = 300K -> final = 1.7M
            booking.calculateAmounts(new BigDecimal("300000.00"));

            assertThat(booking.getTotalAmount()).isEqualByComparingTo("2000000.00");
            assertThat(booking.getDiscountAmount()).isEqualByComparingTo("300000.00");
            assertThat(booking.getFinalAmount()).isEqualByComparingTo("1700000.00");
        }

        @Test
        @DisplayName("Final amount cannot be below zero even if discount exceeds total")
        void calculateAmounts_discountExceedsTotal_capsFinalAmountAtZero() {
            BookingItem item = BookingItem.builder()
                    .quantity(1)
                    .unitPrice(new BigDecimal("500000.00"))
                    .build();

            booking.setBookingItems(List.of(item));

            booking.calculateAmounts(new BigDecimal("1000000.00"));

            assertThat(booking.getTotalAmount()).isEqualByComparingTo("500000.00");
            assertThat(booking.getDiscountAmount()).isEqualByComparingTo("1000000.00");
            assertThat(booking.getFinalAmount()).isEqualByComparingTo("0.00");
        }
    }


    @Nested
    @DisplayName("Booking Code Generator")
    class CodeGenerator {

        private final BookingCodeGenerator generator = new BookingCodeGenerator();

        @Test
        @DisplayName("Code should match expected BK-YYYYMMDD-XXXXXX format")
        void generateCode_matchesExpectedFormat() {
            String code = generator.generateCode();

            assertThat(code).startsWith("BK-");
            assertThat(code).matches("^BK-\\d{8}-[A-Z0-9]{6}$");
        }

        @Test
        @DisplayName("Code generation should produce unique values across multiple calls")
        void generateCode_producesUniqueValues() {
            Set<String> generatedCodes = new HashSet<>();
            int count = 1000;

            for (int i = 0; i < count; i++) {
                generatedCodes.add(generator.generateCode());
            }

            assertThat(generatedCodes).hasSize(count);
        }
    }
}
