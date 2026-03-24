package com.codecomp.codecomp.models;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "contest_history")
@Data
public class ContestHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Long roomId;

    private Integer solved;
    private Integer penalty;

    private String result;

    private Long timestamp;
}