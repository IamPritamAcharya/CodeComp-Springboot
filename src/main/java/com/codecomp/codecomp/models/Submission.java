package com.codecomp.codecomp.models;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "submissions")
@Data
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private Long roomId;

    private Long problemId;

    @Column(columnDefinition = "TEXT")
    private String code;

    private String status;

    private Integer languageId;

    private Long createdAt;

}
