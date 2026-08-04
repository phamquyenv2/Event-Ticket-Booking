package com.quyen.geekticket.domain.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import jakarta.validation.constraints.Size;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateBookingRequest {

    @NotNull(message = "Concert ID is required")
    private Long concertId;

    @NotEmpty(message = "Booking items must not be empty")
    @Valid
    private List<BookingItemRequest> items;

    @Size(max = 50, message = "Voucher code must not exceed 50 characters")
    private String voucherCode;
}

