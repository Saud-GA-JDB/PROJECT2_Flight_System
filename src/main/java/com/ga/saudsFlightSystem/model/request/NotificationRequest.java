package com.ga.saudsFlightSystem.model.request;

public record NotificationRequest(Long userId, String role, String eventName, Object data) {}
