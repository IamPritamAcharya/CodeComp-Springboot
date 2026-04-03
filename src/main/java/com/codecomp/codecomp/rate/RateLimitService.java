package com.codecomp.codecomp.rate;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.codecomp.codecomp.repository.SubmissionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final SubmissionRepository submissionRepository;

    @Value("${rate.limit.max}")
    private int maxRequests;

    @Value("${rate.limit.window}")
    private long windowMs;

    public void checkSubmissionLimit(Long userId) {

        LocalDateTime windowStart = LocalDateTime.now()
                .minusSeconds(windowMs / 1000);

        long count = submissionRepository
                .countByUserIdAndCreatedAtAfter(userId, windowStart);

        if (count >= maxRequests) {
            throw new RuntimeException("Too many submissions. Please slow down.");
        }
    }
}