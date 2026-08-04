package com.quyen.geekticket.controller;

import com.quyen.geekticket.domain.request.CancelBookingRequest;
import com.quyen.geekticket.domain.request.CreateBookingRequest;
import com.quyen.geekticket.domain.response.booking.BookingResponse;
import com.quyen.geekticket.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
@Tag(name = "Customer - Bookings", description = "Customer booking APIs")
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    @Operation(
            summary = "Create a booking",
            description = "Creates one booking per user and Idempotency-Key. Replays return the persisted booking."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Booking created or original booking replayed"),
            @ApiResponse(responseCode = "400", description = "Missing/invalid idempotency key or invalid request"),
            @ApiResponse(responseCode = "409", description = "Idempotency key conflict or unavailable inventory")
    })
    public ResponseEntity<com.quyen.geekticket.domain.dto.ApiResponse<BookingResponse>> createBooking(
            @RequestHeader("X-User-Id") @Parameter(description = "Customer user ID") Long userId,
            @RequestHeader(value = "Idempotency-Key", required = false)
            @Parameter(
                    description = "Required UUID identifying this booking attempt within the user scope",
                    required = true,
                    example = "2f43fb4d-0d70-4d85-8b11-17c083bd67e3") String idempotencyKey,
            @Valid @RequestBody CreateBookingRequest request) {

        BookingResponse booking = bookingService.createBooking(userId, idempotencyKey, request);
        com.quyen.geekticket.domain.dto.ApiResponse<BookingResponse> response = new com.quyen.geekticket.domain.dto.ApiResponse<>(
                HttpStatus.CREATED.value(),
                null,
                "Booking created successfully",
                booking);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{bookingCode}")
    @Operation(
            summary = "Get booking by booking code",
            description = "Retrieves booking details for the authenticated owner by bookingCode"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Booking retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Booking not found or not owned by user")
    })
    public ResponseEntity<com.quyen.geekticket.domain.dto.ApiResponse<BookingResponse>> getBookingByCode(
            @RequestHeader("X-User-Id") @Parameter(description = "Customer user ID") Long userId,
            @PathVariable("bookingCode") String bookingCode) {

        BookingResponse booking = bookingService.getBookingByCode(userId, bookingCode);
        com.quyen.geekticket.domain.dto.ApiResponse<BookingResponse> response = new com.quyen.geekticket.domain.dto.ApiResponse<>(
                HttpStatus.OK.value(),
                null,
                "Booking retrieved successfully",
                booking);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{bookingId}/cancel")
    @Operation(
            summary = "Cancel booking",
            description = "Cancels a RESERVED booking, restoring inventory and voucher usage"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Booking cancelled successfully"),
            @ApiResponse(responseCode = "404", description = "Booking not found or not owned by user"),
            @ApiResponse(responseCode = "409", description = "Booking is not in RESERVED status")
    })
    public ResponseEntity<com.quyen.geekticket.domain.dto.ApiResponse<BookingResponse>> cancelBooking(
            @RequestHeader("X-User-Id") @Parameter(description = "Customer user ID") Long userId,
            @PathVariable("bookingId") Long bookingId,
            @RequestBody(required = false) CancelBookingRequest request) {

        BookingResponse booking = bookingService.cancelBooking(userId, bookingId, request);
        com.quyen.geekticket.domain.dto.ApiResponse<BookingResponse> response = new com.quyen.geekticket.domain.dto.ApiResponse<>(
                HttpStatus.OK.value(),
                null,
                "Booking cancelled successfully",
                booking);
        return ResponseEntity.ok(response);
    }
}
