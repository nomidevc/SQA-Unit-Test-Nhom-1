package com.doan.WEB_TMDT.module.accounting.controller;

import com.doan.WEB_TMDT.common.dto.ApiResponse;
import com.doan.WEB_TMDT.module.accounting.dto.ShippingReconciliationResponse;
import com.doan.WEB_TMDT.module.accounting.service.ShippingReconciliationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/accounting/shipping-reconciliation")
@RequiredArgsConstructor
public class ShippingReconciliationController {
    
    private final ShippingReconciliationService shippingReconciliationService;
    
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or @employeeSecurityService.hasPosition(authentication, 'ACCOUNTANT')")
    public ResponseEntity<ApiResponse> getShippingReconciliation(
            @RequestParam String startDate,
            @RequestParam String endDate) {
        try {
            ShippingReconciliationResponse reconciliation = 
                shippingReconciliationService.generateReconciliation(startDate, endDate);
            return ResponseEntity.ok(ApiResponse.success("Tải dữ liệu đối soát thành công", reconciliation));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("Lỗi khi tải dữ liệu đối soát: " + e.getMessage()));
        }
    }
    
    @GetMapping("/export")
    @PreAuthorize("hasRole('ADMIN') or @employeeSecurityService.hasPosition(authentication, 'ACCOUNTANT')")
    public ResponseEntity<ApiResponse> exportShippingReconciliation(
            @RequestParam String startDate,
            @RequestParam String endDate) {
        try {
            // TODO: Implement Excel export functionality
            return ResponseEntity.ok(ApiResponse.success("Chức năng xuất Excel đang được phát triển", null));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("Lỗi khi xuất Excel: " + e.getMessage()));
        }
    }
}
