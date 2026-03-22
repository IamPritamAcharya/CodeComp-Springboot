package com.codecomp.codecomp.room;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;

import com.codecomp.codecomp.dto.EndContestResponse;
import com.codecomp.codecomp.dto.LeaderboardResponse;
import com.codecomp.codecomp.models.Participant;
import com.codecomp.codecomp.models.Problem;
import com.codecomp.codecomp.models.Room;
import com.codecomp.codecomp.models.RoomProblem;
import com.codecomp.codecomp.repository.ParticipantRepository;
import com.codecomp.codecomp.repository.ProblemRepository;
import com.codecomp.codecomp.repository.RoomProblemRepository;
import com.codecomp.codecomp.repository.RoomRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final ParticipantRepository participantRepository;
    private final ProblemRepository problemRepository;
    private final RoomProblemRepository roomProblemRepository;

    public List<LeaderboardResponse> getLeaderboard(Long roomId) {

        // validate room
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        List<Participant> participants = participantRepository.findByRoomId(roomId);

        if (participants.isEmpty()) {
            return Collections.emptyList();
        }

        participants.sort((a, b) -> b.getScore().compareTo(a.getScore()));

        List<LeaderboardResponse> leaderboard = new ArrayList<>();
        int rank = 1;

        for (int i = 0; i < participants.size(); i++) {

            if (i > 0 && !participants.get(i).getScore().equals(participants.get(i - 1).getScore())) {
                rank = i + 1;
            }

            leaderboard.add(new LeaderboardResponse(
                    participants.get(i).getUserId(),
                    participants.get(i).getScore(),
                    rank));
        }

        return leaderboard;
    }

    public EndContestResponse endContest(Long roomId) {

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        if (!"ACTIVE".equalsIgnoreCase(room.getStatus())) {
            throw new RuntimeException("Contest not active");
        }

        List<Participant> participants = participantRepository.findByRoomId(roomId);

        if (participants.size() != 2) {
            throw new RuntimeException("Invalid participant count");
        }

        Participant p1 = participants.get(0);
        Participant p2 = participants.get(1);

        Long winnerUserId = null;
        String result;

        if (p1.getScore() > p2.getScore()) {
            winnerUserId = p1.getUserId();
            result = "WIN";
        } else if (p2.getScore() > p1.getScore()) {
            winnerUserId = p2.getUserId();
            result = "WIN";
        } else {
            result = "DRAW";
        }

        room.setStatus("FINISHED");
        roomRepository.save(room);

        return new EndContestResponse(winnerUserId, result);
    }

    public Room createRoom(Long userId) {
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

    public String joinRoom(Long roomId, String password, Long userId) {

        // all things that can go wrong

        // case1: If room exists
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        // case2: Check password
        if (!room.getPassword().equals(password)) {
            return "Wrong Password";
        }

        List<Participant> roomParticipants = participantRepository.findByRoomId(roomId);

        // case3: Room is full
        if (roomParticipants.size() >= 2) {
            return "Room is full";
        }

        // case4: If user already exists in this room
        for (Participant p : roomParticipants) {
            if (p.getUserId().equals(userId)) {
                return "User already in this room";
            }
        }

        // case5: Check is user already in another ACTIVE or WAITING room
        List<Participant> userParticipants = participantRepository.findByUserId(userId);

        for (Participant p : userParticipants) {
            Room existingRoom = roomRepository.findById(p.getRoomId()).orElse(null);

            if (existingRoom != null
                    && (existingRoom.getStatus().equals("WAITING") || existingRoom.getStatus().equals("ACTIVE"))) {
                return "User already in another active room";
            }
        }

        Participant participant = new Participant();
        participant.setRoomId(roomId);
        participant.setUserId(userId);
        participant.setScore(0);

        participantRepository.save(participant);

        return "Joined successfully";
    }

    public String startContest(Long roomId, Long userId) {
        Room room = roomRepository.findById(roomId).orElseThrow(() -> new RuntimeException("Room not found"));

        // Only host can start
        if (!room.getHostUserId().equals(userId)) {
            return "Only host can start the contest";
        }

        // case1: check room status
        if (!room.getStatus().equals("WAITING")) {
            return "Room already started or finished";
        }

        // case2: to check partiicipant count
        List<Participant> participants = participantRepository.findByRoomId(roomId);

        if (participants.size() != 2) {
            return "Need exactly 2 players to start";
        }

        // fetch problems ( Current Count : 3 )
        List<Problem> problems = problemRepository.findAll();

        if (problems.size() < 3) {
            return "Not enough problems in DB";
        }

        for (int i = 0; i < 3; i++) {
            RoomProblem rp = new RoomProblem();
            rp.setRoomId(roomId);
            rp.setProblemId(problems.get(i).getId());

            roomProblemRepository.save(rp);
        }

        room.setStatus("ACTIVE");
        roomRepository.save(room);

        return "Contest started";
    }
}
