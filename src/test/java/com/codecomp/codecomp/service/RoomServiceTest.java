package com.codecomp.codecomp.service;

import com.codecomp.codecomp.dto.EndContestResponse;
import com.codecomp.codecomp.dto.LeaderboardResponse;
import com.codecomp.codecomp.dto.ProblemResponse;
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
import com.codecomp.codecomp.room.RoomService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private ParticipantRepository participantRepository;

    @Mock
    private ProblemRepository problemRepository;

    @Mock
    private RoomProblemRepository roomProblemRepository;

    @Mock
    private ParticipantProblemRepository participantProblemRepository;

    @Mock
    private ContestHistoryRepository contestHistoryRepository;

    @Spy
    @InjectMocks
    private RoomService roomService;

    @Test
    void createRoom_shouldSaveRoomAndHostParticipant() {
        Room savedRoom = new Room();
        savedRoom.setId(1L);
        savedRoom.setHostUserId(10L);
        savedRoom.setPassword("1234");
        savedRoom.setStatus("WAITING");

        when(roomRepository.save(any(Room.class))).thenReturn(savedRoom);

        Room result = roomService.createRoom(10L, "1234");

        assertEquals(1L, result.getId());
        assertEquals(10L, result.getHostUserId());
        assertEquals("WAITING", result.getStatus());
        assertEquals("1234", result.getPassword());

        ArgumentCaptor<Participant> participantCaptor = ArgumentCaptor.forClass(Participant.class);
        verify(participantRepository).save(participantCaptor.capture());
        assertEquals(10L, participantCaptor.getValue().getUserId());
        assertEquals(1L, participantCaptor.getValue().getRoomId());
        assertEquals(0, participantCaptor.getValue().getScore());
    }

    @Test
    void joinRoom_shouldJoinWhenEverythingIsValid() {
        Room room = new Room();
        room.setId(1L);
        room.setPassword("1234");
        room.setStatus("WAITING");

        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));
        when(participantRepository.findByRoomId(1L)).thenReturn(new ArrayList<>());
        when(participantRepository.findByUserId(20L)).thenReturn(new ArrayList<>());

        String result = roomService.joinRoom(1L, "1234", 20L);

        assertEquals("Joined successfully", result);
        verify(participantRepository).save(any(Participant.class));
    }

    @Test
    void joinRoom_shouldRejectWrongPassword() {
        Room room = new Room();
        room.setId(1L);
        room.setPassword("1234");
        room.setStatus("WAITING");

        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));

        String result = roomService.joinRoom(1L, "wrong", 20L);

        assertEquals("Wrong Password", result);
        verify(participantRepository, never()).save(any());
    }

    @Test
    void startContest_shouldAssignProblemsAndActivateRoom() {
        Room room = new Room();
        room.setId(1L);
        room.setHostUserId(10L);
        room.setStatus("WAITING");

        Participant p1 = new Participant();
        p1.setUserId(10L);
        p1.setRoomId(1L);

        Participant p2 = new Participant();
        p2.setUserId(20L);
        p2.setRoomId(1L);

        Problem pr1 = new Problem();
        pr1.setId(101L);
        pr1.setTitle("A");

        Problem pr2 = new Problem();
        pr2.setId(102L);
        pr2.setTitle("B");

        Problem pr3 = new Problem();
        pr3.setId(103L);
        pr3.setTitle("C");

        List<RoomProblem> savedRoomProblems = new ArrayList<>();

        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));
        when(participantRepository.findByRoomId(1L)).thenReturn(List.of(p1, p2));
        when(problemRepository.findAll()).thenReturn(List.of(pr1, pr2, pr3));
        when(roomProblemRepository.save(any(RoomProblem.class))).thenAnswer(invocation -> {
            RoomProblem rp = invocation.getArgument(0);
            savedRoomProblems.add(rp);
            return rp;
        });
        when(roomProblemRepository.findByRoomId(1L)).thenAnswer(invocation -> savedRoomProblems);
        when(participantProblemRepository.findByUserIdAndRoomIdAndProblemId(any(), any(), any()))
                .thenReturn(Optional.empty());

        String result = roomService.startContest(1L, 10L);

        assertEquals("Contest started", result);

        verify(roomProblemRepository, times(3)).save(any(RoomProblem.class));
        verify(participantProblemRepository, times(6)).save(any(ParticipantProblem.class));

        ArgumentCaptor<Room> roomCaptor = ArgumentCaptor.forClass(Room.class);
        verify(roomRepository).save(roomCaptor.capture());

        Room updated = roomCaptor.getValue();
        assertEquals("ACTIVE", updated.getStatus());
        assertNotNull(updated.getStartTime());
        assertEquals(10 * 60 * 1000L, updated.getDuration());
    }

    @Test
    void getLeaderboard_shouldSortBySolvedAndPenalty() {
        Room room = new Room();
        room.setId(1L);
        room.setStartTime(1000L);

        ParticipantProblem user1 = new ParticipantProblem();
        user1.setUserId(10L);
        user1.setRoomId(1L);
        user1.setProblemId(101L);
        user1.setSolved(true);
        user1.setSolvedAt(3000L);
        user1.setPenalty(0);

        ParticipantProblem user2 = new ParticipantProblem();
        user2.setUserId(20L);
        user2.setRoomId(1L);
        user2.setProblemId(101L);
        user2.setSolved(true);
        user2.setSolvedAt(5000L);
        user2.setPenalty(0);

        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));
        when(participantProblemRepository.findByRoomId(1L)).thenReturn(List.of(user1, user2));

        List<LeaderboardResponse> leaderboard = roomService.getLeaderboard(1L);

        assertEquals(2, leaderboard.size());
        assertEquals(10L, leaderboard.get(0).getUserId());
        assertEquals(20L, leaderboard.get(1).getUserId());
        assertEquals(1, leaderboard.get(0).getRank());
        assertEquals(2, leaderboard.get(1).getRank());
    }

    @Test
    void endContest_shouldFinishRoomAndSaveContestHistory() {
        Room room = new Room();
        room.setId(1L);
        room.setHostUserId(10L);
        room.setStatus("ACTIVE");

        LeaderboardResponse one = new LeaderboardResponse(10L, 1, 100L, 3000L, 1);
        LeaderboardResponse two = new LeaderboardResponse(20L, 1, 100L, 4000L, 2);

        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));
        doReturn(List.of(one, two)).when(roomService).getLeaderboard(1L);
        when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(contestHistoryRepository.save(any(ContestHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EndContestResponse response = roomService.endContest(1L, 10L);

        assertEquals("DRAW", response.getResult());
        assertEquals(null, response.getWinnerUserId());

        verify(roomRepository).save(any(Room.class));
        verify(contestHistoryRepository, times(2)).save(any(ContestHistory.class));
    }

    @Test
    void getUserStats_shouldAggregateCorrectly() {
        ContestHistory h1 = new ContestHistory();
        h1.setResult("WIN");
        h1.setSolved(2);

        ContestHistory h2 = new ContestHistory();
        h2.setResult("LOSS");
        h2.setSolved(1);

        ContestHistory h3 = new ContestHistory();
        h3.setResult("DRAW");
        h3.setSolved(0);

        when(contestHistoryRepository.findByUserId(10L)).thenReturn(List.of(h1, h2, h3));

        Map<String, Object> stats = roomService.getUserStats(10L);

        assertEquals(3, stats.get("totalContests"));
        assertEquals(1, stats.get("wins"));
        assertEquals(1, stats.get("losses"));
        assertEquals(1, stats.get("draws"));
        assertEquals(3, stats.get("totalSolved"));
        assertEquals(33.333333333333336, (Double) stats.get("winRate"));
    }

    @Test
    void getRoomProblems_shouldMapProblemResponses() {
        RoomProblem rp = new RoomProblem();
        rp.setProblemId(101L);

        Problem problem = new Problem();
        problem.setId(101L);
        problem.setTitle("Two Sum");
        problem.setDescription("Find sum");
        problem.setDifficulty("Easy");

        when(roomProblemRepository.findByRoomId(1L)).thenReturn(List.of(rp));
        when(problemRepository.findById(101L)).thenReturn(Optional.of(problem));

        List<ProblemResponse> responses = roomService.getRoomProblems(1L);

        assertEquals(1, responses.size());
        assertEquals(101L, responses.get(0).getId());
        assertEquals("Two Sum", responses.get(0).getTitle());
        assertEquals("Easy", responses.get(0).getDifficulty());
    }
}