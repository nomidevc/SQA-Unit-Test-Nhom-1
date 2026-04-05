package com.doan.WEB_TMDT.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsDTO {
    private Long totalOrders;
    private Double totalRevenue;
    private Double totalProfit;
    private Double profitMargin;
    private Long totalProducts;
    private Long totalCustomers;
    private Long pendingOrders;
    private Long lowStockProducts;
    private Long overdueOrders;
    private Long overduePayables;
    
    // Percentage changes
    private Double ordersChangePercent;
    private Double revenueChangePercent;
    private Double profitChangePercent;
    private Double productsChangePercent;
    private Double customersChangePercent;
}
