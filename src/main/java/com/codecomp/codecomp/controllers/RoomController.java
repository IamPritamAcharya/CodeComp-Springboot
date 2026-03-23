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
@RequiredArgsConstructor // lombok creates constrcutor for final feidls
public class RoomController {

    private final SubmissionService submissionService;
    private final RoomService roomService;

    @PostMapping("/create")
    public ResponseEntity<?> createRoom(@RequestParam Long userId) {
        try {
            return ResponseEntity.ok(roomService.createRoom(userId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/join")
    public ResponseEntity<?> joinRoom(@RequestParam Long roomId,
            @RequestParam String password,
            @RequestParam Long userId) {
        try {
            return ResponseEntity.ok(roomService.joinRoom(roomId, password, userId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/start")
    public ResponseEntity<?> startContest(@RequestParam Long roomId,
            @RequestParam Long userId) {
        try {
            return ResponseEntity.ok(roomService.startContest(roomId, userId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/submit")
    public ResponseEntity<?> submitCode(@RequestBody SubmissionRequest req) {
        try {
            return ResponseEntity.ok(submissionService.submit(req));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Internal server error");
        }
    }

    @GetMapping("/leaderboard")
    public ResponseEntity<?> getLeaderboard(@RequestParam Long roomId) {
        try {
            return ResponseEntity.ok(roomService.getLeaderboard(roomId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }

    }

    @PostMapping("/end")
    public ResponseEntity<?> endContest(@RequestParam Long roomId) {
        try {
            return ResponseEntity.ok(roomService.endContest(roomId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
