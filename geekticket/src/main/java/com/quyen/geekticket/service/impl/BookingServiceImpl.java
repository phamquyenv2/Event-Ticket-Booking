package com.quyen.geekticket.service.impl;

import com.quyen.geekticket.domain.entity.Booking;
import com.quyen.geekticket.domain.entity.BookingItem;
import com.quyen.geekticket.domain.entity.BookingStatusHistory;
import com.quyen.geekticket.domain.entity.Concert;
import com.quyen.geekticket.domain.entity.IdempotencyRecord;
import com.quyen.geekticket.domain.entity.TicketCategory;
import com.quyen.geekticket.domain.entity.User;
import com.quyen.geekticket.domain.entity.Voucher;
import com.quyen.geekticket.domain.entity.VoucherRedemption;
import com.quyen.geekticket.domain.request.BookingItemRequest;
import com.quyen.geekticket.domain.request.CancelBookingRequest;
import com.quyen.geekticket.domain.request.CreateBookingRequest;
import com.quyen.geekticket.domain.response.booking.BookingResponse;
import com.quyen.geekticket.repository.BookingRepository;
import com.quyen.geekticket.repository.ConcertRepository;
import com.quyen.geekticket.repository.IdempotencyRecordRepository;
import com.quyen.geekticket.repository.TicketCategoryRepository;
import com.quyen.geekticket.repository.UserRepository;
import com.quyen.geekticket.repository.VoucherRedemptionRepository;
import com.quyen.geekticket.repository.VoucherRepository;
import com.quyen.geekticket.service.BookingService;
import com.quyen.geekticket.util.constant.BookingStatus;
import com.quyen.geekticket.util.constant.ConcertStatus;
import com.quyen.geekticket.util.constant.DiscountType;
import com.quyen.geekticket.util.constant.VoucherStatus;
import com.quyen.geekticket.util.error.BusinessException;
import com.quyen.geekticket.util.error.ErrorCode;
import com.quyen.geekticket.util.error.InsufficientTicketException;
import com.quyen.geekticket.util.error.InvalidBookingStatusException;
import com.quyen.geekticket.util.error.ResourceNotFoundException;
import com.quyen.geekticket.util.generator.BookingCodeGenerator;
import com.quyen.geekticket.util.generator.RequestHashGenerator;
import com.quyen.geekticket.util.mapper.BookingMapper;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private final UserRepository userRepository;
    private final ConcertRepository concertRepository;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final BookingRepository bookingRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final VoucherRepository voucherRepository;
    private final VoucherRedemptionRepository voucherRedemptionRepository;
    private final BookingCodeGenerator bookingCodeGenerator;
    private final RequestHashGenerator requestHashGenerator;
    private final BookingMapper bookingMapper;
    private final EntityManager entityManager;

    @Override
    @Transactional
    public BookingResponse createBooking(Long userId,
                                         String idempotencyKey,
                                         CreateBookingRequest request) {
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        String requestHash = requestHashGenerator.generate(request);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));

        int claimed = idempotencyRecordRepository.tryClaim(user.getId(), normalizedKey, requestHash);
        if (claimed == 0) {
            return replayExistingBooking(user.getId(), normalizedKey, requestHash);
        }

        Concert concert = concertRepository.findById(request.getConcertId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CONCERT_NOT_FOUND));

        validateConcertOnSale(concert);
        List<ValidatedItem> validatedItems = validateAllItems(request, concert);

        validatedItems.stream()
                .sorted(Comparator.comparing(item -> item.category().getId()))
                .forEach(this::decrementInventory);

        Voucher voucher = null;
        BigDecimal discountAmount = BigDecimal.ZERO;

        if (request.getVoucherCode() != null && !request.getVoucherCode().isBlank()) {
            String code = request.getVoucherCode().trim();
            voucher = voucherRepository.findByCodeIgnoreCase(code)
                    .orElseThrow(() -> new BusinessException(ErrorCode.VOUCHER_NOT_FOUND,
                            "Voucher not found: " + code));

            if (voucher.getStatus() != VoucherStatus.ACTIVE) {
                throw new BusinessException(ErrorCode.VOUCHER_NOT_APPLICABLE,
                        "Voucher is not active: " + code);
            }

            Instant now = Instant.now();
            if (now.isBefore(voucher.getStartTime()) || now.isAfter(voucher.getEndTime())) {
                throw new BusinessException(ErrorCode.VOUCHER_EXPIRED,
                        "Voucher has expired or is not active yet: " + code);
            }

            if (voucher.getConcert() != null && !voucher.getConcert().getId().equals(concert.getId())) {
                throw new BusinessException(ErrorCode.VOUCHER_NOT_APPLICABLE,
                        "Voucher does not apply to concert " + concert.getId());
            }

            BigDecimal subtotal = validatedItems.stream()
                    .map(item -> item.category().getPrice().multiply(BigDecimal.valueOf(item.quantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (voucher.getMinOrderAmount() != null && subtotal.compareTo(voucher.getMinOrderAmount()) < 0) {
                throw new BusinessException(ErrorCode.VOUCHER_NOT_APPLICABLE,
                        "Order subtotal " + subtotal + " does not meet voucher minimum order amount " + voucher.getMinOrderAmount());
            }

            boolean alreadyUsed = voucherRedemptionRepository.existsByVoucherIdAndUserId(voucher.getId(), user.getId());
            if (alreadyUsed) {
                throw new BusinessException(ErrorCode.VOUCHER_ALREADY_USED,
                        "User " + user.getId() + " has already used voucher " + code);
            }

            int affectedRows = voucherRepository.incrementUsageCount(voucher.getId());
            if (affectedRows == 0) {
                throw new BusinessException(ErrorCode.VOUCHER_USAGE_LIMIT_REACHED,
                        "Voucher usage limit reached for " + code);
            }

            discountAmount = calculateVoucherDiscount(voucher, subtotal);
        }

        Booking booking = buildBooking(user, concert, validatedItems, discountAmount);
        if (voucher != null) {
            VoucherRedemption redemption = VoucherRedemption.builder()
                    .voucher(voucher)
                    .booking(booking)
                    .user(user)
                    .discountAmount(discountAmount)
                    .build();
            booking.setVoucherRedemption(redemption);
        }

        booking = bookingRepository.saveAndFlush(booking);

        int completed = idempotencyRecordRepository.completeClaim(
                user.getId(), normalizedKey, requestHash, booking.getId());
        if (completed != 1) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "Unable to complete idempotency record");
        }
        entityManager.refresh(booking);
        return bookingMapper.toResponse(booking);
    }

    @Override
    @Transactional(readOnly = true)
    public BookingResponse getBookingByCode(Long userId, String bookingCode) {
        if (userId == null) {
            throw new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, "User ID header is required");
        }
        Booking booking = bookingRepository.findByBookingCode(bookingCode)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.BOOKING_NOT_FOUND,
                        "Booking not found: " + bookingCode));

        if (!booking.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException(ErrorCode.BOOKING_NOT_FOUND,
                    "Booking not found: " + bookingCode);
        }
        return bookingMapper.toResponse(booking);
    }

    @Override
    @Transactional
    public BookingResponse cancelBooking(Long userId, Long bookingId, CancelBookingRequest request) {
        if (userId == null) {
            throw new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, "User ID header is required");
        }
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.BOOKING_NOT_FOUND,
                        "Booking not found: " + bookingId));

        if (!booking.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException(ErrorCode.BOOKING_NOT_FOUND,
                    "Booking not found: " + bookingId);
        }

        if (booking.getStatus() != BookingStatus.RESERVED) {
            throw new InvalidBookingStatusException(
                    "Only RESERVED bookings can be cancelled, current status: " + booking.getStatus());
        }

        booking.setStatus(BookingStatus.CANCELLED);
        String reason = (request != null && request.getReason() != null && !request.getReason().isBlank())
                ? request.getReason().trim()
                : "Cancelled by customer";

        booking.getStatusHistories().add(BookingStatusHistory.builder()
                .booking(booking)
                .fromStatus(BookingStatus.RESERVED.name())
                .toStatus(BookingStatus.CANCELLED.name())
                .changedBy("USER:" + userId)
                .reason(reason)
                .build());

        for (BookingItem item : booking.getBookingItems()) {
            ticketCategoryRepository.incrementAvailableQuantity(
                    item.getTicketCategory().getId(), item.getQuantity());
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

        booking = bookingRepository.saveAndFlush(booking);
        return bookingMapper.toResponse(booking);
    }

    private BigDecimal calculateVoucherDiscount(Voucher voucher, BigDecimal subtotal) {
        if (voucher.getDiscountType() == DiscountType.PERCENTAGE) {
            BigDecimal rawDiscount = subtotal
                    .multiply(voucher.getDiscountValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            if (voucher.getMaxDiscountAmount() != null && rawDiscount.compareTo(voucher.getMaxDiscountAmount()) > 0) {
                return voucher.getMaxDiscountAmount();
            }
            return rawDiscount;
        } else if (voucher.getDiscountType() == DiscountType.FIXED_AMOUNT) {
            if (voucher.getDiscountValue().compareTo(subtotal) > 0) {
                return subtotal;
            }
            return voucher.getDiscountValue();
        }
        return BigDecimal.ZERO;
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }

        String normalized = idempotencyKey.trim().toLowerCase(Locale.ROOT);
        try {
            UUID parsed = UUID.fromString(normalized);
            if (!parsed.toString().equals(normalized)) {
                throw new IllegalArgumentException("Non-canonical UUID");
            }
            return normalized;
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Idempotency-Key must be a valid UUID");
        }
    }

    private BookingResponse replayExistingBooking(Long userId,
                                                   String idempotencyKey,
                                                   String requestHash) {
        IdempotencyRecord existing = idempotencyRecordRepository
                .findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR,
                        "Idempotency claim could not be loaded"));

        if (!existing.getRequestHash().equals(requestHash)) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }
        if (existing.getBooking() == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "Idempotency record has no completed booking");
        }
        return bookingMapper.toResponse(existing.getBooking());
    }

    private void validateConcertOnSale(Concert concert) {
        Instant now = Instant.now();
        if (concert.getStatus() != ConcertStatus.PUBLISHED
                || now.isBefore(concert.getSaleStartTime())
                || !now.isBefore(concert.getSaleEndTime())) {
            throw new BusinessException(ErrorCode.CONCERT_NOT_ON_SALE);
        }
    }

    private List<ValidatedItem> validateAllItems(CreateBookingRequest request, Concert concert) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Booking items must not be empty");
        }

        Set<Long> requestedIds = new HashSet<>();
        for (BookingItemRequest item : request.getItems()) {
            if (item.getTicketCategoryId() == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Ticket category ID is required");
            }
            if (!requestedIds.add(item.getTicketCategoryId())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "Duplicate ticketCategoryId: " + item.getTicketCategoryId());
            }
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Quantity must be greater than 0");
            }
        }

        Map<Long, TicketCategory> categoriesById = new HashMap<>();
        ticketCategoryRepository.findAllById(requestedIds)
                .forEach(category -> categoriesById.put(category.getId(), category));

        List<ValidatedItem> result = new ArrayList<>(request.getItems().size());
        for (BookingItemRequest item : request.getItems()) {
            TicketCategory category = categoriesById.get(item.getTicketCategoryId());
            if (category == null || !category.getConcert().getId().equals(concert.getId())) {
                throw new ResourceNotFoundException(ErrorCode.TICKET_CATEGORY_NOT_FOUND,
                        "Ticket category " + item.getTicketCategoryId()
                                + " does not belong to concert " + concert.getId());
            }
            if (item.getQuantity() > category.getMaxQuantityPerBooking()) {
                throw new BusinessException(ErrorCode.BOOKING_LIMIT_EXCEEDED,
                        "Ticket category " + category.getId() + " allows at most "
                                + category.getMaxQuantityPerBooking() + " tickets per booking");
            }
            result.add(new ValidatedItem(category, item.getQuantity()));
        }
        return result;
    }

    private void decrementInventory(ValidatedItem item) {
        int affectedRows = ticketCategoryRepository.decrementAvailableQuantity(
                item.category().getId(), item.quantity());
        if (affectedRows == 0) {
            throw new InsufficientTicketException(
                    "Insufficient quantity for ticket category " + item.category().getId());
        }
    }

    private Booking buildBooking(User user, Concert concert, List<ValidatedItem> validatedItems, BigDecimal discountAmount) {
        Booking booking = Booking.builder()
                .bookingCode(bookingCodeGenerator.generateCode())
                .user(user)
                .concert(concert)
                .status(BookingStatus.RESERVED)
                .bookingItems(new ArrayList<>())
                .statusHistories(new ArrayList<>())
                .build();

        for (ValidatedItem validatedItem : validatedItems) {
            BookingItem bookingItem = BookingItem.builder()
                    .booking(booking)
                    .ticketCategory(validatedItem.category())
                    .quantity(validatedItem.quantity())
                    .unitPrice(validatedItem.category().getPrice())
                    .build();
            bookingItem.calculateSubtotal();
            booking.getBookingItems().add(bookingItem);
        }

        booking.calculateAmounts(discountAmount);
        booking.getStatusHistories().add(BookingStatusHistory.builder()
                .booking(booking)
                .toStatus(BookingStatus.RESERVED.name())
                .changedBy("USER:" + user.getId())
                .reason("Booking created")
                .build());
        return booking;
    }

    private record ValidatedItem(TicketCategory category, int quantity) {
    }
}
