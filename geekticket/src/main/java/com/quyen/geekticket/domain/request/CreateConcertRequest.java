package com.quyen.geekticket.domain.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
public class CreateConcertRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 150, message = "Title must not exceed 150 characters")
    private String title;

    private String description;

    @NotBlank(message = "Venue is required")
    @Size(max = 150, message = "Venue must not exceed 150 characters")
    private String venue;

    @NotNull(message = "Total capacity is required")
    @Min(value = 1, message = "Total capacity must be at least 1")
    private Integer totalCapacity;

    @NotNull(message = "Sale start time is required")
    private Instant saleStartTime;

    @NotNull(message = "Sale end time is required")
    private Instant saleEndTime;

    @NotNull(message = "Concert start time is required")
    private Instant concertStartTime;
}
