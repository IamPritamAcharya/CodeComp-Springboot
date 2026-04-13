package com.codecomp.codecomp.service;

import com.codecomp.codecomp.rate.RateLimitService;
import com.codecomp.codecomp.repository.SubmissionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitServiceTest {

    @Mock
    private SubmissionRepository submissionRepository;

    @Test
    void checkSubmissionLimit_shouldAllowBelowLimit() {
        RateLimitService rateLimitService = new RateLimitService(submissionRepository);
        ReflectionTestUtils.setField(rateLimitService, "maxRequests", 5);
        ReflectionTestUtils.setField(rateLimitService, "windowMs", 10_000L);
        when(submissionRepository.countByUserIdAndCreatedAtAfter(
                eq(1L),
                any(LocalDateTime.class))).thenReturn(4L);

        assertDoesNotThrow(() -> rateLimitService.checkSubmissionLimit(1L));
    }

    @Test
    void checkSubmissionLimit_shouldRejectAtLimit() {
        RateLimitService rateLimitService = new RateLimitService(submissionRepository);
        ReflectionTestUtils.setField(rateLimitService, "maxRequests", 5);
        ReflectionTestUtils.setField(rateLimitService, "windowMs", 10_000L);

        when(submissionRepository.countByUserIdAndCreatedAtAfter(1L, LocalDateTime.now().minusSeconds(10)))
                .thenReturn(5L);

        assertThrows(RuntimeException.class, () -> rateLimitService.checkSubmissionLimit(1L));
    }
}