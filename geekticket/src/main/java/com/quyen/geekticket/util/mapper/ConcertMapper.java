package com.quyen.geekticket.util.mapper;

import com.quyen.geekticket.domain.entity.Concert;
import com.quyen.geekticket.domain.entity.TicketCategory;
import com.quyen.geekticket.domain.request.CreateConcertRequest;
import com.quyen.geekticket.domain.request.CreateTicketCategoryRequest;
import com.quyen.geekticket.domain.response.concert.ConcertDetailResponse;
import com.quyen.geekticket.domain.response.concert.ConcertSummaryResponse;
import com.quyen.geekticket.domain.response.concert.TicketCategoryResponse;
import com.quyen.geekticket.util.constant.ConcertStatus;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ConcertMapper {

    public ConcertSummaryResponse toSummary(Concert concert) {
        return ConcertSummaryResponse.builder()
                .id(concert.getId())
                .title(concert.getTitle())
                .venue(concert.getVenue())
                .status(concert.getStatus().name())
                .totalCapacity(concert.getTotalCapacity())
                .saleStartTime(concert.getSaleStartTime())
                .saleEndTime(concert.getSaleEndTime())
                .concertStartTime(concert.getConcertStartTime())
                .createdAt(concert.getCreatedAt())
                .build();
    }

    public ConcertDetailResponse toDetail(Concert concert) {
        List<TicketCategoryResponse> categories = concert.getTicketCategories()
                .stream()
                .map(this::toTicketCategoryResponse)
                .toList();

        return ConcertDetailResponse.builder()
                .id(concert.getId())
                .title(concert.getTitle())
                .description(concert.getDescription())
                .venue(concert.getVenue())
                .status(concert.getStatus().name())
                .totalCapacity(concert.getTotalCapacity())
                .saleStartTime(concert.getSaleStartTime())
                .saleEndTime(concert.getSaleEndTime())
                .concertStartTime(concert.getConcertStartTime())
                .ticketCategories(categories)
                .createdAt(concert.getCreatedAt())
                .updatedAt(concert.getUpdatedAt())
                .build();
    }

    public TicketCategoryResponse toTicketCategoryResponse(TicketCategory tc) {
        return TicketCategoryResponse.builder()
                .id(tc.getId())
                .name(tc.getName())
                .description(tc.getDescription())
                .price(tc.getPrice())
                .totalQuantity(tc.getTotalQuantity())
                .availableQuantity(tc.getAvailableQuantity())
                .maxQuantityPerBooking(tc.getMaxQuantityPerBooking())
                .build();
    }

    public Concert toEntity(CreateConcertRequest request) {
        return Concert.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .venue(request.getVenue())
                .totalCapacity(request.getTotalCapacity())
                .status(ConcertStatus.DRAFT)
                .saleStartTime(request.getSaleStartTime())
                .saleEndTime(request.getSaleEndTime())
                .concertStartTime(request.getConcertStartTime())
                .build();
    }

    public TicketCategory toEntity(CreateTicketCategoryRequest request, Concert concert) {
        return TicketCategory.builder()
                .concert(concert)
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .totalQuantity(request.getTotalQuantity())
                .availableQuantity(request.getTotalQuantity())
                .maxQuantityPerBooking(request.getMaxQuantityPerBooking() != null
                        ? request.getMaxQuantityPerBooking()
                        : 4)
                .build();
    }
}
