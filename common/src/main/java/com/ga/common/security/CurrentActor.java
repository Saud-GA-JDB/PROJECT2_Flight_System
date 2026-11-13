package com.ga.common.security;

/** Account information for the authenticated caller, without persistence relationships. */
public record CurrentActor(Long userId, String role, String status, boolean active) {
}
