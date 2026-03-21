package com.codecomp.codecomp.models;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "rooms")
@Data
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long hostUserId;

    private String password;

    private String status;
}
