package com.quyen.geekticket.domain.response.booking;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OperationBookingDetailResponse {

    private Long id;
    private String bookingCode;
    private Long userId;
    private String userEmail;
    private Long concertId;
    private String concertTitle;
    private BigDecimal totalAmount;
    private BigDecimal discountAmount;
    private BigDecimal finalAmount;
    private String status;
    private Boolean suspicious;
    private String voucherCode;
    private List<BookingItemResponse> items;
    private List<BookingStatusHistoryResponse> statusHistories;
    private Instant createdAt;
    private Instant updatedAt;
}
