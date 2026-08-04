package com.quyen.geekticket.domain.entity;

import com.quyen.geekticket.util.constant.BookingStatus;
import com.quyen.geekticket.util.error.BusinessException;
import com.quyen.geekticket.util.error.ErrorCode;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_code", nullable = false, unique = true, length = 36)
    private String bookingCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "concert_id", nullable = false)
    private Concert concert;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "final_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal finalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<BookingItem> bookingItems = new ArrayList<>();

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<BookingStatusHistory> statusHistories = new ArrayList<>();

    @OneToOne(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    private VoucherRedemption voucherRedemption;

    @Builder.Default
    @Column(name = "suspicious", nullable = false)
    private Boolean suspicious = false;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public void calculateAmounts(BigDecimal discount) {
        BigDecimal sumSubtotal = BigDecimal.ZERO;
        if (bookingItems != null) {
            for (BookingItem item : bookingItems) {
                sumSubtotal = sumSubtotal.add(item.calculateSubtotal());
            }
        }
        this.totalAmount = sumSubtotal;
        this.discountAmount = discount != null ? discount : BigDecimal.ZERO;

        BigDecimal calculatedFinal = this.totalAmount.subtract(this.discountAmount);
        this.finalAmount = calculatedFinal.compareTo(BigDecimal.ZERO) < 0
                ? BigDecimal.ZERO
                : calculatedFinal;
    }

    public void confirm(String changedBy, String reason) {
        if (this.status != BookingStatus.RESERVED) {
            throw new BusinessException(ErrorCode.INVALID_BOOKING_STATUS_TRANSITION,
                    "Cannot confirm booking from status " + this.status + ". Must be RESERVED.");
        }
        changeStatus(BookingStatus.CONFIRMED, changedBy, reason);
    }

    public void cancel(String changedBy, String reason, boolean isOperator) {
        if (this.status == BookingStatus.RESERVED) {
            changeStatus(BookingStatus.CANCELLED, changedBy, reason);
        } else if (this.status == BookingStatus.CONFIRMED && isOperator) {
            changeStatus(BookingStatus.CANCELLED, changedBy, reason);
        } else {
            throw new BusinessException(ErrorCode.INVALID_BOOKING_STATUS_TRANSITION,
                    "Cannot cancel booking from status " + this.status + (isOperator ? " (Operator)" : " (Customer)"));
        }
    }

    public void expire(String reason) {
        if (this.status != BookingStatus.RESERVED) {
            throw new BusinessException(ErrorCode.INVALID_BOOKING_STATUS_TRANSITION,
                    "Cannot expire booking from status " + this.status + ". Must be RESERVED.");
        }
        changeStatus(BookingStatus.EXPIRED, "SYSTEM", reason);
    }

    public void markFailed(String reason) {
        if (this.status != BookingStatus.RESERVED) {
            throw new BusinessException(ErrorCode.INVALID_BOOKING_STATUS_TRANSITION,
                    "Cannot mark booking as failed from status " + this.status + ". Must be RESERVED.");
        }
        changeStatus(BookingStatus.FAILED, "SYSTEM", reason);
    }

    private void changeStatus(BookingStatus newStatus, String changedBy, String reason) {
        String oldStatusStr = this.status != null ? this.status.name() : null;
        this.status = newStatus;

        BookingStatusHistory history = BookingStatusHistory.builder()
                .booking(this)
                .fromStatus(oldStatusStr)
                .toStatus(newStatus.name())
                .changedBy(changedBy != null ? changedBy : "SYSTEM")
                .reason(reason)
                .build();

        if (this.statusHistories == null) {
            this.statusHistories = new ArrayList<>();
        }
        this.statusHistories.add(history);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Booking booking = (Booking) o;
        return id != null && Objects.equals(id, booking.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
