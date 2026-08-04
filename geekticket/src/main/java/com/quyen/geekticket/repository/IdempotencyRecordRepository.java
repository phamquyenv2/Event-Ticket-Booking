package com.quyen.geekticket.repository;

import com.quyen.geekticket.domain.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO idempotency_records
                (idempotency_key, user_id, request_hash, created_at, updated_at)
            VALUES
                (:idempotencyKey, :userId, :requestHash, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT (user_id, idempotency_key) DO NOTHING
            """, nativeQuery = true)
    int tryClaim(@Param("userId") Long userId,
                 @Param("idempotencyKey") String idempotencyKey,
                 @Param("requestHash") String requestHash);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE idempotency_records
            SET booking_id = :bookingId,
                updated_at = CURRENT_TIMESTAMP
            WHERE user_id = :userId
              AND idempotency_key = :idempotencyKey
              AND request_hash = :requestHash
              AND booking_id IS NULL
            """, nativeQuery = true)
    int completeClaim(@Param("userId") Long userId,
                      @Param("idempotencyKey") String idempotencyKey,
                      @Param("requestHash") String requestHash,
                      @Param("bookingId") Long bookingId);

    @EntityGraph(attributePaths = {
            "booking",
            "booking.bookingItems",
            "booking.bookingItems.ticketCategory"
    })
    Optional<IdempotencyRecord> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    long countByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);
}
