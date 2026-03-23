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
import com.codecomp.codecomp.models.Room;
import com.codecomp.codecomp.models.Submission;
import com.codecomp.codecomp.queue.JudgePublisher;
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

    private final ObjectMapper objectMapper;
    private final JudgePublisher judgePublisher;

    public Map<String, Object> submit(SubmissionRequest req) {

        Long userId = req.getUserId();
        Long roomId = req.getRoomId();
        Long problemId = req.getProblemId();
        String code = req.getCode();
        Integer languageId = req.getLanguageId();

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        if (!"ACTIVE".equalsIgnoreCase(room.getStatus())) {
            throw new RuntimeException("Contest not active");
        }

        participantRepository.findByRoomId(roomId)
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

        Submission submission = new Submission();
        submission.setUserId(userId);
        submission.setRoomId(roomId);
        submission.setProblemId(problemId);
        submission.setCode(code);
        submission.setStatus("PENDING");
        submission.setLanguageId(languageId);
        submission.setCreatedAt(System.currentTimeMillis());

        submissionRepository.save(submission);

        judgePublisher.send(submission.getId());

        Map<String, Object> result = new HashMap<>();
        result.put("status", "PENDING");

        return result;
    }

    public String runTestCase(String code, String input, Integer languageId) throws Exception {

        Map<String, Object> judgeRequest = new HashMap<>();
        judgeRequest.put("source_code", code);
        judgeRequest.put("language_id", languageId);
        judgeRequest.put("stdin", input);

        String jsonBody = objectMapper.writeValueAsString(judgeRequest);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);

        // Submit
        ResponseEntity<String> response = restTemplate.postForEntity(
                "http://localhost:2358/submissions?base64_encoded=false&wait=false",
                entity,
                String.class);

        Map<String, Object> responseMap = objectMapper.readValue(response.getBody(), Map.class);

        String token = (String) responseMap.get("token");

        // Poll with retry
        for (int i = 0; i < 5; i++) {

            Thread.sleep(1000);

            Map<String, Object> result = restTemplate.getForObject(
                    "http://localhost:2358/submissions/" + token + "?base64_encoded=false",
                    Map.class);

            Map<String, Object> status = (Map<String, Object>) result.get("status");

            String statusDesc = (String) status.get("description");

            if ("In Queue".equals(statusDesc) || "Processing".equals(statusDesc)) {
                continue;
            }

            // error cases
            if (!"Accepted".equalsIgnoreCase(statusDesc)) {
                return null;
            }

            return (String) result.get("stdout");
        }

        return null;
    }
}
