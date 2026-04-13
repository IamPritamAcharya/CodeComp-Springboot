package com.codecomp.codecomp.service;

import com.codecomp.codecomp.dto.SubmissionRequest;
import com.codecomp.codecomp.models.Participant;
import com.codecomp.codecomp.models.Room;
import com.codecomp.codecomp.models.RoomProblem;
import com.codecomp.codecomp.models.Submission;
import com.codecomp.codecomp.queue.JudgePublisher;
import com.codecomp.codecomp.rate.RateLimitService;
import com.codecomp.codecomp.repository.ParticipantRepository;
import com.codecomp.codecomp.repository.RoomProblemRepository;
import com.codecomp.codecomp.repository.RoomRepository;
import com.codecomp.codecomp.repository.SubmissionRepository;
import com.codecomp.codecomp.submission.SubmissionService;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubmissionServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private ParticipantRepository participantRepository;

    @Mock
    private RoomProblemRepository roomProblemRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private JudgePublisher judgePublisher;

    @Mock
    private RateLimitService rateLimitService;

    @InjectMocks
    private SubmissionService submissionService;

    @Mock
    private ObjectMapper objectMapper;

  

    @Test
    void submit_shouldPersistAndQueueSubmission() {
        SubmissionRequest req = new SubmissionRequest();
        req.setUserId(7L);
        req.setRoomId(1L);
        req.setProblemId(101L);
        req.setCode("print(1)");
        req.setLanguageId(71);

        Room room = new Room();
        room.setId(1L);
        room.setStatus("ACTIVE");

        Participant participant = new Participant();
        participant.setUserId(7L);
        participant.setRoomId(1L);

        RoomProblem roomProblem = new RoomProblem();
        roomProblem.setRoomId(1L);
        roomProblem.setProblemId(101L);

        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));
        when(participantRepository.findByRoomId(1L)).thenReturn(List.of(participant));
        when(roomProblemRepository.findAll()).thenReturn(List.of(roomProblem));
        doNothing().when(rateLimitService).checkSubmissionLimit(7L);

        when(submissionRepository.save(any(Submission.class))).thenAnswer(invocation -> {
            Submission submission = invocation.getArgument(0);
            submission.setId(99L);
            submission.setCreatedAt(LocalDateTime.now());
            return submission;
        });

        Map<String, Object> result = submissionService.submit(req);

        assertEquals("PENDING", result.get("status"));
        assertEquals(99L, result.get("submissionId"));

        ArgumentCaptor<Submission> submissionCaptor = ArgumentCaptor.forClass(Submission.class);
        verify(submissionRepository).save(submissionCaptor.capture());

        Submission saved = submissionCaptor.getValue();
        assertEquals(7L, saved.getUserId());
        assertEquals(1L, saved.getRoomId());
        assertEquals(101L, saved.getProblemId());
        assertEquals("PENDING", saved.getStatus());

        verify(judgePublisher).send(99L);
    }

    @Test
    void submit_shouldRejectInvalidProblem() {
        SubmissionRequest req = new SubmissionRequest();
        req.setUserId(7L);
        req.setRoomId(1L);
        req.setProblemId(999L);
        req.setCode("print(1)");
        req.setLanguageId(71);

        Room room = new Room();
        room.setId(1L);
        room.setStatus("ACTIVE");

        Participant participant = new Participant();
        participant.setUserId(7L);
        participant.setRoomId(1L);

        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));
        when(participantRepository.findByRoomId(1L)).thenReturn(List.of(participant));
        when(roomProblemRepository.findAll()).thenReturn(List.of(new RoomProblem()));
        doNothing().when(rateLimitService).checkSubmissionLimit(7L);

        assertThrows(RuntimeException.class, () -> submissionService.submit(req));
    }

    @Test
    void runTestCase_shouldReturnAcceptedStdout() throws Exception {
        Map<String, Object> postResponse = new HashMap<>();
        postResponse.put("token", "abc123");

        Map<String, Object> status = new HashMap<>();
        status.put("description", "Accepted");

        Map<String, Object> getResponse = new HashMap<>();
        getResponse.put("status", status);
        getResponse.put("stdout", " 42 \n");

        when(objectMapper.writeValueAsString(any()))
                .thenReturn("{\"token\":\"abc123\"}");

        when(restTemplate.postForEntity(any(String.class), any(), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{\"token\":\"abc123\"}"));

        when(objectMapper.readValue(any(String.class), eq(Map.class)))
                .thenReturn(getResponse);

        when(restTemplate.getForObject(any(String.class), eq(Map.class)))
                .thenReturn(getResponse);

        String output = submissionService.runTestCase("print(1)", "", 71);

        assertEquals("42", output);
    }
}