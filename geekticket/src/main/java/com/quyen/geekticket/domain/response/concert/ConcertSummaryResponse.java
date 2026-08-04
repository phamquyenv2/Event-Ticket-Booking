package com.quyen.geekticket.domain.response.concert;

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
public class ConcertSummaryResponse {

    private Long id;
    private String title;
    private String venue;
    private String status;
    private Integer totalCapacity;
    private Instant saleStartTime;
    private Instant saleEndTime;
    private Instant concertStartTime;
    private Instant createdAt;
}
