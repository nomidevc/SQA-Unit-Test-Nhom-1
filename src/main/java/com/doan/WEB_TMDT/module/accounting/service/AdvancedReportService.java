package com.doan.WEB_TMDT.module.accounting.service;

import com.doan.WEB_TMDT.module.accounting.dto.AdvancedReportRequest;
import com.doan.WEB_TMDT.module.accounting.dto.AdvancedReportResponse;

public interface AdvancedReportService {
    AdvancedReportResponse generateProfitLossReport(AdvancedReportRequest request);
    AdvancedReportResponse generateCashFlowReport(AdvancedReportRequest request);
    AdvancedReportResponse generateExpenseAnalysis(AdvancedReportRequest request);
}
