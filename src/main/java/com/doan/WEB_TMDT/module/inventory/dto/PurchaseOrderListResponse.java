package com.doan.WEB_TMDT.module.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderListResponse {
    private Long id;
    private String poCode;
    private String supplierName;
    private LocalDateTime orderDate;
    private LocalDateTime receivedDate;
    private String status;
    private Double totalAmount;
    private Integer itemCount;
}
