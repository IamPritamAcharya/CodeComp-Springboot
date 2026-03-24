package com.codecomp.codecomp.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.codecomp.codecomp.models.ContestHistory;

public interface ContestHistoryRepository extends JpaRepository<ContestHistory, Long> {

    List<ContestHistory> findByUserId(Long userId);

    List<ContestHistory> findByUserIdOrderByTimestampDesc(Long userId);
}