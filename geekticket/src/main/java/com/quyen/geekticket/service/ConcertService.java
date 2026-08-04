package com.quyen.geekticket.service;

import com.quyen.geekticket.domain.response.concert.ConcertDetailResponse;
import com.quyen.geekticket.domain.response.concert.ConcertSummaryResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ConcertService {

    List<ConcertSummaryResponse> getPublishedConcerts(Pageable pageable);

    long countPublishedConcerts();

    ConcertDetailResponse getConcertDetail(Long concertId);
}
