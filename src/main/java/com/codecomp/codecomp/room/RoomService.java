package com.codecomp.codecomp.room;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.codecomp.codecomp.dto.EndContestResponse;
import com.codecomp.codecomp.dto.LeaderboardResponse;
import com.codecomp.codecomp.models.Participant;
import com.codecomp.codecomp.models.ParticipantProblem;
import com.codecomp.codecomp.models.Problem;
import com.codecomp.codecomp.models.Room;
import com.codecomp.codecomp.models.RoomProblem;
import com.codecomp.codecomp.repository.ParticipantProblemRepository;
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
    private final ParticipantProblemRepository participantProblemRepository;

    public List<LeaderboardResponse> getLeaderboard(Long roomId) {

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        List<ParticipantProblem> ppList = participantProblemRepository.findByRoomId(roomId);

        if (ppList.isEmpty()) {
            return Collections.emptyList();
        }

        // group by user
        Map<Long, List<ParticipantProblem>> userMap = new HashMap<>();

        for (ParticipantProblem pp : ppList) {
            userMap.computeIfAbsent(pp.getUserId(), k -> new ArrayList<>()).add(pp);
        }

        List<LeaderboardResponse> leaderboard = new ArrayList<>();

        // compute score per user
        for (Long userId : userMap.keySet()) {

            int solved = 0;
            int totalPenalty = 0;

            for (ParticipantProblem pp : userMap.get(userId)) {
                if (Boolean.TRUE.equals(pp.getSolved())) {
                    solved++;
                    totalPenalty += pp.getPenalty();
                }
            }

            leaderboard.add(new LeaderboardResponse(
                    userId,
                    solved,
                    totalPenalty,
                    0 // rank will be assigned later
            ));
        }

        // sort:
        // 1. solved DESC
        // 2. penalty ASC
        leaderboard.sort((a, b) -> {
            if (!b.getSolved().equals(a.getSolved())) {
                return b.getSolved() - a.getSolved();
            }
            return a.getPenalty() - b.getPenalty();
        });

        // assign ranks
        int rank = 1;

        for (int i = 0; i < leaderboard.size(); i++) {

            if (i > 0 &&
                    (!leaderboard.get(i).getSolved().equals(leaderboard.get(i - 1).getSolved()) ||
                            !leaderboard.get(i).getPenalty().equals(leaderboard.get(i - 1).getPenalty()))) {

                rank = i + 1;
            }

            leaderboard.get(i).setRank(rank);
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

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        if (!room.getHostUserId().equals(userId)) {
            throw new RuntimeException("Only host can start the contest");
        }

        if (!"WAITING".equals(room.getStatus())) {
            throw new RuntimeException("Room already started or finished");
        }

        List<Participant> participants = participantRepository.findByRoomId(roomId);

        if (participants.size() != 2) {
            throw new RuntimeException("Need exactly 2 players to start");
        }

        // Fetch problems
        List<Problem> problems = problemRepository.findAll();

        if (problems.size() < 3) {
            throw new RuntimeException("Not enough problems in DB");
        }

        // Assign first 3 problems to room
        for (int i = 0; i < 3; i++) {
            RoomProblem rp = new RoomProblem();
            rp.setRoomId(roomId);
            rp.setProblemId(problems.get(i).getId());
            roomProblemRepository.save(rp);
        }

        // Fetch assigned problems 
        List<RoomProblem> roomProblems = roomProblemRepository.findByRoomId(roomId);

        // Initialize ParticipantProblem for each user + problem
        for (Participant participant : participants) {

            for (RoomProblem rp : roomProblems) {

                boolean exists = participantProblemRepository
                        .findByUserIdAndRoomIdAndProblemId(
                                participant.getUserId(),
                                roomId,
                                rp.getProblemId())
                        .isPresent();

                if (!exists) {
                    ParticipantProblem pp = new ParticipantProblem();
                    pp.setUserId(participant.getUserId());
                    pp.setRoomId(roomId);
                    pp.setProblemId(rp.getProblemId());
                    pp.setAttempts(0);
                    pp.setPenalty(0);
                    pp.setSolved(false);

                    participantProblemRepository.save(pp);
                }
            }
        }

        room.setStartTime(System.currentTimeMillis());

        room.setStatus("ACTIVE");
        roomRepository.save(room);

        return "Contest started";
    }
}
