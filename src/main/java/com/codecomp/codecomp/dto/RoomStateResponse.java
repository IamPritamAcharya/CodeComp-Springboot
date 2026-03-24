package com.codecomp.codecomp.dto;

import java.util.List;

import com.codecomp.codecomp.models.ParticipantProblem;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class RoomStateResponse {
    private Long roomId;
    private List<LeaderboardResponse> leaderboard;

    private List<ParticipantProblem> myProblems;
    private List<ParticipantProblem> opponentProblems;

}
