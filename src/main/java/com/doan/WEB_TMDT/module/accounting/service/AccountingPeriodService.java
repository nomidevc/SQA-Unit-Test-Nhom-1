package com.doan.WEB_TMDT.module.accounting.service;

import com.doan.WEB_TMDT.common.dto.ApiResponse;

public interface AccountingPeriodService {
    ApiResponse getAllPeriods();

    ApiResponse getPeriodById(Long id);

    ApiResponse getPeriodDetails(Long id); // NEW: Chi tiết đầy đủ của kỳ

    ApiResponse createPeriod(String name, String startDate, String endDate);

    ApiResponse closePeriod(Long id, String closedBy);

    ApiResponse reopenPeriod(Long id);

    ApiResponse calculatePeriodStats(Long id);
}
