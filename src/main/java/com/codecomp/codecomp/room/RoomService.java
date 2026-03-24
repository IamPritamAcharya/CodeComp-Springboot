package com.codecomp.codecomp.room;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.codecomp.codecomp.dto.EndContestResponse;
import com.codecomp.codecomp.dto.LeaderboardResponse;
import com.codecomp.codecomp.dto.RoomStateResponse;
import com.codecomp.codecomp.models.ContestHistory;
import com.codecomp.codecomp.models.Participant;
import com.codecomp.codecomp.models.ParticipantProblem;
import com.codecomp.codecomp.models.Problem;
import com.codecomp.codecomp.models.Room;
import com.codecomp.codecomp.models.RoomProblem;
import com.codecomp.codecomp.repository.ContestHistoryRepository;
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
    private final ContestHistoryRepository contestHistoryRepository;

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
            long totalPenalty = 0;
            long lastSolvedTime = Long.MAX_VALUE;

            for (ParticipantProblem pp : userMap.get(userId)) {
                if (Boolean.TRUE.equals(pp.getSolved()) &&
                        pp.getSolvedAt() != null &&
                        room.getStartTime() != null) {

                    solved++;

                    long timeTaken = (pp.getSolvedAt() - room.getStartTime()) / 1000;

                    long problemPenalty = pp.getPenalty() * 600L + timeTaken;

                    totalPenalty += problemPenalty;

                    // track latest solved problem time
                    lastSolvedTime = Math.min(lastSolvedTime, pp.getSolvedAt());
                }
            }

            leaderboard.add(new LeaderboardResponse(
                    userId,
                    solved,
                    totalPenalty,
                    lastSolvedTime,
                    0));
        }

        // sort:
        leaderboard.sort((a, b) -> {
            if (!b.getSolved().equals(a.getSolved())) {
                return b.getSolved() - a.getSolved();
            }

            int penaltyCompare = Long.compare(a.getPenalty(), b.getPenalty());
            if (penaltyCompare != 0)
                return penaltyCompare;

            return Long.compare(a.getLastSolvedTime(), b.getLastSolvedTime());
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

    public EndContestResponse endContest(Long roomId, Long userId) {

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        if (!room.getHostUserId().equals(userId)) {
            throw new RuntimeException("Only host can end the contest");
        }

        if (!"ACTIVE".equalsIgnoreCase(room.getStatus())) {
            throw new RuntimeException("Contest not active");
        }

        // Use leaderboard instead of score
        List<LeaderboardResponse> leaderboard = getLeaderboard(roomId);

        Long winnerUserId = null;
        String result;

        if (leaderboard.isEmpty()) {
            result = "DRAW";
        } else {

            winnerUserId = leaderboard.get(0).getUserId();
            result = "WIN";

            // check for draw
            if (leaderboard.size() > 1) {

                LeaderboardResponse first = leaderboard.get(0);
                LeaderboardResponse second = leaderboard.get(1);

                if (first.getSolved().equals(second.getSolved()) &&
                        first.getPenalty().equals(second.getPenalty())) {

                    winnerUserId = null;
                    result = "DRAW";
                }
            }
        }

        // update room
        room.setStatus("FINISHED");
        room.setEndTime(System.currentTimeMillis());
        roomRepository.save(room);

        // save contest history
        for (LeaderboardResponse entry : leaderboard) {

            ContestHistory history = new ContestHistory();

            history.setRoomId(roomId);
            history.setUserId(entry.getUserId());
            history.setSolved(entry.getSolved());
            history.setPenalty(entry.getPenalty().intValue());
            history.setTimestamp(System.currentTimeMillis());

            if (winnerUserId == null) {
                history.setResult("DRAW");
            } else if (entry.getUserId().equals(winnerUserId)) {
                history.setResult("WIN");
            } else {
                history.setResult("LOSS");
            }

            contestHistoryRepository.save(history);
        }

        return new EndContestResponse(winnerUserId, result);
    }

    public Room createRoom(Long userId, String password) {

        Room room = new Room();

        room.setHostUserId(userId);

        // if host gives password -> use it
        // else -> auto generate
        if (password == null || password.isBlank()) {
            password = generatePassword();
        }

        room.setPassword(password);
        room.setStatus("WAITING");

        Room savedRoom = roomRepository.save(room);

        Participant participant = new Participant();
        participant.setRoomId(savedRoom.getId());
        participant.setUserId(userId);
        participant.setScore(0);

        participantRepository.save(participant);

        return savedRoom;
    }

    private String generatePassword() {
        return String.valueOf((int) (Math.random() * 9000) + 1000);
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
        room.setDuration(10 * 60 * 1000L);

        room.setStatus("ACTIVE");
        roomRepository.save(room);

        return "Contest started";
    }

    public RoomStateResponse getRoomState(Long roomId) {

        List<LeaderboardResponse> leaderboard = getLeaderboard(roomId);

        List<ParticipantProblem> all = participantProblemRepository.findByRoomId(roomId);

        // separate users
        Map<Long, List<ParticipantProblem>> map = new HashMap<>();

        for (ParticipantProblem pp : all) {
            map.computeIfAbsent(pp.getUserId(), k -> new ArrayList<>()).add(pp);
        }

        // get 2 users
        List<Long> users = new ArrayList<>(map.keySet());

        List<ParticipantProblem> user1 = map.getOrDefault(users.get(0), new ArrayList<>());
        List<ParticipantProblem> user2 = users.size() > 1
                ? map.getOrDefault(users.get(1), new ArrayList<>())
                : new ArrayList<>();

        return new RoomStateResponse(
                roomId,
                leaderboard,
                user1,
                user2);
    }

    public Map<String, Object> getUserStats(Long userId) {

        List<ContestHistory> history = contestHistoryRepository.findByUserId(userId);

        int total = history.size();
        int wins = 0;
        int losses = 0;
        int draws = 0;
        int totalSolved = 0;

        for (ContestHistory h : history) {

            totalSolved += h.getSolved();

            switch (h.getResult()) {
                case "WIN" -> wins++;
                case "LOSS" -> losses++;
                case "DRAW" -> draws++;
            }
        }

        double winRate = total == 0 ? 0 : (wins * 100.0) / total;

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalContests", total);
        stats.put("wins", wins);
        stats.put("losses", losses);
        stats.put("draws", draws);
        stats.put("totalSolved", totalSolved);
        stats.put("winRate", winRate);

        return stats;
    }

    public Map<String, Object> getUserProfile(Long userId) {

        List<ContestHistory> history = contestHistoryRepository.findByUserIdOrderByTimestampDesc(userId);

        int total = history.size();
        int wins = 0, losses = 0, draws = 0;
        int totalSolved = 0;

        for (ContestHistory h : history) {

            totalSolved += h.getSolved();

            switch (h.getResult()) {
                case "WIN" -> wins++;
                case "LOSS" -> losses++;
                case "DRAW" -> draws++;
            }
        }

        double winRate = total == 0 ? 0 : (wins * 100.0) / total;

        // last 5 contests
        List<ContestHistory> recent = history.stream().limit(5).toList();

        Map<String, Object> response = new HashMap<>();

        response.put("stats", Map.of(
                "totalContests", total,
                "wins", wins,
                "losses", losses,
                "draws", draws,
                "totalSolved", totalSolved,
                "winRate", winRate));

        response.put("recentMatches", recent);

        return response;
    }
}
