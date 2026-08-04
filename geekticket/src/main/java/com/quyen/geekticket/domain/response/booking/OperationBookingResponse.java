package com.quyen.geekticket.domain.response.booking;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OperationBookingResponse {

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
    private Instant createdAt;
}
