package com.codecomp.codecomp.worker;

import com.codecomp.codecomp.dto.RoomStateResponse;
import com.codecomp.codecomp.models.ParticipantProblem;
import com.codecomp.codecomp.models.Submission;
import com.codecomp.codecomp.models.TestCase;
import com.codecomp.codecomp.redis.RedisPublisher;
import com.codecomp.codecomp.repository.ParticipantProblemRepository;
import com.codecomp.codecomp.repository.SubmissionRepository;
import com.codecomp.codecomp.repository.TestCaseRepository;
import com.codecomp.codecomp.room.RoomService;
import com.codecomp.codecomp.submission.SubmissionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JudgeConsumerTest {

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private TestCaseRepository testCaseRepository;

    @Mock
    private ParticipantProblemRepository participantProblemRepository;

    @Mock
    private RoomService roomService;

    @Mock
    private RedisPublisher redisPublisher;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private SubmissionService submissionService;

    @InjectMocks
    private JudgeConsumer judgeConsumer;

    @Test
    void consume_shouldMarkAcceptedAndPublishRoomState() throws Exception {
        Submission submission = new Submission();
        submission.setId(1L);
        submission.setUserId(10L);
        submission.setRoomId(20L);
        submission.setProblemId(30L);
        submission.setCode("print(1)");
        submission.setLanguageId(71);
        submission.setStatus("PENDING");

        TestCase testCase = new TestCase();
        testCase.setProblemId(30L);
        testCase.setInput("input");
        testCase.setExpectedOutput("42");

        when(submissionRepository.findById(1L)).thenReturn(Optional.of(submission));
        when(testCaseRepository.findByProblemId(30L)).thenReturn(List.of(testCase));
        when(submissionService.runTestCase("print(1)", "input", 71)).thenReturn("42");
        when(participantProblemRepository.findByUserIdAndRoomIdAndProblemId(10L, 20L, 30L))
                .thenReturn(Optional.empty());
        when(roomService.getRoomStateInternal(20L)).thenReturn(new RoomStateResponse());
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(participantProblemRepository.save(any(ParticipantProblem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(submissionRepository.save(any(Submission.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        judgeConsumer.consume(1L);

        ArgumentCaptor<Submission> submissionCaptor = ArgumentCaptor.forClass(Submission.class);
        verify(submissionRepository).save(submissionCaptor.capture());
        assertEquals("Accepted", submissionCaptor.getValue().getStatus());

        ArgumentCaptor<ParticipantProblem> ppCaptor = ArgumentCaptor.forClass(ParticipantProblem.class);
        verify(participantProblemRepository).save(ppCaptor.capture());
        assertTrue(ppCaptor.getValue().getSolved());
        assertEquals(1, ppCaptor.getValue().getAttempts());

        verify(redisPublisher).publish("room:20", "{}");
    }
}