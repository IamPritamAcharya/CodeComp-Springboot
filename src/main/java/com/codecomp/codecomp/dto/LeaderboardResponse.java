package com.codecomp.codecomp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LeaderboardResponse {

    private Long userId;

    private Integer solved; // number of solved problems

    private Long penalty;



    private Long lastSolvedTime;

    private Integer rank;
}