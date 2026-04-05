package com.doan.WEB_TMDT.module.order.entity;

public enum OrderStatus {
    PENDING_PAYMENT,    // Chờ thanh toán (đơn online)
    CONFIRMED,          // Đã xác nhận - Chờ xuất kho
    READY_TO_SHIP,      // Đã xuất kho - Chờ tài xế lấy hàng
    PICKED_UP,          // Tài xế đã lấy hàng
    SHIPPING,           // Đang giao hàng
    DELIVERY_FAILED,    // Giao hàng thất bại
    DELIVERED,          // Đã giao hàng
    COMPLETED,          // Hoàn thành
    CANCELLED,          // Đã hủy
    RETURNED,           // Đã trả hàng
    PROCESSING          // Đang xử lý (deprecated, không dùng)
}
