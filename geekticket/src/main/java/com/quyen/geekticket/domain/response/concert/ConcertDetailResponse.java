package com.quyen.geekticket.domain.response.concert;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConcertDetailResponse {

    private Long id;
    private String title;
    private String description;
    private String venue;
    private String status;
    private Integer totalCapacity;
    private Instant saleStartTime;
    private Instant saleEndTime;
    private Instant concertStartTime;
    private List<TicketCategoryResponse> ticketCategories;
    private Instant createdAt;
    private Instant updatedAt;
}
