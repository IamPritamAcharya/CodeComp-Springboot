package com.codecomp.codecomp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SubmissionUpdate {

    private Long userId;
    private Long problemId;
    private String status;
    private Integer attempts;

}
