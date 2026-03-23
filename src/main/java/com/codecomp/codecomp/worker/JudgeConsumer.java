package com.codecomp.codecomp.worker;

import java.util.List;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import com.codecomp.codecomp.config.RabbitMQConfig;
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

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class JudgeConsumer {

    private final SubmissionRepository submissionRepository;
    private final TestCaseRepository testCaseRepository;
    private final ParticipantProblemRepository participantProblemRepository;
    private final RoomService roomService;
    private final RedisPublisher redisPublisher;
    private final ObjectMapper objectMapper;
    private final SubmissionService submissionService;

    @RabbitListener(queues = RabbitMQConfig.QUEUE)
    public void consume(Long submissionId) {

        try {
            System.out.println("RabbitMQ picked: " + submissionId);

            Submission submission = submissionRepository.findById(submissionId)
                    .orElseThrow();

            Long userId = submission.getUserId();
            Long roomId = submission.getRoomId();
            Long problemId = submission.getProblemId();
            String code = submission.getCode();
            Integer languageId = submission.getLanguageId();

            List<TestCase> testCases = testCaseRepository.findByProblemId(problemId);

            boolean allPassed = true;

            for (TestCase tc : testCases) {

                String output = submissionService.runTestCase(code, tc.getInput(), languageId);

                if (output == null ||
                        !output.trim().equals(tc.getExpectedOutput().trim())) {

                    allPassed = false;
                    break;
                }
            }

            String status = allPassed ? "Accepted" : "Wrong Answer";

            submission.setStatus(status);
            submissionRepository.save(submission);

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

            if (!"Accepted".equalsIgnoreCase(status)) {
                pp.setPenalty(pp.getPenalty() + 1);
            } else {
                if (!Boolean.TRUE.equals(pp.getSolved())) {
                    pp.setSolved(true);
                    pp.setSolvedAt(System.currentTimeMillis());
                }
            }

            participantProblemRepository.save(pp);

            RoomStateResponse state = roomService.getRoomState(roomId);
            String json = objectMapper.writeValueAsString(state);

            redisPublisher.publish("room:" + roomId, json);

            System.out.println("RabbitMQ finished: " + submissionId);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}