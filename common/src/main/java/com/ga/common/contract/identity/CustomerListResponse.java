package com.ga.common.contract.identity;

import java.util.List;

public record CustomerListResponse(
        List<CustomerResponse> content,
        int page,
        int size,
        long totalElements,
        long totalPages
) {}
