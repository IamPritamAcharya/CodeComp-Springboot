package com.codecomp.codecomp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class EndContestResponse {
    private Long winnerUserId;
    private String result;
}
