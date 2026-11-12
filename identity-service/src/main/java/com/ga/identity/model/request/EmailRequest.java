package com.ga.identity.model.request;

public record EmailRequest(String recipient, String subject, String body) {}
