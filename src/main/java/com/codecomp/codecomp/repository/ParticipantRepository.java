package com.codecomp.codecomp.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.codecomp.codecomp.models.Participant;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {

    // SELECT * FROM participants WHERE room_id = ?
    List<Participant> findByRoomId(Long roomId);

    List<Participant> findByUserId(Long userId);

}
