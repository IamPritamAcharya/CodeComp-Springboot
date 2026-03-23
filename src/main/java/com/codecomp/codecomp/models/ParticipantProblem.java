package com.codecomp.codecomp.models;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "participant_problems")
@Data
@NoArgsConstructor
public class ParticipantProblem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private Long roomId;

    private Long problemId;

    private Integer attempts = 0;

    private Integer penalty = 0;

    private Boolean solved = false;

    private Long solvedAt; 
}
