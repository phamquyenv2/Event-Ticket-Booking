package com.quyen.geekticket.service;

import com.quyen.geekticket.domain.request.CreateConcertRequest;
import com.quyen.geekticket.domain.request.CreateTicketCategoryRequest;
import com.quyen.geekticket.domain.response.concert.ConcertDetailResponse;
import com.quyen.geekticket.domain.response.concert.TicketCategoryResponse;

public interface OperationConcertService {

    ConcertDetailResponse createConcert(CreateConcertRequest request, Long operatorId);

    TicketCategoryResponse addTicketCategory(Long concertId, CreateTicketCategoryRequest request, Long operatorId);

    ConcertDetailResponse publishConcert(Long concertId, Long operatorId);
}
