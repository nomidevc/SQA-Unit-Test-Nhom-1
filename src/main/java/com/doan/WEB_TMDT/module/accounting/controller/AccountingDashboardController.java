package com.doan.WEB_TMDT.module.accounting.controller;

import com.doan.WEB_TMDT.common.dto.ApiResponse;
import com.doan.WEB_TMDT.module.accounting.service.AccountingService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Dashboard Controller - Merged from old DashboardController
 * Provides dashboard statistics and recent orders for all user roles
 * Endpoints:
 * - GET /api/dashboard/stats - Get dashboard statistics
 * - GET /api/dashboard/recent-orders - Get recent orders
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AccountingDashboardController {

    private final AccountingService accountingService;

    @GetMapping("/stats")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'EMPLOYEE', 'ACCOUNTANT', 'SALE', 'SALES', 'WAREHOUSE', 'PRODUCT_MANAGER', 'CSKH', 'SHIPPER')")
    public ApiResponse getDashboardStats() {
        return accountingService.getDashboardStats();
    }

    @GetMapping("/recent-orders")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'EMPLOYEE', 'ACCOUNTANT', 'SALE', 'SALES', 'WAREHOUSE', 'PRODUCT_MANAGER', 'CSKH', 'SHIPPER')")
    public ApiResponse getRecentOrders(@RequestParam(defaultValue = "10") int limit) {
        return accountingService.getRecentOrders(limit);
    }
}
