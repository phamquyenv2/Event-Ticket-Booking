package com.quyen.geekticket.domain.response.booking;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingItemResponse {

    private Long id;
    private Long ticketCategoryId;
    private String ticketCategoryName;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
}
