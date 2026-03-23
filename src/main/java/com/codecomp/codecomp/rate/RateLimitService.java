package com.codecomp.codecomp.rate;

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

        long now = System.currentTimeMillis();

        long count = submissionRepository
                .countByUserIdAndCreatedAtAfter(userId, now - windowMs);

        if (count >= maxRequests) {
            throw new RuntimeException("Too many submissions. Please slow down.");
        }
    }
}