package com.codecomp.codecomp.room;

import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.codecomp.codecomp.models.Room;
import com.codecomp.codecomp.repository.RoomRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ContestScheduler {

    private final RoomRepository roomRepository;
    private final RoomService roomService; 

    @Scheduled(fixedRate = 5000) // every 5 seconds
    public void checkAndEndContests() {

        List<Room> activeRooms = roomRepository.findAll()
                .stream()
                .filter(r -> "ACTIVE".equals(r.getStatus()))
                .toList();

        long now = System.currentTimeMillis();

        for (Room room : activeRooms) {

            if (room.getStartTime() == null || room.getDuration() == null) {
                continue;
            }

            long endTime = room.getStartTime() + room.getDuration();

            if (now >= endTime) {

                // call actual business logic
                roomService.endContest(room.getId(), room.getHostUserId());

                System.out.println("Contest auto-ended for room: " + room.getId());
            }
        }
    }
}