package com.codecomp.codecomp.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.codecomp.codecomp.dto.SubmissionRequest;
import com.codecomp.codecomp.room.RoomService;
import com.codecomp.codecomp.submission.SubmissionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final SubmissionService submissionService;
    private final RoomService roomService;

    @PostMapping("/create")
    public ResponseEntity<?> createRoom(@RequestParam Long userId) {
        return ResponseEntity.ok(roomService.createRoom(userId));
    }

    @PostMapping("/join")
    public ResponseEntity<?> joinRoom(@RequestParam Long roomId,
            @RequestParam String password,
            @RequestParam Long userId) {
        return ResponseEntity.ok(roomService.joinRoom(roomId, password, userId));
    }

    @PostMapping("/start")
    public ResponseEntity<?> startContest(@RequestParam Long roomId,
            @RequestParam Long userId) {
        return ResponseEntity.ok(roomService.startContest(roomId, userId));
    }

    @PostMapping("/submit")
    public ResponseEntity<?> submitCode(@RequestBody SubmissionRequest req) {
        return ResponseEntity.ok(submissionService.submit(req));
    }

    @GetMapping("/leaderboard")
    public ResponseEntity<?> getLeaderboard(@RequestParam Long roomId) {
        return ResponseEntity.ok(roomService.getLeaderboard(roomId));
    }

    @PostMapping("/end")
    public ResponseEntity<?> endContest(@RequestParam Long roomId) {
        return ResponseEntity.ok(roomService.endContest(roomId));
    }
}