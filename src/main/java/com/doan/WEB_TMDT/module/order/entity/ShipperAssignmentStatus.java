package com.doan.WEB_TMDT.module.order.entity;

public enum ShipperAssignmentStatus {
    CLAIMED,       // Shipper đã nhận đơn
    DELIVERING,    // Đang giao hàng (đã lấy hàng từ kho)
    DELIVERED,     // Đã giao thành công
    FAILED,        // Giao thất bại
    CANCELLED      // Đã hủy (shipper hủy nhận hoặc đơn hàng bị hủy)
}
