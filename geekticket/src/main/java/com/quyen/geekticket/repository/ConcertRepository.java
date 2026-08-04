package com.quyen.geekticket.repository;

import com.quyen.geekticket.domain.entity.Concert;
import com.quyen.geekticket.util.constant.ConcertStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConcertRepository extends JpaRepository<Concert, Long> {

    Page<Concert> findByStatus(ConcertStatus status, Pageable pageable);
}
