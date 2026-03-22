package com.codecomp.codecomp.submission;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.stereotype.Service;

import com.codecomp.codecomp.dto.SubmissionRequest;
import com.codecomp.codecomp.models.Participant;
import com.codecomp.codecomp.models.Room;
import com.codecomp.codecomp.models.Submission;
import com.codecomp.codecomp.repository.ParticipantRepository;
import com.codecomp.codecomp.repository.RoomProblemRepository;
import com.codecomp.codecomp.repository.RoomRepository;
import com.codecomp.codecomp.repository.SubmissionRepository;

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

    public Map<String, Object> submit(SubmissionRequest req) throws Exception {

        Long userId = req.getUserId();
        Long roomId = req.getRoomId();
        Long problemId = req.getProblemId();
        String code = req.getCode();

        // Validate room
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        if (!"ACTIVE".equalsIgnoreCase(room.getStatus())) {
            throw new RuntimeException("Contest not active");
        }

        // Validate participant
        Participant participant = participantRepository.findByRoomId(roomId)
                .stream()
                .filter(p -> p.getUserId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("User not in room"));

        // Validate problem
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

        String statusDescription = pollResult(token);

        boolean alreadySolved = submissionRepository.findAll()
                .stream()
                .anyMatch(s -> s.getUserId().equals(userId)
                        && s.getProblemId().equals(problemId)
                        && "Accepted".equalsIgnoreCase(s.getStatus()));

        Submission submission = new Submission();
        submission.setUserId(userId);
        submission.setRoomId(roomId);
        submission.setProblemId(problemId);
        submission.setCode(code);
        submission.setStatus(statusDescription);

        submissionRepository.save(submission);

        if ("Accepted".equalsIgnoreCase(statusDescription) && !alreadySolved) {
            participant.setScore(participant.getScore() + 1);
            participantRepository.save(participant);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("status", statusDescription);
        result.put("score", participant.getScore());

        return result;
    }

    private String pollResult(String token) throws InterruptedException {

        for (int i = 0; i < 5; i++) {
            Thread.sleep(1000);

            String resultUrl = "http://localhost:2358/submissions/" + token + "?base64_encoded=false";

            Map<String, Object> result = restTemplate.getForObject(resultUrl, Map.class);

            if (result != null && result.get("status") != null) {
                Map<String, Object> status = (Map<String, Object>) result.get("status");
                String desc = (String) status.get("description");

                if (!"In Queue".equals(desc) && !"Processing".equals(desc)) {
                    return desc;
                }
            }
        }

        throw new RuntimeException("Judge0 timeout");
    }

}
