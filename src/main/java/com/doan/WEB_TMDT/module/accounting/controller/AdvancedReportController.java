package com.doan.WEB_TMDT.module.accounting.controller;

import com.doan.WEB_TMDT.common.dto.ApiResponse;
import com.doan.WEB_TMDT.module.accounting.dto.AdvancedReportRequest;
import com.doan.WEB_TMDT.module.accounting.dto.AdvancedReportResponse;
import com.doan.WEB_TMDT.module.accounting.service.AdvancedReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/accounting/reports")
@RequiredArgsConstructor
public class AdvancedReportController {
    
    private final AdvancedReportService advancedReportService;
    
    @PostMapping("/profit-loss")
    @PreAuthorize("hasRole('ADMIN') or @employeeSecurityService.hasPosition(authentication, 'ACCOUNTANT')")
    public ResponseEntity<ApiResponse> generateProfitLossReport(
            @RequestBody AdvancedReportRequest request) {
        try {
            AdvancedReportResponse report = advancedReportService.generateProfitLossReport(request);
            return ResponseEntity.ok(ApiResponse.success("Tạo báo cáo lãi lỗ thành công", report));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("Lỗi khi tạo báo cáo: " + e.getMessage()));
        }
    }
    
    @PostMapping("/cash-flow")
    @PreAuthorize("hasRole('ADMIN') or @employeeSecurityService.hasPosition(authentication, 'ACCOUNTANT')")
    public ResponseEntity<ApiResponse> generateCashFlowReport(
            @RequestBody AdvancedReportRequest request) {
        try {
            AdvancedReportResponse report = advancedReportService.generateCashFlowReport(request);
            return ResponseEntity.ok(ApiResponse.success("Tạo báo cáo dòng tiền thành công", report));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("Lỗi khi tạo báo cáo: " + e.getMessage()));
        }
    }
    
    @PostMapping("/expense-analysis")
    @PreAuthorize("hasRole('ADMIN') or @employeeSecurityService.hasPosition(authentication, 'ACCOUNTANT')")
    public ResponseEntity<ApiResponse> generateExpenseAnalysis(
            @RequestBody AdvancedReportRequest request) {
        try {
            AdvancedReportResponse report = advancedReportService.generateExpenseAnalysis(request);
            return ResponseEntity.ok(ApiResponse.success("Tạo phân tích chi phí thành công", report));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("Lỗi khi tạo báo cáo: " + e.getMessage()));
        }
    }
}
