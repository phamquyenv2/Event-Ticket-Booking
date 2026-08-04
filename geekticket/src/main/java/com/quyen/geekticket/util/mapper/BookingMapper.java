package com.quyen.geekticket.util.mapper;

import com.quyen.geekticket.domain.entity.Booking;
import com.quyen.geekticket.domain.entity.BookingItem;
import com.quyen.geekticket.domain.entity.BookingStatusHistory;
import com.quyen.geekticket.domain.response.booking.BookingItemResponse;
import com.quyen.geekticket.domain.response.booking.BookingResponse;
import com.quyen.geekticket.domain.response.booking.BookingStatusHistoryResponse;
import com.quyen.geekticket.domain.response.booking.OperationBookingDetailResponse;
import com.quyen.geekticket.domain.response.booking.OperationBookingResponse;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class BookingMapper {

    public BookingResponse toResponse(Booking booking) {
        if (booking == null) {
            return null;
        }

        List<BookingItemResponse> items = booking.getBookingItems() != null
                ? booking.getBookingItems().stream().map(this::toItemResponse).toList()
                : Collections.emptyList();

        String voucherCode = booking.getVoucherRedemption() != null && booking.getVoucherRedemption().getVoucher() != null
                ? booking.getVoucherRedemption().getVoucher().getCode()
                : null;

        return BookingResponse.builder()
                .id(booking.getId())
                .bookingCode(booking.getBookingCode())
                .status(booking.getStatus() != null ? booking.getStatus().name() : null)
                .items(items)
                .subtotal(booking.getTotalAmount())
                .discountAmount(booking.getDiscountAmount())
                .totalAmount(booking.getFinalAmount())
                .voucherCode(voucherCode)
                .createdAt(booking.getCreatedAt())
                .build();
    }

    public OperationBookingResponse toOperationResponse(Booking booking) {
        if (booking == null) {
            return null;
        }

        return OperationBookingResponse.builder()
                .id(booking.getId())
                .bookingCode(booking.getBookingCode())
                .userId(booking.getUser() != null ? booking.getUser().getId() : null)
                .userEmail(booking.getUser() != null ? booking.getUser().getEmail() : null)
                .concertId(booking.getConcert() != null ? booking.getConcert().getId() : null)
                .concertTitle(booking.getConcert() != null ? booking.getConcert().getTitle() : null)
                .totalAmount(booking.getTotalAmount())
                .discountAmount(booking.getDiscountAmount())
                .finalAmount(booking.getFinalAmount())
                .status(booking.getStatus() != null ? booking.getStatus().name() : null)
                .suspicious(booking.getSuspicious())
                .createdAt(booking.getCreatedAt())
                .build();
    }

    public OperationBookingDetailResponse toOperationDetailResponse(Booking booking) {
        if (booking == null) {
            return null;
        }

        List<BookingItemResponse> items = booking.getBookingItems() != null
                ? booking.getBookingItems().stream().map(this::toItemResponse).toList()
                : Collections.emptyList();

        List<BookingStatusHistoryResponse> histories = booking.getStatusHistories() != null
                ? booking.getStatusHistories().stream().map(this::toHistoryResponse).toList()
                : Collections.emptyList();

        String voucherCode = booking.getVoucherRedemption() != null && booking.getVoucherRedemption().getVoucher() != null
                ? booking.getVoucherRedemption().getVoucher().getCode()
                : null;

        return OperationBookingDetailResponse.builder()
                .id(booking.getId())
                .bookingCode(booking.getBookingCode())
                .userId(booking.getUser() != null ? booking.getUser().getId() : null)
                .userEmail(booking.getUser() != null ? booking.getUser().getEmail() : null)
                .concertId(booking.getConcert() != null ? booking.getConcert().getId() : null)
                .concertTitle(booking.getConcert() != null ? booking.getConcert().getTitle() : null)
                .totalAmount(booking.getTotalAmount())
                .discountAmount(booking.getDiscountAmount())
                .finalAmount(booking.getFinalAmount())
                .status(booking.getStatus() != null ? booking.getStatus().name() : null)
                .suspicious(booking.getSuspicious())
                .voucherCode(voucherCode)
                .items(items)
                .statusHistories(histories)
                .createdAt(booking.getCreatedAt())
                .updatedAt(booking.getUpdatedAt())
                .build();
    }

    public BookingItemResponse toItemResponse(BookingItem item) {
        if (item == null) {
            return null;
        }

        return BookingItemResponse.builder()
                .id(item.getId())
                .ticketCategoryId(item.getTicketCategory() != null ? item.getTicketCategory().getId() : null)
                .ticketCategoryName(item.getTicketCategory() != null ? item.getTicketCategory().getName() : null)
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .subtotal(item.getSubtotal())
                .build();
    }

    public BookingStatusHistoryResponse toHistoryResponse(BookingStatusHistory history) {
        if (history == null) {
            return null;
        }

        return BookingStatusHistoryResponse.builder()
                .id(history.getId())
                .fromStatus(history.getFromStatus())
                .toStatus(history.getToStatus())
                .changedBy(history.getChangedBy())
                .reason(history.getReason())
                .createdAt(history.getCreatedAt())
                .build();
    }
}
