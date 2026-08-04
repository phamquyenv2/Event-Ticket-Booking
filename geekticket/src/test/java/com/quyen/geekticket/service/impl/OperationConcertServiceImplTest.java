package com.quyen.geekticket.service.impl;

import com.quyen.geekticket.domain.entity.Concert;
import com.quyen.geekticket.domain.entity.TicketCategory;
import com.quyen.geekticket.domain.entity.User;
import com.quyen.geekticket.domain.request.CreateConcertRequest;
import com.quyen.geekticket.domain.response.concert.ConcertDetailResponse;
import com.quyen.geekticket.repository.ConcertRepository;
import com.quyen.geekticket.repository.TicketCategoryRepository;
import com.quyen.geekticket.repository.UserRepository;
import com.quyen.geekticket.util.constant.ConcertStatus;
import com.quyen.geekticket.util.constant.UserRole;
import com.quyen.geekticket.util.error.BusinessException;
import com.quyen.geekticket.util.error.ResourceNotFoundException;
import com.quyen.geekticket.util.mapper.ConcertMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationConcertServiceImplTest {

    @Mock
    private ConcertRepository concertRepository;

    @Mock
    private TicketCategoryRepository ticketCategoryRepository;

    @Mock
    private UserRepository userRepository;

    @Spy
    private ConcertMapper concertMapper;

    @InjectMocks
    private OperationConcertServiceImpl operationConcertService;

    private User operator;
    private User customer;

    @BeforeEach
    void setUp() {
        operator = User.builder()
                .id(3L)
                .username("operator01")
                .role(UserRole.OPERATOR)
                .build();

        customer = User.builder()
                .id(1L)
                .username("customer01")
                .role(UserRole.CUSTOMER)
                .build();
    }

    @Test
    @DisplayName("Create concert should fail when sale end time is before sale start time")
    void createConcert_invalidSaleTimes_throwsBusinessException() {
        when(userRepository.findById(3L)).thenReturn(Optional.of(operator));

        Instant now = Instant.now();
        CreateConcertRequest request = CreateConcertRequest.builder()
                .title("Test Concert")
                .venue("Test Venue")
                .totalCapacity(1000)
                .saleStartTime(now.plus(10, ChronoUnit.DAYS))
                .saleEndTime(now.plus(5, ChronoUnit.DAYS))  // BEFORE start
                .concertStartTime(now.plus(20, ChronoUnit.DAYS))
                .build();

        assertThatThrownBy(() -> operationConcertService.createConcert(request, 3L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Sale end time must be after sale start time");
    }

    @Test
    @DisplayName("Create concert should fail when concert start time is before sale start time")
    void createConcert_invalidConcertStartTime_throwsBusinessException() {
        when(userRepository.findById(3L)).thenReturn(Optional.of(operator));

        Instant now = Instant.now();
        CreateConcertRequest request = CreateConcertRequest.builder()
                .title("Test Concert")
                .venue("Test Venue")
                .totalCapacity(1000)
                .saleStartTime(now.plus(10, ChronoUnit.DAYS))
                .saleEndTime(now.plus(15, ChronoUnit.DAYS))
                .concertStartTime(now.plus(5, ChronoUnit.DAYS))  // BEFORE sale start
                .build();

        assertThatThrownBy(() -> operationConcertService.createConcert(request, 3L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Concert start time must be after sale start time");
    }

    @Test
    @DisplayName("Create concert should fail when user is not an operator")
    void createConcert_nonOperator_throwsResourceNotFoundException() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(customer));

        Instant now = Instant.now();
        CreateConcertRequest request = CreateConcertRequest.builder()
                .title("Test Concert")
                .venue("Test Venue")
                .totalCapacity(1000)
                .saleStartTime(now.plus(5, ChronoUnit.DAYS))
                .saleEndTime(now.plus(10, ChronoUnit.DAYS))
                .concertStartTime(now.plus(15, ChronoUnit.DAYS))
                .build();

        assertThatThrownBy(() -> operationConcertService.createConcert(request, 1L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Create concert should succeed with valid data")
    void createConcert_validData_returnsConcertDetail() {
        when(userRepository.findById(3L)).thenReturn(Optional.of(operator));

        Instant now = Instant.now();
        CreateConcertRequest request = CreateConcertRequest.builder()
                .title("Test Concert")
                .venue("Test Venue")
                .totalCapacity(1000)
                .saleStartTime(now.plus(5, ChronoUnit.DAYS))
                .saleEndTime(now.plus(10, ChronoUnit.DAYS))
                .concertStartTime(now.plus(15, ChronoUnit.DAYS))
                .build();

        Concert savedConcert = Concert.builder()
                .id(1L)
                .title("Test Concert")
                .venue("Test Venue")
                .totalCapacity(1000)
                .status(ConcertStatus.DRAFT)
                .saleStartTime(request.getSaleStartTime())
                .saleEndTime(request.getSaleEndTime())
                .concertStartTime(request.getConcertStartTime())
                .ticketCategories(new ArrayList<>())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(concertRepository.save(any(Concert.class))).thenReturn(savedConcert);

        ConcertDetailResponse result = operationConcertService.createConcert(request, 3L);

        assertThat(result.getTitle()).isEqualTo("Test Concert");
        assertThat(result.getStatus()).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("Publish concert should fail when concert has no ticket categories")
    void publishConcert_noTicketCategories_throwsBusinessException() {
        when(userRepository.findById(3L)).thenReturn(Optional.of(operator));

        Concert concert = Concert.builder()
                .id(1L)
                .status(ConcertStatus.DRAFT)
                .ticketCategories(new ArrayList<>())
                .build();

        when(concertRepository.findById(1L)).thenReturn(Optional.of(concert));

        assertThatThrownBy(() -> operationConcertService.publishConcert(1L, 3L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("at least one ticket category");
    }

    @Test
    @DisplayName("Publish concert should fail when concert is not DRAFT")
    void publishConcert_notDraft_throwsBusinessException() {
        when(userRepository.findById(3L)).thenReturn(Optional.of(operator));

        Concert concert = Concert.builder()
                .id(1L)
                .status(ConcertStatus.PUBLISHED)
                .build();

        when(concertRepository.findById(1L)).thenReturn(Optional.of(concert));

        assertThatThrownBy(() -> operationConcertService.publishConcert(1L, 3L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Only DRAFT concerts can be published");
    }

    @Test
    @DisplayName("Publish concert should succeed with valid DRAFT concert and ticket categories")
    void publishConcert_validDraftWithCategories_succeeds() {
        when(userRepository.findById(3L)).thenReturn(Optional.of(operator));

        TicketCategory vip = TicketCategory.builder()
                .id(1L)
                .name("VIP")
                .price(BigDecimal.valueOf(2500000))
                .totalQuantity(500)
                .availableQuantity(500)
                .build();

        Concert concert = Concert.builder()
                .id(1L)
                .title("Test Concert")
                .venue("Test Venue")
                .status(ConcertStatus.DRAFT)
                .ticketCategories(List.of(vip))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(concertRepository.findById(1L)).thenReturn(Optional.of(concert));
        when(concertRepository.save(any(Concert.class))).thenReturn(concert);

        ConcertDetailResponse result = operationConcertService.publishConcert(1L, 3L);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("Create concert should fail when operator does not exist")
    void createConcert_operatorNotFound_throwsResourceNotFoundException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        Instant now = Instant.now();
        CreateConcertRequest request = CreateConcertRequest.builder()
                .title("Test Concert")
                .venue("Test Venue")
                .totalCapacity(1000)
                .saleStartTime(now.plus(5, ChronoUnit.DAYS))
                .saleEndTime(now.plus(10, ChronoUnit.DAYS))
                .concertStartTime(now.plus(15, ChronoUnit.DAYS))
                .build();

        assertThatThrownBy(() -> operationConcertService.createConcert(request, 99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
