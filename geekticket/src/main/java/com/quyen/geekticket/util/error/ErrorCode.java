package com.quyen.geekticket.util.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Concert
    CONCERT_NOT_FOUND(HttpStatus.NOT_FOUND, "Concert not found"),
    CONCERT_NOT_ON_SALE(HttpStatus.CONFLICT, "Concert is not currently on sale"),
    CONCERT_NOT_PUBLISHABLE(HttpStatus.CONFLICT, "Concert cannot be published"),

    // Ticket
    TICKET_CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "Ticket category not found"),
    INSUFFICIENT_TICKET_QUANTITY(HttpStatus.CONFLICT, "The requested ticket quantity is no longer available"),
    BOOKING_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "Booking quantity exceeds the allowed limit"),

    // Booking
    BOOKING_NOT_FOUND(HttpStatus.NOT_FOUND, "Booking not found"),
    INVALID_BOOKING_STATUS_TRANSITION(HttpStatus.CONFLICT, "Invalid booking status transition"),

    // Voucher
    VOUCHER_NOT_FOUND(HttpStatus.NOT_FOUND, "Voucher not found"),
    VOUCHER_EXPIRED(HttpStatus.CONFLICT, "Voucher has expired"),
    VOUCHER_USAGE_LIMIT_REACHED(HttpStatus.CONFLICT, "Voucher usage limit has been reached"),
    VOUCHER_ALREADY_USED(HttpStatus.CONFLICT, "Voucher has already been used by this user"),
    VOUCHER_NOT_APPLICABLE(HttpStatus.CONFLICT, "Voucher is not applicable to this order"),

    // Idempotency
    IDEMPOTENCY_KEY_REQUIRED(HttpStatus.BAD_REQUEST, "Idempotency-Key header is required"),
    IDEMPOTENCY_KEY_CONFLICT(HttpStatus.CONFLICT, "Idempotency key conflict: different request body"),

    // User
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "User not found"),
    OPERATOR_NOT_FOUND(HttpStatus.NOT_FOUND, "Operator not found"),

    // General
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Validation error"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");

    private final HttpStatus httpStatus;
    private final String message;
}
