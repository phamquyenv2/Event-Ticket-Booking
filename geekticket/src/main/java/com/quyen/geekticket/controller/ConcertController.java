package com.quyen.geekticket.controller;

import com.quyen.geekticket.domain.dto.ApiResponse;
import com.quyen.geekticket.domain.response.concert.ConcertDetailResponse;
import com.quyen.geekticket.domain.response.concert.ConcertSummaryResponse;
import com.quyen.geekticket.service.ConcertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/concerts")
@RequiredArgsConstructor
@Tag(name = "Customer - Concerts", description = "Customer-facing concert APIs")
public class ConcertController {

    private final ConcertService concertService;

    @GetMapping
    @Operation(summary = "List published concerts", description = "Returns paginated list of PUBLISHED concerts only")
    public ResponseEntity<ApiResponse<List<ConcertSummaryResponse>>> getPublishedConcerts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        var pageable = PageRequest.of(page, size, Sort.by("concertStartTime").ascending());
        List<ConcertSummaryResponse> concerts = concertService.getPublishedConcerts(pageable);

        ApiResponse<List<ConcertSummaryResponse>> response = new ApiResponse<>();
        response.setStatusCode(200);
        response.setMessage("Success");
        response.setData(concerts);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{concertId}")
    @Operation(summary = "Get concert detail", description = "Returns concert detail with ticket categories")
    public ResponseEntity<ApiResponse<ConcertDetailResponse>> getConcertDetail(
            @PathVariable Long concertId) {

        ConcertDetailResponse detail = concertService.getConcertDetail(concertId);

        ApiResponse<ConcertDetailResponse> response = new ApiResponse<>();
        response.setStatusCode(200);
        response.setMessage("Success");
        response.setData(detail);
        return ResponseEntity.ok(response);
    }
}
