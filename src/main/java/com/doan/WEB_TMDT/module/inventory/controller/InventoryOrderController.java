package com.doan.WEB_TMDT.module.inventory.controller;

import com.doan.WEB_TMDT.common.dto.ApiResponse;
import com.doan.WEB_TMDT.module.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory/orders")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class InventoryOrderController {

    private final OrderService orderService;

    /**
     * Lấy danh sách đơn hàng cần xuất kho (status = CONFIRMED và chưa có trong export_orders)
     * Quản lý kho xem để chuẩn bị hàng
     */
    @GetMapping("/pending-export")
    public ApiResponse getOrdersPendingExport() {
        // Lấy các đơn hàng đã CONFIRMED, chưa xuất kho (chưa có trong export_orders)
        return orderService.getOrdersPendingExport();
    }

    /**
     * Lấy chi tiết đơn hàng để chuẩn bị xuất kho
     */
    @GetMapping("/{orderId}")
    public ApiResponse getOrderDetail(@PathVariable Long orderId) {
        return orderService.getOrderById(orderId);
    }

    /**
     * Lấy danh sách đơn hàng đã xuất kho (status = READY_TO_SHIP)
     * Quản lý kho xem các đơn đã xuất, đang chờ tài xế lấy
     */
    @GetMapping("/exported")
    public ApiResponse getOrdersExported(
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        // Lấy các đơn hàng đã xuất kho (READY_TO_SHIP)
        return orderService.getAllOrders("READY_TO_SHIP", page, size);
    }

    /**
     * Lấy thống kê đơn hàng cần xuất
     */
    @GetMapping("/statistics")
    public ApiResponse getExportStatistics() {
        return orderService.getOrderStatistics();
    }
}
