package com.quyen.geekticket.util.error;

public class InvalidBookingStatusException extends BusinessException {

    public InvalidBookingStatusException() {
        super(ErrorCode.INVALID_BOOKING_STATUS_TRANSITION);
    }

    public InvalidBookingStatusException(String message) {
        super(ErrorCode.INVALID_BOOKING_STATUS_TRANSITION, message);
    }
}
