package com.ga.common.events;

public record NotificationEvent(
        String eventId,
        Long userId,
        String role,
        String eventName,
        Object data
) {}
