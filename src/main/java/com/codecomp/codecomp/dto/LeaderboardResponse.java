package com.codecomp.codecomp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LeaderboardResponse {

    private Long userId;

    private Integer solved;   // number of solved problems

    private Integer penalty;  

    private Integer rank;
}