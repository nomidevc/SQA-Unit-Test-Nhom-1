package com.doan.WEB_TMDT.module.order.controller;

import com.doan.WEB_TMDT.common.dto.ApiResponse;
import com.doan.WEB_TMDT.module.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
// Temporarily disabled for debugging
// @PreAuthorize("hasAnyAuthority('ADMIN', 'SALE', 'SALES', 'SALES_STAFF', 'EMPLOYEE')")
public class OrderManagementController {

    private final OrderService orderService;

   
    @GetMapping
    public ApiResponse getAllOrders(
            @RequestParam(required = false, defaultValue = "ALL") String status,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size,
            @RequestParam(required = false) String search) {
        return orderService.getAllOrders(status, page, size);
    }

    
    @GetMapping("/{orderId}")
    public ApiResponse getOrderById(@PathVariable Long orderId) {
        return orderService.getOrderById(orderId);
    }

    
    @GetMapping("/statistics")
    public ApiResponse getOrderStatistics() {
        return orderService.getOrderStatistics();
    }

    @GetMapping("/stats")
    public ApiResponse getOrderStats() {
        return orderService.getOrderStatistics();
    }

    
    // DEPRECATED: Không cho phép cập nhật từ CONFIRMED nữa
    // Chỉ cho phép cập nhật từ READY_TO_SHIP → SHIPPING
    // @PutMapping("/{orderId}/shipping")
    // public ApiResponse markAsShipping(@PathVariable Long orderId) {
    //     return orderService.markAsShipping(orderId);
    // }

    // Cho phép nhân viên bán hàng cập nhật từ READY_TO_SHIP sang SHIPPING
    // CHỈ endpoint này được sử dụng để cập nhật sang SHIPPING
    @PutMapping("/{orderId}/mark-shipping-from-ready")
    public ApiResponse markShippingFromReady(@PathVariable Long orderId) {
        return orderService.markShippingFromReady(orderId);
    }

    @PutMapping("/{orderId}/delivered")
    public ApiResponse markAsDelivered(@PathVariable Long orderId) {
        return orderService.markAsDelivered(orderId);
    }

   
    @PutMapping("/{orderId}/cancel")
    public ApiResponse cancelOrder(
            @PathVariable Long orderId,
            @RequestParam(required = false) String reason) {
        return orderService.cancelOrder(orderId, reason);
    }

    
    @PutMapping("/{orderId}/status")
    public ApiResponse updateOrderStatus(
            @PathVariable Long orderId,
            @RequestParam String status) {
        return orderService.updateOrderStatus(orderId, status);
    }

    
    @GetMapping("/{orderId}/shipping-status")
    public ApiResponse getShippingStatusAdmin(@PathVariable Long orderId) {
        return orderService.getShippingStatusAdmin(orderId);
    }
}
