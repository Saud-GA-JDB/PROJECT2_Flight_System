package com.ga.notification.model.request;

public record EmailRequest(String recipient, String subject, String body) {}
