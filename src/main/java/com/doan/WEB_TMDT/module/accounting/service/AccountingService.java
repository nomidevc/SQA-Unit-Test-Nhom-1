package com.doan.WEB_TMDT.module.accounting.service;

import com.doan.WEB_TMDT.common.dto.ApiResponse;
import com.doan.WEB_TMDT.module.accounting.dto.ReconciliationRequest;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

public interface AccountingService {
    ApiResponse getStats();
    ApiResponse getDashboardStats();
    ApiResponse getRecentOrders(int limit);
    ApiResponse getPaymentReconciliation(ReconciliationRequest request);
    ApiResponse importReconciliationFile(MultipartFile file, String gateway);
    ApiResponse getShippingReconciliation(LocalDate startDate, LocalDate endDate);
    ApiResponse getFinancialReports(LocalDate startDate, LocalDate endDate, String viewMode);
    ApiResponse exportReports(LocalDate startDate, LocalDate endDate);
    ApiResponse getAllPeriods();
    ApiResponse closePeriod(Long id);
    ApiResponse reopenPeriod(Long id);
    ApiResponse exportShippingReconciliation(LocalDate startDate, LocalDate endDate);
}
