package com.codecomp.codecomp.submission;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
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
import com.codecomp.codecomp.rate.RateLimitService;
import com.codecomp.codecomp.repository.ParticipantRepository;
import com.codecomp.codecomp.repository.RoomProblemRepository;
import com.codecomp.codecomp.repository.RoomRepository;
import com.codecomp.codecomp.repository.SubmissionRepository;

import lombok.RequiredArgsConstructor;
import com.fasterxml.jackson.databind.ObjectMapper;

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
    private final RateLimitService rateLimitService;

    @Value("${judge0.url}")
    private String judgeUrl;

    public Map<String, Object> submit(SubmissionRequest req) {

        Long userId = req.getUserId();
        Long roomId = req.getRoomId();
        Long problemId = req.getProblemId();
        String code = req.getCode();
        Integer languageId = req.getLanguageId();

        rateLimitService.checkSubmissionLimit(userId);

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
        submission.setCreatedAt(LocalDateTime.now());

        submissionRepository.save(submission);

        // send to RabbitMQ queue
        judgePublisher.send(submission.getId());

        Map<String, Object> result = new HashMap<>();
        result.put("status", "PENDING");
        result.put("submissionId", submission.getId());

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

        ResponseEntity<String> response = restTemplate.postForEntity(
                judgeUrl + "/submissions?base64_encoded=false&wait=false",
                entity,
                String.class);

        Map<String, Object> responseMap = objectMapper.readValue(response.getBody(), Map.class);

        String token = (String) responseMap.get("token");

        int maxAttempts = 30;
        int attempts = 0;

        while (attempts++ < maxAttempts) {

            Thread.sleep(1000);

            Map<String, Object> result = restTemplate.getForObject(
                    judgeUrl + "/submissions/" + token + "?base64_encoded=false",
                    Map.class);

            Map<String, Object> status = (Map<String, Object>) result.get("status");
            String statusDesc = (String) status.get("description");

            System.out.println("Judge0 Status: " + statusDesc);

            if ("In Queue".equalsIgnoreCase(statusDesc) ||
                    "Processing".equalsIgnoreCase(statusDesc)) {
                continue;
            }

            if (!"Accepted".equalsIgnoreCase(statusDesc)) {
                System.out.println("Execution failed: " + statusDesc);
                return null;
            }

            Object stdoutObj = result.get("stdout");

            System.out.println("STDOUT: " + stdoutObj);

            return stdoutObj == null ? null : ((String) stdoutObj).trim();
        }

        return null;
    }
}