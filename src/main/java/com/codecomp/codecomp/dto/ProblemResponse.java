package com.codecomp.codecomp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ProblemResponse {
    private Long id;
    private String title;
    private String description;
    private String difficulty;
}