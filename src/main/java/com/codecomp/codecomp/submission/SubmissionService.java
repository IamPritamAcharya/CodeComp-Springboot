package com.codecomp.codecomp.submission;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.client.RestTemplate;
import org.springframework.stereotype.Service;

import com.codecomp.codecomp.dto.SubmissionRequest;
import com.codecomp.codecomp.dto.SubmissionUpdate;
import com.codecomp.codecomp.models.Participant;
import com.codecomp.codecomp.models.ParticipantProblem;
import com.codecomp.codecomp.models.Room;
import com.codecomp.codecomp.models.Submission;
import com.codecomp.codecomp.models.TestCase;
import com.codecomp.codecomp.repository.ParticipantProblemRepository;
import com.codecomp.codecomp.repository.ParticipantRepository;
import com.codecomp.codecomp.repository.RoomProblemRepository;
import com.codecomp.codecomp.repository.RoomRepository;
import com.codecomp.codecomp.repository.SubmissionRepository;
import com.codecomp.codecomp.repository.TestCaseRepository;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class SubmissionService {

    private final RoomRepository roomRepository;
    private final ParticipantRepository participantRepository;
    private final RoomProblemRepository roomProblemRepository;
    private final SubmissionRepository submissionRepository;
    private final RestTemplate restTemplate;
    private final ParticipantProblemRepository participantProblemRepository;
    private final TestCaseRepository testCaseRepository;

    private final SimpMessagingTemplate messagingTemplate;

    public Map<String, Object> submit(SubmissionRequest req) throws Exception {

        Long userId = req.getUserId();
        Long roomId = req.getRoomId();
        Long problemId = req.getProblemId();
        String code = req.getCode();

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        if (!"ACTIVE".equalsIgnoreCase(room.getStatus())) {
            throw new RuntimeException("Contest not active");
        }

        Participant participant = participantRepository.findByRoomId(roomId)
                .stream()
                .filter(p -> p.getUserId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("User not in room"));

        boolean validProblem = roomProblemRepository.findAll()
                .stream()
                .anyMatch(rp -> rp.getRoomId().equals(roomId) &&
                        rp.getProblemId().equals(problemId));

        if (!validProblem) {
            throw new RuntimeException("Invalid problem");
        }

        ObjectMapper objectMapper = new ObjectMapper();

        Map<String, Object> judgeRequest = new HashMap<>();
        judgeRequest.put("source_code", code);
        judgeRequest.put("language_id", 62);
        judgeRequest.put("stdin", "");

        String jsonBody = objectMapper.writeValueAsString(judgeRequest);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);

        ResponseEntity<String> responseEntity = restTemplate.postForEntity(
                "http://localhost:2358/submissions",
                entity,
                String.class);

        Map<String, Object> response = objectMapper.readValue(responseEntity.getBody(), Map.class);

        String token = (String) response.get("token");

        List<TestCase> testCases = testCaseRepository.findByProblemId(problemId);

        boolean allPassed = true;

        for (TestCase tc : testCases) {
            String output = runCode(code, tc.getInput());

            if (output == null || !output.trim().equals(tc.getExpectedOutput().trim())) {
                allPassed = false;
                break;
            }
        }

        String statusDescription = allPassed ? "Accepted" : "Wrong Answer";

        // Save submission
        Submission submission = new Submission();
        submission.setUserId(userId);
        submission.setRoomId(roomId);
        submission.setProblemId(problemId);
        submission.setCode(code);
        submission.setStatus(statusDescription);

        submission.setCreatedAt(System.currentTimeMillis());

        submissionRepository.save(submission);

        // NEW LOGIC: ParticipantProblem tracking
        ParticipantProblem pp = participantProblemRepository
                .findByUserIdAndRoomIdAndProblemId(userId, roomId, problemId)
                .orElseGet(() -> {
                    ParticipantProblem newPP = new ParticipantProblem();
                    newPP.setUserId(userId);
                    newPP.setRoomId(roomId);
                    newPP.setProblemId(problemId);
                    return newPP;
                });

        pp.setAttempts(pp.getAttempts() + 1);

        if (!"Accepted".equalsIgnoreCase(statusDescription)) {
            pp.setPenalty(pp.getPenalty() + 1);
        } else {
            if (!Boolean.TRUE.equals(pp.getSolved())) {
                pp.setSolved(true);
                pp.setSolvedAt(System.currentTimeMillis());
            }
        }

        participantProblemRepository.save(pp);

        SubmissionUpdate update = new SubmissionUpdate(
                userId,
                problemId,
                statusDescription,
                pp.getAttempts());

        System.out.println("Sending WS update: " + update);
        System.out.println("RoomId: " + roomId);

        messagingTemplate.convertAndSend(
                "/topic/room/" + roomId,
                update);

        Map<String, Object> result = new HashMap<>();
        result.put("status", statusDescription);
        result.put("attempts", pp.getAttempts());
        result.put("penalty", pp.getPenalty());
        result.put("solved", pp.getSolved());

        return result;
    }

    private String runCode(String code, String input) throws Exception {

        ObjectMapper objectMapper = new ObjectMapper();

        Map<String, Object> judgeRequest = new HashMap<>();
        judgeRequest.put("source_code", code);
        judgeRequest.put("language_id", 62);
        judgeRequest.put("stdin", input);

        String jsonBody = objectMapper.writeValueAsString(judgeRequest);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);

        ResponseEntity<String> responseEntity = restTemplate.postForEntity(
                "http://localhost:2358/submissions",
                entity,
                String.class);

        Map<String, Object> response = objectMapper.readValue(responseEntity.getBody(), Map.class);

        String token = (String) response.get("token");

        Map<String, Object> result = null;

        for (int i = 0; i < 5; i++) {
            Thread.sleep(1000);

            result = restTemplate.getForObject(
                    "http://localhost:2358/submissions/" + token + "?base64_encoded=false",
                    Map.class);

            if (result != null && result.get("stdout") != null) {
                break;
            }
        }

        return (String) result.get("stdout");
    }

}
