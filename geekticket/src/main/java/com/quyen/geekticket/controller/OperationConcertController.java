package com.quyen.geekticket.controller;

import com.quyen.geekticket.domain.dto.ApiResponse;
import com.quyen.geekticket.domain.request.CreateConcertRequest;
import com.quyen.geekticket.domain.request.CreateTicketCategoryRequest;
import com.quyen.geekticket.domain.response.concert.ConcertDetailResponse;
import com.quyen.geekticket.domain.response.concert.TicketCategoryResponse;
import com.quyen.geekticket.service.OperationConcertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/operations/concerts")
@RequiredArgsConstructor
@Tag(name = "Operations - Concerts", description = "Operator/Admin concert management APIs")
public class OperationConcertController {

    private final OperationConcertService operationConcertService;

    @PostMapping
    @Operation(summary = "Create a new concert", description = "Creates a DRAFT concert. Requires X-Operator-Id header.")
    public ResponseEntity<ApiResponse<ConcertDetailResponse>> createConcert(
            @Valid @RequestBody CreateConcertRequest request,
            @RequestHeader("X-Operator-Id") @Parameter(description = "Operator user ID") Long operatorId) {

        ConcertDetailResponse detail = operationConcertService.createConcert(request, operatorId);

        ApiResponse<ConcertDetailResponse> response = new ApiResponse<>();
        response.setStatusCode(201);
        response.setMessage("Concert created successfully");
        response.setData(detail);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{concertId}/ticket-categories")
    @Operation(summary = "Add ticket category to concert", description = "Adds a ticket category. availableQuantity = totalQuantity initially.")
    public ResponseEntity<ApiResponse<TicketCategoryResponse>> addTicketCategory(
            @PathVariable Long concertId,
            @Valid @RequestBody CreateTicketCategoryRequest request,
            @RequestHeader("X-Operator-Id") @Parameter(description = "Operator user ID") Long operatorId) {

        TicketCategoryResponse category = operationConcertService.addTicketCategory(concertId, request, operatorId);

        ApiResponse<TicketCategoryResponse> response = new ApiResponse<>();
        response.setStatusCode(201);
        response.setMessage("Ticket category created successfully");
        response.setData(category);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{concertId}/publish")
    @Operation(summary = "Publish a concert", description = "Publishes a DRAFT concert. Requires at least one ticket category.")
    public ResponseEntity<ApiResponse<ConcertDetailResponse>> publishConcert(
            @PathVariable Long concertId,
            @RequestHeader("X-Operator-Id") @Parameter(description = "Operator user ID") Long operatorId) {

        ConcertDetailResponse detail = operationConcertService.publishConcert(concertId, operatorId);

        ApiResponse<ConcertDetailResponse> response = new ApiResponse<>();
        response.setStatusCode(200);
        response.setMessage("Concert published successfully");
        response.setData(detail);
        return ResponseEntity.ok(response);
    }
}
