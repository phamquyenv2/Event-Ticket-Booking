package com.quyen.geekticket.repository;

import com.quyen.geekticket.domain.entity.VoucherRedemption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VoucherRedemptionRepository extends JpaRepository<VoucherRedemption, Long> {

    boolean existsByVoucherIdAndUserId(Long voucherId, Long userId);

    int countByVoucherId(Long voucherId);

    java.util.Optional<VoucherRedemption> findByBookingId(Long bookingId);

    void deleteByBookingId(Long bookingId);
}
