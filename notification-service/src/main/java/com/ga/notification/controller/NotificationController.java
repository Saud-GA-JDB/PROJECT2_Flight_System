package com.ga.notification.controller;

import com.ga.notification.service.NotificationService;
import com.ga.common.security.CurrentActor;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    @Operation(summary = "Subscribe to live notifications",
            description = "Active account required. Receives existing user/role events, a connected event, and heartbeat comments. Reconnect after five minutes; no replay. Use a streaming client supporting the Authorization header.")
    public ResponseEntity<SseEmitter> subscribe(@AuthenticationPrincipal CurrentActor actor) throws IOException {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-cache")
                .body(notificationService.subscribe(actor));
    }
}
