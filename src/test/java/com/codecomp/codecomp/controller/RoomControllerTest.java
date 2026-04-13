package com.codecomp.codecomp.controller;

import com.codecomp.codecomp.controllers.RoomController;
import com.codecomp.codecomp.dto.SubmissionRequest;
import com.codecomp.codecomp.models.Room;
import com.codecomp.codecomp.room.RoomService;
import com.codecomp.codecomp.submission.SubmissionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomControllerTest {

    @Mock
    private SubmissionService submissionService;

    @Mock
    private RoomService roomService;

    @InjectMocks
    private RoomController roomController;

    @Test
    void createRoom_shouldUseUserIdFromRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("userId", 7L);

        Room room = new Room();
        room.setId(1L);

        when(roomService.createRoom(7L, "1234")).thenReturn(room);

        ResponseEntity<?> response = roomController.createRoom("1234", request);

        assertSame(room, response.getBody());
        verify(roomService).createRoom(7L, "1234");
    }

    @Test
    void joinRoom_shouldUseUserIdFromRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("userId", 7L);

        when(roomService.joinRoom(1L, "1234", 7L)).thenReturn("Joined successfully");

        ResponseEntity<?> response = roomController.joinRoom(1L, "1234", request);

        assertEquals("Joined successfully", response.getBody());
        verify(roomService).joinRoom(1L, "1234", 7L);
    }

    @Test
    void submitCode_shouldInjectUserIdIntoRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("userId", 7L);

        SubmissionRequest submissionRequest = new SubmissionRequest();
        submissionRequest.setRoomId(1L);
        submissionRequest.setProblemId(101L);
        submissionRequest.setCode("print(1)");
        submissionRequest.setLanguageId(71);

        Map<String, Object> result = Map.of(
                "status", "PENDING",
                "submissionId", 99L);

        when(submissionService.submit(any(SubmissionRequest.class))).thenReturn(result);

        ResponseEntity<?> response = roomController.submitCode(submissionRequest, request);

        assertEquals(7L, submissionRequest.getUserId());
        assertSame(result, response.getBody());
        verify(submissionService).submit(submissionRequest);
    }
}