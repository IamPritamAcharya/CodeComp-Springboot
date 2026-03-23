package com.codecomp.codecomp.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.codecomp.codecomp.models.ParticipantProblem;

public interface ParticipantProblemRepository
        extends JpaRepository<ParticipantProblem, Long> {

    Optional<ParticipantProblem> findByUserIdAndRoomIdAndProblemId(
            Long userId, Long roomId, Long problemId);

    List<ParticipantProblem> findByRoomId(Long roomId);
}