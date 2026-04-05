package com.doan.WEB_TMDT.module.accounting.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShippingReconciliationResponse {
    private String period;
    private Integer totalOrders;
    private Double totalShippingFeeCollected;
    private Double totalShippingCostPaid;
    private Double shippingProfit;
    private Double profitMargin;
    private List<ShippingDetail> details;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShippingDetail {
        private Long orderId;
        private String orderDate;
        private String shippingAddress;
        private Double shippingFeeCollected;
        private Double actualShippingCost;
        private Double profit;
    }
}
