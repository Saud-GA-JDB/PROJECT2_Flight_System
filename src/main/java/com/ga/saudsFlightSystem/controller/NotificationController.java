package com.ga.saudsFlightSystem.controller;

import com.ga.saudsFlightSystem.service.NotificationService;
import com.ga.saudsFlightSystem.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notificationService;

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> subscribe() throws IOException {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-cache")
                .header("X-Accel-Buffering", "no")
                .body(notificationService.subscribe(UserService.getCurrentLoggedInUser()));
    }
}
