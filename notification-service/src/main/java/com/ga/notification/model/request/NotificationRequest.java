package com.ga.notification.model.request;

public record NotificationRequest(Long userId, String role, String eventName, Object data) {}
