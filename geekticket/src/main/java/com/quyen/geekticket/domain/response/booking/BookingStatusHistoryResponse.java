package com.quyen.geekticket.domain.response.booking;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingStatusHistoryResponse {

    private Long id;
    private String fromStatus;
    private String toStatus;
    private String changedBy;
    private String reason;
    private Instant createdAt;
}
