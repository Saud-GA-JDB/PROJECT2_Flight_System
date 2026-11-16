package com.ga.common.web;

import com.ga.common.web.exception.InvalidInformationException;

public final class Pagination {
    private Pagination() {}

    public static void validateZeroBased(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new InvalidInformationException("Page must be nonnegative and size must be between 1 and 100.");
        }
    }

    /** Returns the page count for a nonnegative total and a validated positive page size. */
    public static long totalPages(long totalElements, int size) {
        return totalElements / size + (totalElements % size == 0 ? 0 : 1);
    }
}
