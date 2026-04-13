package com.codecomp.codecomp.repository;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Autowired;

@SpringBootTest
@ActiveProfiles("test")
class RepositoryTests {

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private ParticipantRepository participantRepository;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private RoomProblemRepository roomProblemRepository;

    @Autowired
    private ParticipantProblemRepository participantProblemRepository;

    @Autowired
    private ContestHistoryRepository contestHistoryRepository;

    @Autowired
    private TestCaseRepository testCaseRepository;

    @Autowired
    private UserRepository userRepository;

}