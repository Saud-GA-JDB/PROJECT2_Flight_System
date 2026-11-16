package com.ga.common.contract.notification;

public record EmailRequest(String recipient, String subject, String body) {}
