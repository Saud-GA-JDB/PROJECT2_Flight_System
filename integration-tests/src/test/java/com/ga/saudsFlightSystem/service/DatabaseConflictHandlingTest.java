package com.ga.saudsFlightSystem.service;

import com.ga.common.web.exception.DatabaseConflicts;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseConflictHandlingTest {
    @Test
    void classifiesOnlyUniqueViolationsIncludingWrappedCauses() {
        assertTrue(DatabaseConflicts.isUniqueViolation(new RuntimeException(new SQLException("duplicate", "23505"))));
        assertFalse(DatabaseConflicts.isUniqueViolation(new SQLException("foreign key", "23503")));
        assertFalse(DatabaseConflicts.isUniqueViolation(new RuntimeException("unrelated")));
        assertFalse(DatabaseConflicts.isUniqueViolation(null));
    }

    @Test
    void allDatabaseBackedServicesRetainConflictAndFallbackStatuses() {
        var aviation = new com.ga.saudsFlightSystem.exception.GlobalExceptionHandler();
        var identity = new com.ga.identity.exception.GlobalExceptionHandler();
        var maintenance = new com.ga.maintenance.exception.GlobalExceptionHandler();
        var request = new MockHttpServletRequest();
        for (String sqlState : new String[]{"23505", "23503"}) {
            var error = new DataIntegrityViolationException("test", new SQLException("test", sqlState));
            int expected = sqlState.equals("23505") ? 409 : 500;
            assertEquals(expected, aviation.handleDataIntegrity(error, request).getStatusCode().value());
            assertEquals(expected, identity.handleDataIntegrity(error, request).getStatusCode().value());
            assertEquals(expected, maintenance.dataConflict(error, request).getStatusCode().value());
        }
    }
}
