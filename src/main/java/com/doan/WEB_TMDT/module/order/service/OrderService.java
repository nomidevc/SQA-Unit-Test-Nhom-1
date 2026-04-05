package com.doan.WEB_TMDT.module.order.service;

import com.doan.WEB_TMDT.common.dto.ApiResponse;
import com.doan.WEB_TMDT.module.order.dto.CreateOrderRequest;

public interface OrderService {
    // Helper method
    Long getCustomerIdByEmail(String email);
    
    // Customer endpoints
    ApiResponse createOrderFromCart(Long customerId, CreateOrderRequest request);
    ApiResponse getOrderById(Long orderId, Long customerId);
    ApiResponse getOrderByCode(String orderCode, Long customerId);
    ApiResponse getMyOrders(Long customerId);
    ApiResponse cancelOrderByCustomer(Long orderId, Long customerId, String reason);
    ApiResponse getShippingStatus(Long orderId, Long customerId);
    ApiResponse confirmReceived(Long orderId, Long customerId); // Khách xác nhận đã nhận hàng
    
    // Admin/Staff endpoints
    ApiResponse getAllOrders(String status, int page, int size);
    ApiResponse getOrderById(Long orderId);
    ApiResponse getOrderStatistics();
    ApiResponse getOrdersPendingExport(); // Đơn CONFIRMED chưa xuất kho
    // ApiResponse confirmOrder(Long orderId);
    ApiResponse updateOrderStatus(Long orderId, String status);
    ApiResponse markAsShipping(Long orderId);
    ApiResponse markShippingFromReady(Long orderId); // Cập nhật từ READY_TO_SHIP sang SHIPPING
    ApiResponse markAsDelivered(Long orderId);
    ApiResponse cancelOrder(Long orderId, String reason);
    ApiResponse getShippingStatusAdmin(Long orderId);
    ApiResponse getOrdersByCustomerId(Long customerId); // Lấy đơn hàng theo customer (cho nhân viên)
}
