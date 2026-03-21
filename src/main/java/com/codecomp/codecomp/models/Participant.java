package com.codecomp.codecomp.models;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "participants")
@Data
public class Participant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private Long roomId;

    private Integer score = 0;

}
