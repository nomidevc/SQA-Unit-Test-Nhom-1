package com.doan.WEB_TMDT.module.order.service;

import com.doan.WEB_TMDT.common.dto.ApiResponse;

public interface ShipperAssignmentService {
    
    // Lấy danh sách đơn hàng có thể nhận (nội thành Hà Nội, chưa có shipper, chưa dùng GHN)
    ApiResponse getAvailableOrdersForShipper();
    
    // Shipper tự nhận đơn
    ApiResponse claimOrder(Long orderId, Long shipperId);
    
    // Lấy danh sách đơn đã nhận của shipper
    ApiResponse getMyOrders(Long shipperId);
    
    // Lấy danh sách đơn đang giao của shipper
    ApiResponse getMyActiveOrders(Long shipperId);
    
    // Shipper xác nhận đã lấy hàng và bắt đầu giao
    ApiResponse startDelivery(Long assignmentId, Long shipperId);
    
    // Shipper xác nhận giao thành công
    ApiResponse confirmDelivery(Long assignmentId, Long shipperId);
    
    // Shipper báo giao thất bại
    ApiResponse reportFailure(Long assignmentId, Long shipperId, String reason);
    
    // Shipper hủy nhận đơn (chỉ khi chưa lấy hàng)
    ApiResponse cancelClaim(Long assignmentId, Long shipperId);
    
    // Lấy chi tiết assignment
    ApiResponse getAssignmentDetail(Long assignmentId);
    
    // Hủy assignment (khi đơn hàng bị hủy)
    ApiResponse cancelAssignment(Long orderId);
    
    // Lấy tất cả assignments
    ApiResponse getAllAssignments();
    
    // Lấy assignment theo orderId
    ApiResponse getAssignmentByOrder(Long orderId);
}
