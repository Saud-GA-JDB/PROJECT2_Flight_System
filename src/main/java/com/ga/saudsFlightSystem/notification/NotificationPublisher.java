package com.ga.saudsFlightSystem.notification;

public interface NotificationPublisher {
    void sendToUser(Long userId, String eventName, Object data);

    void sendToRole(String role, String eventName, Object data);
}
