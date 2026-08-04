package com.quyen.geekticket.repository;

import com.quyen.geekticket.domain.entity.Voucher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

@Repository
public interface VoucherRepository extends JpaRepository<Voucher, Long> {

    Optional<Voucher> findByCode(String code);

    Optional<Voucher> findByCodeIgnoreCase(String code);

    @Modifying
    @Query("""
        UPDATE Voucher v
        SET v.currentUsageCount = v.currentUsageCount + 1
        WHERE v.id = :voucherId
          AND (v.totalUsageLimit IS NULL OR v.currentUsageCount < v.totalUsageLimit)
    """)
    int incrementUsageCount(@Param("voucherId") Long voucherId);

    @Modifying
    @Query("""
        UPDATE Voucher v
        SET v.currentUsageCount = v.currentUsageCount - 1
        WHERE v.id = :voucherId
          AND v.currentUsageCount > 0
    """)
    int decrementUsageCount(@Param("voucherId") Long voucherId);
}
