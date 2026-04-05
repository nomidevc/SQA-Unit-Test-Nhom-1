package com.doan.WEB_TMDT.module.accounting.controller;

import com.doan.WEB_TMDT.common.dto.ApiResponse;
import com.doan.WEB_TMDT.module.accounting.dto.TaxReportRequest;
import com.doan.WEB_TMDT.module.accounting.entity.TaxType;
import com.doan.WEB_TMDT.module.accounting.service.TaxReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/accounting/tax")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TaxReportController {

    private final TaxReportService taxReportService;

    @GetMapping("/reports")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<ApiResponse> getAllTaxReports() {
        return ResponseEntity.ok(taxReportService.getAllTaxReports());
    }

    @GetMapping("/reports/{type}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<ApiResponse> getTaxReportsByType(@PathVariable TaxType type) {
        return ResponseEntity.ok(taxReportService.getTaxReportsByType(type));
    }

    @GetMapping("/reports/detail/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<ApiResponse> getTaxReportById(@PathVariable Long id) {
        return ResponseEntity.ok(taxReportService.getTaxReportById(id));
    }

    @PostMapping("/reports")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<ApiResponse> createTaxReport(
            @RequestBody TaxReportRequest request,
            Authentication authentication
    ) {
        String createdBy = authentication.getName();
        return ResponseEntity.ok(taxReportService.createTaxReport(request, createdBy));
    }

    @PutMapping("/reports/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<ApiResponse> updateTaxReport(
            @PathVariable Long id,
            @RequestBody TaxReportRequest request
    ) {
        return ResponseEntity.ok(taxReportService.updateTaxReport(id, request));
    }

    @PostMapping("/reports/{id}/submit")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<ApiResponse> submitTaxReport(@PathVariable Long id) {
        return ResponseEntity.ok(taxReportService.submitTaxReport(id));
    }

    @PostMapping("/reports/{id}/mark-paid")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<ApiResponse> markAsPaid(@PathVariable Long id) {
        return ResponseEntity.ok(taxReportService.markAsPaid(id));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<ApiResponse> getTaxSummary() {
        return ResponseEntity.ok(taxReportService.getTaxSummary());
    }

    @GetMapping("/calculate-revenue")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<ApiResponse> calculateTaxableRevenue(
            @RequestParam String periodStart,
            @RequestParam String periodEnd
    ) {
        return ResponseEntity.ok(taxReportService.calculateTaxableRevenue(periodStart, periodEnd));
    }

    @PostMapping("/auto-create")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<ApiResponse> autoCreateTaxReport(
            @RequestParam String periodStart,
            @RequestParam String periodEnd,
            @RequestParam TaxType taxType,
            Authentication authentication
    ) {
        String createdBy = authentication.getName();
        return ResponseEntity.ok(taxReportService.autoCreateTaxReport(periodStart, periodEnd, taxType, createdBy));
    }

    @PostMapping("/reports/{id}/recalculate")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<ApiResponse> recalculateTaxReport(@PathVariable Long id) {
        return ResponseEntity.ok(taxReportService.recalculateTaxReport(id));
    }
}
