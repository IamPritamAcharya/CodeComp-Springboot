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

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final SubmissionService submissionService;
    private final RoomService roomService;

    @PostMapping("/create")
    public ResponseEntity<?> createRoom(
            @RequestParam(required = false) String password,
            HttpServletRequest request) {

        Long userId = (Long) request.getAttribute("userId");

        return ResponseEntity.ok(roomService.createRoom(userId, password));
    }

    @PostMapping("/join")
    public ResponseEntity<?> joinRoom(@RequestParam Long roomId,
            @RequestParam String password,
            HttpServletRequest request) {

        Long userId = (Long) request.getAttribute("userId");

        return ResponseEntity.ok(roomService.joinRoom(roomId, password, userId));
    }

    @PostMapping("/start")
    public ResponseEntity<?> startContest(@RequestParam Long roomId,
            HttpServletRequest request) {

        Long userId = (Long) request.getAttribute("userId");

        return ResponseEntity.ok(roomService.startContest(roomId, userId));
    }

    @PostMapping("/submit")
    public ResponseEntity<?> submitCode(@RequestBody SubmissionRequest req,
            HttpServletRequest request) {

        Long userId = (Long) request.getAttribute("userId");

        req.setUserId(userId);

        return ResponseEntity.ok(submissionService.submit(req));
    }

    @GetMapping("/leaderboard")
    public ResponseEntity<?> getLeaderboard(@RequestParam Long roomId) {
        return ResponseEntity.ok(roomService.getLeaderboard(roomId));
    }

    @PostMapping("/end")
    public ResponseEntity<?> endContest(@RequestParam Long roomId,
            HttpServletRequest request) {

        Long userId = (Long) request.getAttribute("userId");

        return ResponseEntity.ok(roomService.endContest(roomId, userId));
    }

    @GetMapping("/stats")
    public ResponseEntity<?> getStats(HttpServletRequest request) {

        Long userId = (Long) request.getAttribute("userId");

        return ResponseEntity.ok(roomService.getUserStats(userId));
    }

    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(HttpServletRequest request) {

        Long userId = (Long) request.getAttribute("userId");

        return ResponseEntity.ok(roomService.getUserProfile(userId));
    }
}