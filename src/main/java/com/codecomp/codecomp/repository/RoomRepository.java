package com.codecomp.codecomp.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.codecomp.codecomp.models.Room;

public interface RoomRepository extends JpaRepository<Room, Long> {

}
