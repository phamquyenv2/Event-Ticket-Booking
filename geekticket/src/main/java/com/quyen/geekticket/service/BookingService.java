package com.quyen.geekticket.service;

import com.quyen.geekticket.domain.request.CancelBookingRequest;
import com.quyen.geekticket.domain.request.CreateBookingRequest;
import com.quyen.geekticket.domain.response.booking.BookingResponse;

public interface BookingService {

    BookingResponse createBooking(Long userId, String idempotencyKey, CreateBookingRequest request);

    BookingResponse getBookingByCode(Long userId, String bookingCode);

    BookingResponse cancelBooking(Long userId, Long bookingId, CancelBookingRequest request);
}
