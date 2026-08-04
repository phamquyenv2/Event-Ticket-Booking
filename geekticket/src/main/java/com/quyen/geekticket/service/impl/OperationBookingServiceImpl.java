package com.quyen.geekticket.service.impl;

import com.quyen.geekticket.domain.dto.PageResponse;
import com.quyen.geekticket.domain.entity.Booking;
import com.quyen.geekticket.domain.entity.BookingItem;
import com.quyen.geekticket.domain.entity.User;
import com.quyen.geekticket.domain.entity.Voucher;
import com.quyen.geekticket.domain.entity.VoucherRedemption;
import com.quyen.geekticket.domain.request.UpdateBookingStatusRequest;
import com.quyen.geekticket.domain.request.UpdateSuspiciousRequest;
import com.quyen.geekticket.domain.response.booking.OperationBookingDetailResponse;
import com.quyen.geekticket.domain.response.booking.OperationBookingResponse;
import com.quyen.geekticket.repository.BookingRepository;
import com.quyen.geekticket.repository.TicketCategoryRepository;
import com.quyen.geekticket.repository.UserRepository;
import com.quyen.geekticket.repository.VoucherRedemptionRepository;
import com.quyen.geekticket.repository.VoucherRepository;
import com.quyen.geekticket.repository.specification.BookingSpecification;
import com.quyen.geekticket.service.OperationBookingService;
import com.quyen.geekticket.util.constant.BookingStatus;
import com.quyen.geekticket.util.constant.UserRole;
import com.quyen.geekticket.util.error.BusinessException;
import com.quyen.geekticket.util.error.ErrorCode;
import com.quyen.geekticket.util.error.ResourceNotFoundException;
import com.quyen.geekticket.util.mapper.BookingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OperationBookingServiceImpl implements OperationBookingService {

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final VoucherRepository voucherRepository;
    private final VoucherRedemptionRepository voucherRedemptionRepository;
    private final BookingMapper bookingMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OperationBookingResponse> getBookings(Long operatorId,
                                                              BookingStatus status,
                                                              Long concertId,
                                                              Long userId,
                                                              Boolean suspicious,
                                                              Instant createdFrom,
                                                              Instant createdTo,
                                                              Pageable pageable) {
        validateOperator(operatorId);

        Specification<Booking> spec = BookingSpecification.filterBookings(
                status, concertId, userId, suspicious, createdFrom, createdTo);

        Page<Booking> page = bookingRepository.findAll(spec, pageable);
        List<OperationBookingResponse> content = page.getContent().stream()
                .map(bookingMapper::toOperationResponse)
                .toList();

        return PageResponse.<OperationBookingResponse>builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public OperationBookingDetailResponse getBookingDetail(Long operatorId, Long bookingId) {
        validateOperator(operatorId);

        Booking booking = bookingRepository.findDetailById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.BOOKING_NOT_FOUND,
                        "Booking not found: " + bookingId));

        return bookingMapper.toOperationDetailResponse(booking);
    }

    @Override
    @Transactional
    public OperationBookingDetailResponse updateBookingStatus(Long operatorId,
                                                               Long bookingId,
                                                               UpdateBookingStatusRequest request) {
        validateOperator(operatorId);

        Booking booking = bookingRepository.findDetailById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.BOOKING_NOT_FOUND,
                        "Booking not found: " + bookingId));

        BookingStatus newStatus = request.getStatus();
        String actor = "OPERATOR:" + operatorId;
        String reason = request.getReason();

        if (newStatus == BookingStatus.CONFIRMED) {
            booking.confirm(actor, reason);
        } else if (newStatus == BookingStatus.CANCELLED) {
            booking.cancel(actor, reason, true);
            restoreInventoryAndVoucher(booking);
        } else if (newStatus == BookingStatus.EXPIRED) {
            booking.expire(reason);
            restoreInventoryAndVoucher(booking);
        } else if (newStatus == BookingStatus.FAILED) {
            booking.markFailed(reason);
            restoreInventoryAndVoucher(booking);
        } else {
            throw new BusinessException(ErrorCode.INVALID_BOOKING_STATUS_TRANSITION,
                    "Unsupported target status: " + newStatus);
        }

        booking = bookingRepository.saveAndFlush(booking);
        return bookingMapper.toOperationDetailResponse(booking);
    }

    @Override
    @Transactional
    public OperationBookingDetailResponse updateBookingSuspicious(Long operatorId,
                                                                   Long bookingId,
                                                                   UpdateSuspiciousRequest request) {
        validateOperator(operatorId);

        Booking booking = bookingRepository.findDetailById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.BOOKING_NOT_FOUND,
                        "Booking not found: " + bookingId));

        booking.setSuspicious(request.getSuspicious());
        booking = bookingRepository.saveAndFlush(booking);
        return bookingMapper.toOperationDetailResponse(booking);
    }

    private void restoreInventoryAndVoucher(Booking booking) {
        if (booking.getBookingItems() != null) {
            for (BookingItem item : booking.getBookingItems()) {
                ticketCategoryRepository.incrementAvailableQuantity(
                        item.getTicketCategory().getId(), item.getQuantity());
            }
        }

        if (booking.getVoucherRedemption() != null) {
            VoucherRedemption redemption = booking.getVoucherRedemption();
            Voucher voucher = redemption.getVoucher();
            if (voucher != null) {
                voucherRepository.decrementUsageCount(voucher.getId());
            }
            booking.setVoucherRedemption(null);
            voucherRedemptionRepository.delete(redemption);
        }
    }

    private void validateOperator(Long operatorId) {
        if (operatorId == null) {
            throw new ResourceNotFoundException(ErrorCode.OPERATOR_NOT_FOUND, "X-Operator-Id header is required");
        }
        User operator = userRepository.findById(operatorId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.OPERATOR_NOT_FOUND,
                        "Operator not found: " + operatorId));

        if (operator.getRole() != UserRole.OPERATOR && operator.getRole() != UserRole.ADMIN) {
            throw new BusinessException(ErrorCode.OPERATOR_NOT_FOUND,
                    "User does not have OPERATOR or ADMIN role");
        }
    }
}
