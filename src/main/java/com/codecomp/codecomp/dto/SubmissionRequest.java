package com.codecomp.codecomp.dto;

import lombok.Data;

@Data
public class SubmissionRequest {

    private Long userId;
    private Long roomId;
    private Long problemId;
    private String code;
    private Integer languageId;
}