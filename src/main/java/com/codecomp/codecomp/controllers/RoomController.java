package com.codecomp.codecomp.controllers;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.codecomp.codecomp.models.Participant;
import com.codecomp.codecomp.models.Room;
import com.codecomp.codecomp.repository.ParticipantRepository;
import com.codecomp.codecomp.repository.RoomRepository;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/rooms")
@RequiredArgsConstructor // lombok creates constrcutor for final feidls
public class RoomController {

    private final RoomRepository roomRepository;
    private final ParticipantRepository participantRepository;

    @PostMapping("/create")
    public Room createRoom(@RequestParam Long userId) {

        Room room = new Room();

        room.setHostUserId(userId);

        room.setPassword("1234");

        room.setStatus("WAITING");

        Room savedRoom = roomRepository.save(room); // db insert

        // now we add creator as participant
        Participant participant = new Participant();

        participant.setRoomId(savedRoom.getId());
        participant.setUserId(userId);
        participant.setScore(0);

        participantRepository.save(participant);

        return savedRoom;
    }
}
