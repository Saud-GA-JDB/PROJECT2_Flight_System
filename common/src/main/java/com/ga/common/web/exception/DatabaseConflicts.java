package com.ga.common.web.exception;

import java.sql.SQLException;

/** Database error classification shared by service-specific exception handlers. */
public final class DatabaseConflicts {
    private DatabaseConflicts() {}

    public static boolean isUniqueViolation(Throwable cause) {
        while (cause != null) {
            if (cause instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
