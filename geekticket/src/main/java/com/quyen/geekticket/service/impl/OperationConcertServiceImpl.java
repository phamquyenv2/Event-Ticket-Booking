package com.quyen.geekticket.service.impl;

import com.quyen.geekticket.domain.entity.Concert;
import com.quyen.geekticket.domain.entity.TicketCategory;
import com.quyen.geekticket.domain.entity.User;
import com.quyen.geekticket.domain.request.CreateConcertRequest;
import com.quyen.geekticket.domain.request.CreateTicketCategoryRequest;
import com.quyen.geekticket.domain.response.concert.ConcertDetailResponse;
import com.quyen.geekticket.domain.response.concert.TicketCategoryResponse;
import com.quyen.geekticket.repository.ConcertRepository;
import com.quyen.geekticket.repository.TicketCategoryRepository;
import com.quyen.geekticket.repository.UserRepository;
import com.quyen.geekticket.service.OperationConcertService;
import com.quyen.geekticket.util.constant.ConcertStatus;
import com.quyen.geekticket.util.constant.UserRole;
import com.quyen.geekticket.util.error.BusinessException;
import com.quyen.geekticket.util.error.ErrorCode;
import com.quyen.geekticket.util.error.ResourceNotFoundException;
import com.quyen.geekticket.util.mapper.ConcertMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OperationConcertServiceImpl implements OperationConcertService {

    private final ConcertRepository concertRepository;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final UserRepository userRepository;
    private final ConcertMapper concertMapper;

    @Override
    @Transactional
    public ConcertDetailResponse createConcert(CreateConcertRequest request, Long operatorId) {
        validateOperator(operatorId);
        validateConcertTimes(request);

        Concert concert = concertMapper.toEntity(request);
        concert = concertRepository.save(concert);
        return concertMapper.toDetail(concert);
    }

    @Override
    @Transactional
    public TicketCategoryResponse addTicketCategory(Long concertId,
                                                     CreateTicketCategoryRequest request,
                                                     Long operatorId) {
        validateOperator(operatorId);

        Concert concert = concertRepository.findById(concertId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CONCERT_NOT_FOUND));

        if (concert.getStatus() == ConcertStatus.CANCELLED || concert.getStatus() == ConcertStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.CONCERT_NOT_PUBLISHABLE,
                    "Cannot add ticket categories to a " + concert.getStatus() + " concert");
        }

        TicketCategory ticketCategory = concertMapper.toEntity(request, concert);
        ticketCategory = ticketCategoryRepository.save(ticketCategory);
        return concertMapper.toTicketCategoryResponse(ticketCategory);
    }

    @Override
    @Transactional
    public ConcertDetailResponse publishConcert(Long concertId, Long operatorId) {
        validateOperator(operatorId);

        Concert concert = concertRepository.findById(concertId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CONCERT_NOT_FOUND));

        if (concert.getStatus() != ConcertStatus.DRAFT) {
            throw new BusinessException(ErrorCode.CONCERT_NOT_PUBLISHABLE,
                    "Only DRAFT concerts can be published. Current status: " + concert.getStatus());
        }

        if (concert.getTicketCategories() == null || concert.getTicketCategories().isEmpty()) {
            throw new BusinessException(ErrorCode.CONCERT_NOT_PUBLISHABLE,
                    "Concert must have at least one ticket category before publishing");
        }

        concert.setStatus(ConcertStatus.PUBLISHED);
        concert = concertRepository.save(concert);
        return concertMapper.toDetail(concert);
    }

    private void validateOperator(Long operatorId) {
        User operator = userRepository.findById(operatorId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.OPERATOR_NOT_FOUND));

        if (operator.getRole() != UserRole.OPERATOR && operator.getRole() != UserRole.ADMIN) {
            throw new BusinessException(ErrorCode.OPERATOR_NOT_FOUND,
                    "User does not have OPERATOR or ADMIN role");
        }
    }

    private void validateConcertTimes(CreateConcertRequest request) {
        if (!request.getSaleEndTime().isAfter(request.getSaleStartTime())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Sale end time must be after sale start time");
        }
        if (!request.getConcertStartTime().isAfter(request.getSaleStartTime())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Concert start time must be after sale start time");
        }
    }
}
