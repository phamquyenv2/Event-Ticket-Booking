package com.quyen.geekticket.repository.specification;

import com.quyen.geekticket.domain.entity.Booking;
import com.quyen.geekticket.util.constant.BookingStatus;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class BookingSpecification {

    public static Specification<Booking> filterBookings(BookingStatus status,
                                                        Long concertId,
                                                        Long userId,
                                                        Boolean suspicious,
                                                        Instant createdFrom,
                                                        Instant createdTo) {
        return (root, query, cb) -> {
            if (Long.class != query.getResultType() && long.class != query.getResultType()) {
                root.fetch("user", JoinType.LEFT);
                root.fetch("concert", JoinType.LEFT);
            }

            List<Predicate> predicates = new ArrayList<>();

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (concertId != null) {
                predicates.add(cb.equal(root.get("concert").get("id"), concertId));
            }
            if (userId != null) {
                predicates.add(cb.equal(root.get("user").get("id"), userId));
            }
            if (suspicious != null) {
                predicates.add(cb.equal(root.get("suspicious"), suspicious));
            }
            if (createdFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), createdFrom));
            }
            if (createdTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), createdTo));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
