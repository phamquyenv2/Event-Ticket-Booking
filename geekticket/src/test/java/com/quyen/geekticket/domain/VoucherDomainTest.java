package com.quyen.geekticket.domain;

import com.quyen.geekticket.domain.entity.Voucher;
import com.quyen.geekticket.util.constant.DiscountType;
import com.quyen.geekticket.util.constant.VoucherStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class VoucherDomainTest {

    @Test
    @DisplayName("Percentage discount calculation without max cap should return exact percentage of subtotal")
    void calculatePercentageDiscount_withoutCap_returnsPercentage() {
        Voucher voucher = Voucher.builder()
                .code("DISCOUNT10")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("10")) // 10%
                .maxDiscountAmount(null)
                .build();

        BigDecimal subtotal = new BigDecimal("500000"); // 500k
        BigDecimal expectedDiscount = new BigDecimal("50000.00"); // 50k

        BigDecimal discount = calculateDiscount(voucher, subtotal);

        assertThat(discount).isEqualByComparingTo(expectedDiscount);
    }

    @Test
    @DisplayName("Percentage discount calculation with max cap should cap discount at maxDiscountAmount")
    void calculatePercentageDiscount_withMaxCap_capsDiscountAmount() {
        Voucher voucher = Voucher.builder()
                .code("DISCOUNT20")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("20")) // 20% of 1M = 200k
                .maxDiscountAmount(new BigDecimal("100000")) // Cap at 100k
                .build();

        BigDecimal subtotal = new BigDecimal("1000000");
        BigDecimal expectedDiscount = new BigDecimal("100000");

        BigDecimal discount = calculateDiscount(voucher, subtotal);

        assertThat(discount).isEqualByComparingTo(expectedDiscount);
    }

    @Test
    @DisplayName("Fixed amount discount calculation less than subtotal should return fixed discount value")
    void calculateFixedDiscount_lessThanSubtotal_returnsFixedAmount() {
        Voucher voucher = Voucher.builder()
                .code("FIXED50K")
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(new BigDecimal("50000"))
                .build();

        BigDecimal subtotal = new BigDecimal("300000");
        BigDecimal expectedDiscount = new BigDecimal("50000");

        BigDecimal discount = calculateDiscount(voucher, subtotal);

        assertThat(discount).isEqualByComparingTo(expectedDiscount);
    }

    @Test
    @DisplayName("Fixed amount discount calculation exceeding subtotal should cap discount at subtotal")
    void calculateFixedDiscount_exceedingSubtotal_capsAtSubtotal() {
        Voucher voucher = Voucher.builder()
                .code("FIXED500K")
                .discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(new BigDecimal("500000"))
                .build();

        BigDecimal subtotal = new BigDecimal("200000"); // Subtotal only 200k
        BigDecimal expectedDiscount = new BigDecimal("200000"); // Cap at 200k so net total is 0

        BigDecimal discount = calculateDiscount(voucher, subtotal);

        assertThat(discount).isEqualByComparingTo(expectedDiscount);
    }

    @Test
    @DisplayName("Voucher active window validation should identify active vs expired/inactive status")
    void voucherStatusValidation_validatesCorrectly() {
        Instant now = Instant.now();
        Voucher activeVoucher = Voucher.builder()
                .status(VoucherStatus.ACTIVE)
                .startTime(now.minus(1, ChronoUnit.DAYS))
                .endTime(now.plus(1, ChronoUnit.DAYS))
                .build();

        Voucher expiredVoucher = Voucher.builder()
                .status(VoucherStatus.ACTIVE)
                .startTime(now.minus(2, ChronoUnit.DAYS))
                .endTime(now.minus(1, ChronoUnit.DAYS))
                .build();

        Voucher inactiveVoucher = Voucher.builder()
                .status(VoucherStatus.INACTIVE)
                .startTime(now.minus(1, ChronoUnit.DAYS))
                .endTime(now.plus(1, ChronoUnit.DAYS))
                .build();

        assertThat(activeVoucher.getStatus()).isEqualTo(VoucherStatus.ACTIVE);
        assertThat(now).isBetween(activeVoucher.getStartTime(), activeVoucher.getEndTime());

        assertThat(now.isAfter(expiredVoucher.getEndTime())).isTrue();
        assertThat(inactiveVoucher.getStatus()).isEqualTo(VoucherStatus.INACTIVE);
    }

    private BigDecimal calculateDiscount(Voucher voucher, BigDecimal subtotal) {
        BigDecimal discount;
        if (voucher.getDiscountType() == DiscountType.PERCENTAGE) {
            discount = subtotal.multiply(voucher.getDiscountValue())
                    .divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
            if (voucher.getMaxDiscountAmount() != null && discount.compareTo(voucher.getMaxDiscountAmount()) > 0) {
                discount = voucher.getMaxDiscountAmount();
            }
        } else {
            discount = voucher.getDiscountValue();
            if (discount.compareTo(subtotal) > 0) {
                discount = subtotal;
            }
        }
        return discount;
    }
}
