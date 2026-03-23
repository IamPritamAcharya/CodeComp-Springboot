package com.codecomp.codecomp.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.codecomp.codecomp.models.RoomProblem;

public interface RoomProblemRepository extends JpaRepository<RoomProblem, Long> {
    List<RoomProblem> findByRoomId(Long roomId);
}
