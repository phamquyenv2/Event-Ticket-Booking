package com.quyen.geekticket.repository;

import com.quyen.geekticket.domain.entity.Booking;
import com.quyen.geekticket.util.constant.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long>, JpaSpecificationExecutor<Booking> {

    @Override
    @EntityGraph(attributePaths = {"user", "concert"})
    Page<Booking> findAll(Specification<Booking> spec, Pageable pageable);

    Optional<Booking> findByBookingCode(String bookingCode);

    Page<Booking> findByUserId(Long userId, Pageable pageable);

    Page<Booking> findByStatus(BookingStatus status, Pageable pageable);

    Page<Booking> findByUserIdAndStatus(Long userId, BookingStatus status, Pageable pageable);

    long countByConcertId(Long concertId);

    @EntityGraph(attributePaths = {
            "user",
            "concert",
            "bookingItems",
            "bookingItems.ticketCategory",
            "voucherRedemption",
            "voucherRedemption.voucher"
    })
    @Query("SELECT b FROM Booking b WHERE b.id = :id")
    Optional<Booking> findDetailById(@Param("id") Long id);
}
