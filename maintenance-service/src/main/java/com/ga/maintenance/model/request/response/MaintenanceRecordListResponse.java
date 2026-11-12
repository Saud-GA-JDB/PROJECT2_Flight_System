package com.ga.maintenance.model.request.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class MaintenanceRecordListResponse {
    private List<MaintenanceRecordResponse> content;
    private int page;
    private int size;
    private long totalElements;
    private long totalPages;
}
