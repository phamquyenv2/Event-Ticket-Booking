package com.quyen.geekticket.service.impl;

import com.quyen.geekticket.domain.entity.Concert;
import com.quyen.geekticket.domain.response.concert.ConcertDetailResponse;
import com.quyen.geekticket.domain.response.concert.ConcertSummaryResponse;
import com.quyen.geekticket.repository.ConcertRepository;
import com.quyen.geekticket.service.ConcertService;
import com.quyen.geekticket.util.constant.ConcertStatus;
import com.quyen.geekticket.util.error.ErrorCode;
import com.quyen.geekticket.util.error.ResourceNotFoundException;
import com.quyen.geekticket.util.mapper.ConcertMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConcertServiceImpl implements ConcertService {

    private final ConcertRepository concertRepository;
    private final ConcertMapper concertMapper;

    @Override
    public List<ConcertSummaryResponse> getPublishedConcerts(Pageable pageable) {
        Page<Concert> page = concertRepository.findByStatus(ConcertStatus.PUBLISHED, pageable);
        return page.getContent().stream()
                .map(concertMapper::toSummary)
                .toList();
    }

    @Override
    public long countPublishedConcerts() {
        return concertRepository.findByStatus(ConcertStatus.PUBLISHED, Pageable.unpaged())
                .getTotalElements();
    }

    @Override
    public ConcertDetailResponse getConcertDetail(Long concertId) {
        Concert concert = concertRepository.findById(concertId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CONCERT_NOT_FOUND));
        return concertMapper.toDetail(concert);
    }
}
