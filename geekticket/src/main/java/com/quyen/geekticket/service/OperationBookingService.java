package com.quyen.geekticket.service;

import com.quyen.geekticket.domain.dto.PageResponse;
import com.quyen.geekticket.domain.request.UpdateBookingStatusRequest;
import com.quyen.geekticket.domain.request.UpdateSuspiciousRequest;
import com.quyen.geekticket.domain.response.booking.OperationBookingDetailResponse;
import com.quyen.geekticket.domain.response.booking.OperationBookingResponse;
import com.quyen.geekticket.util.constant.BookingStatus;
import org.springframework.data.domain.Pageable;

import java.time.Instant;

public interface OperationBookingService {

    PageResponse<OperationBookingResponse> getBookings(Long operatorId,
                                                      BookingStatus status,
                                                      Long concertId,
                                                      Long userId,
                                                      Boolean suspicious,
                                                      Instant createdFrom,
                                                      Instant createdTo,
                                                      Pageable pageable);

    OperationBookingDetailResponse getBookingDetail(Long operatorId, Long bookingId);

    OperationBookingDetailResponse updateBookingStatus(Long operatorId,
                                                       Long bookingId,
                                                       UpdateBookingStatusRequest request);

    OperationBookingDetailResponse updateBookingSuspicious(Long operatorId,
                                                           Long bookingId,
                                                           UpdateSuspiciousRequest request);
}
