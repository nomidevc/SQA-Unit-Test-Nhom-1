package com.doan.WEB_TMDT.module.order.controller;

import com.doan.WEB_TMDT.common.dto.ApiResponse;
import com.doan.WEB_TMDT.module.order.service.ShipperAssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/shipper-assignments")
@RequiredArgsConstructor
public class ShipperAssignmentController {
    
    private final ShipperAssignmentService shipperAssignmentService;
    
    // Lấy danh sách đơn hàng có thể nhận
    @GetMapping("/available")
    @PreAuthorize("hasAuthority('SHIPPER')")
    public ApiResponse getAvailableOrders() {
        return shipperAssignmentService.getAvailableOrdersForShipper();
    }
    
    // Shipper tự nhận đơn
    @PostMapping("/claim")
    @PreAuthorize("hasAuthority('SHIPPER')")
    public ApiResponse claimOrder(
            @RequestParam Long orderId,
            @RequestParam Long shipperId) {
        return shipperAssignmentService.claimOrder(orderId, shipperId);
    }
    
    // Lấy danh sách đơn đã nhận của shipper
    @GetMapping("/my-orders")
    @PreAuthorize("hasAuthority('SHIPPER')")
    public ApiResponse getMyOrders(@RequestParam Long shipperId) {
        return shipperAssignmentService.getMyOrders(shipperId);
    }
    
    // Lấy danh sách đơn đang giao của shipper
    @GetMapping("/my-active-orders")
    @PreAuthorize("hasAuthority('SHIPPER')")
    public ApiResponse getMyActiveOrders(@RequestParam Long shipperId) {
        return shipperAssignmentService.getMyActiveOrders(shipperId);
    }
    
    // Shipper xác nhận đã lấy hàng và bắt đầu giao
    @PutMapping("/{assignmentId}/start-delivery")
    @PreAuthorize("hasAuthority('SHIPPER')")
    public ApiResponse startDelivery(
            @PathVariable Long assignmentId,
            @RequestParam Long shipperId) {
        return shipperAssignmentService.startDelivery(assignmentId, shipperId);
    }
    
    // Shipper xác nhận giao thành công
    @PutMapping("/{assignmentId}/deliver")
    @PreAuthorize("hasAuthority('SHIPPER')")
    public ApiResponse confirmDelivery(
            @PathVariable Long assignmentId,
            @RequestParam Long shipperId) {
        return shipperAssignmentService.confirmDelivery(assignmentId, shipperId);
    }
    
    // Shipper báo giao thất bại
    @PutMapping("/{assignmentId}/fail")
    @PreAuthorize("hasAuthority('SHIPPER')")
    public ApiResponse reportFailure(
            @PathVariable Long assignmentId,
            @RequestParam Long shipperId,
            @RequestParam String reason) {
        return shipperAssignmentService.reportFailure(assignmentId, shipperId, reason);
    }
    
    // Shipper hủy nhận đơn (chỉ khi chưa lấy hàng)
    @PutMapping("/{assignmentId}/cancel")
    @PreAuthorize("hasAuthority('SHIPPER')")
    public ApiResponse cancelClaim(
            @PathVariable Long assignmentId,
            @RequestParam Long shipperId) {
        return shipperAssignmentService.cancelClaim(assignmentId, shipperId);
    }
    
    // Lấy chi tiết assignment
    @GetMapping("/{assignmentId}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE', 'SHIPPER')")
    public ApiResponse getAssignmentDetail(@PathVariable Long assignmentId) {
        return shipperAssignmentService.getAssignmentDetail(assignmentId);
    }
    
    // Lấy tất cả assignments (cho admin/warehouse xem)
    @GetMapping("/all")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE', 'SHIPPER', 'SALE', 'CSKH')")
    public ApiResponse getAllAssignments() {
        return shipperAssignmentService.getAllAssignments();
    }
    
    // Lấy assignment theo orderId
    @GetMapping("/by-order/{orderId}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WAREHOUSE', 'SHIPPER', 'SALE', 'CSKH')")
    public ApiResponse getAssignmentByOrder(@PathVariable Long orderId) {
        return shipperAssignmentService.getAssignmentByOrder(orderId);
    }
}
