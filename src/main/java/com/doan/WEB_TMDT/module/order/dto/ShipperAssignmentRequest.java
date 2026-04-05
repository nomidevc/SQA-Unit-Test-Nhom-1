package com.doan.WEB_TMDT.module.order.dto;

import lombok.Data;

@Data
public class ShipperAssignmentRequest {
    private Long orderId;
    private Long shipperId; // ID của nhân viên có position = SHIPPER
}
