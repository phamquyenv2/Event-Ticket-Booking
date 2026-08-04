package com.quyen.geekticket.controller;

import com.quyen.geekticket.domain.dto.ApiResponse;
import com.quyen.geekticket.domain.dto.PageResponse;
import com.quyen.geekticket.domain.request.UpdateBookingStatusRequest;
import com.quyen.geekticket.domain.request.UpdateSuspiciousRequest;
import com.quyen.geekticket.domain.response.booking.OperationBookingDetailResponse;
import com.quyen.geekticket.domain.response.booking.OperationBookingResponse;
import com.quyen.geekticket.service.OperationBookingService;
import com.quyen.geekticket.util.constant.BookingStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/operations/bookings")
@RequiredArgsConstructor
@Tag(name = "Operations - Bookings", description = "Operator/Admin booking management APIs")
public class OperationBookingController {

    private final OperationBookingService operationBookingService;

    @GetMapping
    @Operation(summary = "List bookings with filters", description = "Returns paginated list of bookings with optional filters")
    public ResponseEntity<ApiResponse<PageResponse<OperationBookingResponse>>> getBookings(
            @RequestHeader("X-Operator-Id") @Parameter(description = "Operator user ID") Long operatorId,
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(required = false) Long concertId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Boolean suspicious,
            @RequestParam(required = false) Instant createdFrom,
            @RequestParam(required = false) Instant createdTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {

        Sort sort = direction.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        PageResponse<OperationBookingResponse> result = operationBookingService.getBookings(
                operatorId, status, concertId, userId, suspicious, createdFrom, createdTo, pageable);

        ApiResponse<PageResponse<OperationBookingResponse>> response =
                new ApiResponse<>(HttpStatus.OK.value(), null, "Bookings retrieved successfully", result);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{bookingId}")
    @Operation(summary = "Get booking detail", description = "Retrieves detailed booking information with items and audit status history")
    public ResponseEntity<ApiResponse<OperationBookingDetailResponse>> getBookingDetail(
            @RequestHeader("X-Operator-Id") @Parameter(description = "Operator user ID") Long operatorId,
            @PathVariable Long bookingId) {

        OperationBookingDetailResponse detail = operationBookingService.getBookingDetail(operatorId, bookingId);

        ApiResponse<OperationBookingDetailResponse> response =
                new ApiResponse<>(HttpStatus.OK.value(), null, "Booking detail retrieved successfully", detail);

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{bookingId}/status")
    @Operation(summary = "Update booking status manually", description = "Manually changes booking status with required non-blank reason and records status history audit")
    public ResponseEntity<ApiResponse<OperationBookingDetailResponse>> updateBookingStatus(
            @RequestHeader("X-Operator-Id") @Parameter(description = "Operator user ID") Long operatorId,
            @PathVariable Long bookingId,
            @Valid @RequestBody UpdateBookingStatusRequest request) {

        OperationBookingDetailResponse detail = operationBookingService.updateBookingStatus(operatorId, bookingId, request);

        ApiResponse<OperationBookingDetailResponse> response =
                new ApiResponse<>(HttpStatus.OK.value(), null, "Booking status updated successfully", detail);

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{bookingId}/suspicious")
    @Operation(summary = "Flag/unflag booking as suspicious", description = "Sets suspicious flag on booking with reason")
    public ResponseEntity<ApiResponse<OperationBookingDetailResponse>> updateBookingSuspicious(
            @RequestHeader("X-Operator-Id") @Parameter(description = "Operator user ID") Long operatorId,
            @PathVariable Long bookingId,
            @Valid @RequestBody UpdateSuspiciousRequest request) {

        OperationBookingDetailResponse detail = operationBookingService.updateBookingSuspicious(operatorId, bookingId, request);

        ApiResponse<OperationBookingDetailResponse> response =
                new ApiResponse<>(HttpStatus.OK.value(), null, "Suspicious flag updated successfully", detail);

        return ResponseEntity.ok(response);
    }
}
