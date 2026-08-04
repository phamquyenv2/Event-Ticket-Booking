package com.quyen.geekticket.repository;

import com.quyen.geekticket.domain.entity.TicketCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TicketCategoryRepository extends JpaRepository<TicketCategory, Long> {

    List<TicketCategory> findByConcertId(Long concertId);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE ticket_categories
            SET available_quantity = available_quantity - :quantity
            WHERE id = :ticketCategoryId
              AND available_quantity >= :quantity
            """, nativeQuery = true)
    int decrementAvailableQuantity(@Param("ticketCategoryId") Long ticketCategoryId,
                                   @Param("quantity") int quantity);

    @Modifying
    @Query("UPDATE TicketCategory tc SET tc.availableQuantity = tc.availableQuantity + :qty " +
           "WHERE tc.id = :id")
    int incrementAvailableQuantity(@Param("id") Long id, @Param("qty") int qty);
}
