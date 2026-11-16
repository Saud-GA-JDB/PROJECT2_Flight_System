package com.ga.saudsFlightSystem.service;

import com.ga.common.web.Pagination;
import com.ga.common.web.exception.InvalidInformationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class PaginationTest {
    @ParameterizedTest
    @CsvSource({"0,20,0", "1,20,1", "20,20,1", "21,20,2", "9223372036854775807,2,4611686018427387904"})
    void countsPartialPagesWithoutOverflow(long total, int size, long expected) {
        assertEquals(expected, Pagination.totalPages(total, size));
    }

    @ParameterizedTest
    @CsvSource({"-1,20", "0,0", "0,-1", "0,101"})
    void rejectsOutOfBoundsZeroBasedRequests(int page, int size) {
        assertThrows(InvalidInformationException.class, () -> Pagination.validateZeroBased(page, size));
    }

    @Test
    void acceptsFirstPageAndLargestSupportedSize() {
        assertDoesNotThrow(() -> Pagination.validateZeroBased(0, 100));
    }
}
