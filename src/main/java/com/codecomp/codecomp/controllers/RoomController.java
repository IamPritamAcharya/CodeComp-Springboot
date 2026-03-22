package com.codecomp.codecomp.controllers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.codecomp.codecomp.dto.EndContestResponse;
import com.codecomp.codecomp.dto.LeaderboardResponse;
import com.codecomp.codecomp.dto.SubmissionRequest;
import com.codecomp.codecomp.models.Participant;
import com.codecomp.codecomp.models.Problem;
import com.codecomp.codecomp.models.Room;
import com.codecomp.codecomp.models.RoomProblem;
import com.codecomp.codecomp.models.Submission;
import com.codecomp.codecomp.repository.ParticipantRepository;
import com.codecomp.codecomp.repository.ProblemRepository;
import com.codecomp.codecomp.repository.RoomProblemRepository;
import com.codecomp.codecomp.repository.RoomRepository;
import com.codecomp.codecomp.repository.SubmissionRepository;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/rooms")
@RequiredArgsConstructor // lombok creates constrcutor for final feidls
public class RoomController {

    private final RoomRepository roomRepository;
    private final ParticipantRepository participantRepository;
    private final ProblemRepository problemRepository;
    private final RoomProblemRepository roomProblemRepository;
    private final SubmissionRepository submissionRepository;
    private final RestTemplate restTemplate;

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

    @PostMapping("/join")
    public String joinRoom(@RequestParam Long roomId,
            @RequestParam String password,
            @RequestParam Long userId) {

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

    @PostMapping("/start")
    public String startContest(@RequestParam Long roomId,
            @RequestParam Long userId) {

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

    @PostMapping("/submit")
    public ResponseEntity<?> submitCode(@RequestBody SubmissionRequest req) {

        // 1. Validate input
        if (req.getUserId() == null || req.getRoomId() == null ||
                req.getProblemId() == null || req.getCode() == null || req.getCode().isBlank()) {
            return ResponseEntity.badRequest().body("Invalid request");
        }

        Long userId = req.getUserId();
        Long roomId = req.getRoomId();
        Long problemId = req.getProblemId();
        String code = req.getCode();

        // 2. Validate room
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        if (!"ACTIVE".equalsIgnoreCase(room.getStatus())) {
            return ResponseEntity.badRequest().body("Contest is not active");
        }

        // 3. Validate participant
        Participant participant = participantRepository.findByRoomId(roomId)
                .stream()
                .filter(p -> p.getUserId().equals(userId))
                .findFirst()
                .orElse(null);

        if (participant == null) {
            return ResponseEntity.badRequest().body("User not in room");
        }

        // 4. Validate problem belongs to room
        boolean validProblem = roomProblemRepository.findAll()
                .stream()
                .anyMatch(rp -> rp.getRoomId().equals(roomId) &&
                        rp.getProblemId().equals(problemId));

        if (!validProblem) {
            return ResponseEntity.badRequest().body("Problem not part of this contest");
        }

        try {
            ObjectMapper objectMapper = new ObjectMapper();

            // 5. Prepare Judge0 request
            Map<String, Object> judgeRequest = new HashMap<>();
            judgeRequest.put("source_code", code);
            judgeRequest.put("language_id", 62);
            judgeRequest.put("stdin", "");

            String jsonBody = objectMapper.writeValueAsString(judgeRequest);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);

            // 6. Submit to Judge0
            ResponseEntity<String> responseEntity = restTemplate.postForEntity(
                    "http://localhost:2358/submissions",
                    entity,
                    String.class);

            Map<String, Object> response = objectMapper.readValue(responseEntity.getBody(), Map.class);

            String token = (String) response.get("token");

            if (token == null) {
                return ResponseEntity.internalServerError().body("Judge0 token missing");
            }

            // 7. Poll result (retry instead of sleep)
            Map<String, Object> result = null;
            String statusDescription = null;

            for (int i = 0; i < 5; i++) {
                Thread.sleep(1000);

                String resultUrl = "http://localhost:2358/submissions/" + token + "?base64_encoded=false";
                result = restTemplate.getForObject(resultUrl, Map.class);

                if (result != null && result.get("status") != null) {
                    Map<String, Object> status = (Map<String, Object>) result.get("status");
                    statusDescription = (String) status.get("description");

                    if (!"In Queue".equals(statusDescription) &&
                            !"Processing".equals(statusDescription)) {
                        break;
                    }
                }
            }

            if (statusDescription == null) {
                return ResponseEntity.internalServerError().body("Failed to fetch result");
            }

            // 8. Prevent duplicate AC scoring
            boolean alreadySolved = submissionRepository.findAll()
                    .stream()
                    .anyMatch(s -> s.getUserId().equals(userId)
                            && s.getProblemId().equals(problemId)
                            && "Accepted".equalsIgnoreCase(s.getStatus()));

            // 9. Save submission
            Submission submission = new Submission();
            submission.setUserId(userId);
            submission.setRoomId(roomId);
            submission.setProblemId(problemId);
            submission.setCode(code);
            submission.setStatus(statusDescription);

            submissionRepository.save(submission);

            // 10. Update score (only first AC)
            if ("Accepted".equalsIgnoreCase(statusDescription) && !alreadySolved) {
                participant.setScore(participant.getScore() + 1);
                participantRepository.save(participant);
            }

            // 11. Response
            Map<String, Object> apiResponse = new HashMap<>();
            apiResponse.put("status", statusDescription);
            apiResponse.put("score", participant.getScore());

            return ResponseEntity.ok(apiResponse);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Submission failed");
        }
    }

    @GetMapping("/leaderboard")
    public ResponseEntity<?> getLeaderboard(@RequestParam Long roomId) {

        // validate room
        Room room = roomRepository.findById(roomId).orElse(null);

        if (room == null) {
            return ResponseEntity.badRequest().body("Room not found");
        }

        List<Participant> participants = participantRepository.findByRoomId(roomId);

        if (participants.isEmpty()) {
            return ResponseEntity.ok(Collections.emptyList());
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

        return ResponseEntity.ok(leaderboard);
    }

    @PostMapping("/end")
    public ResponseEntity<?> endContest(@RequestParam Long roomId) {

        // validate room
        Room room = roomRepository.findById(roomId).orElse(null);

        if (room == null) {
            return ResponseEntity.badRequest().body("Room not found");
        }

        if (!"ACTIVE".equalsIgnoreCase(room.getStatus())) {
            return ResponseEntity.badRequest().body("Contest not active");
        }

        List<Participant> participants = participantRepository.findByRoomId(roomId);

        if (participants.size() != 2) {
            return ResponseEntity.badRequest().body("Invalid participant count");
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

        EndContestResponse response = new EndContestResponse(winnerUserId, result);

        return ResponseEntity.ok(response);

    }
}
