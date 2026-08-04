package com.quyen.geekticket.util.generator;

import com.quyen.geekticket.domain.request.BookingItemRequest;
import com.quyen.geekticket.domain.request.CreateBookingRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RequestHashGeneratorTest {

    private final RequestHashGenerator generator = new RequestHashGenerator();

    @Test
    void generate_sameBusinessRequestWithDifferentItemOrder_returnsSameHash() {
        CreateBookingRequest first = request(1L, null,
                item(20L, 2),
                item(10L, 1));
        CreateBookingRequest reordered = request(1L, null,
                item(10L, 1),
                item(20L, 2));

        assertThat(generator.generate(first))
                .isEqualTo(generator.generate(reordered))
                .hasSize(64);
    }

    @Test
    void generate_quantityChanges_returnsDifferentHash() {
        assertThat(generator.generate(request(1L, null, item(10L, 1))))
                .isNotEqualTo(generator.generate(request(1L, null, item(10L, 2))));
    }

    @Test
    void generate_concertChanges_returnsDifferentHash() {
        assertThat(generator.generate(request(1L, null, item(10L, 1))))
                .isNotEqualTo(generator.generate(request(2L, null, item(10L, 1))));
    }

    @Test
    void generate_voucherCodeCaseAndSpacing_returnsSameHash() {
        CreateBookingRequest uppercase = request(1L, "FLASH20", item(10L, 1));
        CreateBookingRequest lowercaseWithSpace = request(1L, " flash20 ", item(10L, 1));

        assertThat(generator.generate(uppercase))
                .isEqualTo(generator.generate(lowercaseWithSpace));
    }

    @Test
    void generate_differentVoucherCode_returnsDifferentHash() {
        CreateBookingRequest voucherA = request(1L, "FLASH20", item(10L, 1));
        CreateBookingRequest voucherB = request(1L, "FLASH30", item(10L, 1));
        CreateBookingRequest noVoucher = request(1L, null, item(10L, 1));

        assertThat(generator.generate(voucherA))
                .isNotEqualTo(generator.generate(voucherB))
                .isNotEqualTo(generator.generate(noVoucher));
    }

    private CreateBookingRequest request(Long concertId, String voucherCode, BookingItemRequest... items) {
        return CreateBookingRequest.builder()
                .concertId(concertId)
                .voucherCode(voucherCode)
                .items(List.of(items))
                .build();
    }

    private BookingItemRequest item(Long categoryId, int quantity) {
        return BookingItemRequest.builder()
                .ticketCategoryId(categoryId)
                .quantity(quantity)
                .build();
    }
}
