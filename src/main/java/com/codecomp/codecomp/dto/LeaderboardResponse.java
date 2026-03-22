package com.codecomp.codecomp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LeaderboardResponse {

    private Long userId;
    private Integer score;
    private Integer rank;
}
