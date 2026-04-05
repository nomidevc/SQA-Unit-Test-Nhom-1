package com.doan.WEB_TMDT.module.order.controller;

import com.doan.WEB_TMDT.common.dto.ApiResponse;
import com.doan.WEB_TMDT.module.order.dto.CreateOrderRequest;
import com.doan.WEB_TMDT.module.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * Test endpoint để xem authorities
     */
    @GetMapping("/test-auth")
    public ApiResponse testAuth(Authentication authentication) {
        if (authentication == null) {
            return ApiResponse.error("Not authenticated");
        }
        
        Map<String, Object> authInfo = new HashMap<>();
        authInfo.put("name", authentication.getName());
        authInfo.put("authorities", authentication.getAuthorities().stream()
            .map(a -> a.getAuthority())
            .collect(java.util.stream.Collectors.toList()));
        authInfo.put("authenticated", authentication.isAuthenticated());
        
        return ApiResponse.success("Auth info", authInfo);
    }

    /**
     * Lấy thống kê đơn hàng (cho nhân viên SALE)
     */
    @GetMapping("/stats")
    // Temporarily disabled for debugging
    // @PreAuthorize("hasAnyAuthority('SALE', 'SALES', 'ADMIN', 'EMPLOYEE')")
    public ApiResponse getOrderStats() {
        return orderService.getOrderStatistics();
    }

    /**
     * Tạo đơn hàng từ giỏ hàng
     */
    @PostMapping
    @PreAuthorize("hasAnyAuthority('CUSTOMER', 'ADMIN')")
    public ApiResponse createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            Authentication authentication) {
        Long customerId = getCustomerIdFromAuth(authentication);
        return orderService.createOrderFromCart(customerId, request);
    }

    /**
     * Lấy danh sách đơn hàng của customer
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('CUSTOMER', 'ADMIN')")
    public ApiResponse getMyOrders(Authentication authentication) {
        Long customerId = getCustomerIdFromAuth(authentication);
        return orderService.getMyOrders(customerId);
    }

    /**
     * Lấy chi tiết đơn hàng theo ID
     */
    @GetMapping("/{orderId}")
    @PreAuthorize("hasAnyAuthority('CUSTOMER', 'ADMIN')")
    public ApiResponse getOrderById(
            @PathVariable Long orderId,
            Authentication authentication) {
        Long customerId = getCustomerIdFromAuth(authentication);
        return orderService.getOrderById(orderId, customerId);
    }

    /**
     * Lấy chi tiết đơn hàng theo mã
     */
    @GetMapping("/code/{orderCode}")
    @PreAuthorize("hasAnyAuthority('CUSTOMER', 'ADMIN')")
    public ApiResponse getOrderByCode(
            @PathVariable String orderCode,
            Authentication authentication) {
        Long customerId = getCustomerIdFromAuth(authentication);
        return orderService.getOrderByCode(orderCode, customerId);
    }

    /**
     * Hủy đơn hàng (Customer)
     */
    @PutMapping("/{orderId}/cancel")
    @PreAuthorize("hasAnyAuthority('CUSTOMER', 'ADMIN')")
    public ApiResponse cancelOrder(
            @PathVariable Long orderId,
            @RequestParam(required = false) String reason,
            Authentication authentication) {
        Long customerId = getCustomerIdFromAuth(authentication);
        return orderService.cancelOrderByCustomer(orderId, customerId, reason);
    }

    /**
     * Xem trạng thái vận chuyển GHN
     */
    @GetMapping("/{orderId}/shipping-status")
    @PreAuthorize("hasAnyAuthority('CUSTOMER', 'ADMIN')")
    public ApiResponse getShippingStatus(
            @PathVariable Long orderId,
            Authentication authentication) {
        Long customerId = getCustomerIdFromAuth(authentication);
        return orderService.getShippingStatus(orderId, customerId);
    }

    /**
     * Khách hàng xác nhận đã nhận hàng
     */
    @PutMapping("/{orderId}/confirm-received")
    @PreAuthorize("hasAnyAuthority('CUSTOMER', 'ADMIN')")
    public ApiResponse confirmReceived(
            @PathVariable Long orderId,
            Authentication authentication) {
        Long customerId = getCustomerIdFromAuth(authentication);
        return orderService.confirmReceived(orderId, customerId);
    }

    /**
     * Lấy danh sách đơn hàng của 1 khách hàng (cho nhân viên/admin)
     */
    @GetMapping("/customer/{customerId}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'EMPLOYEE', 'SALE', 'WAREHOUSE', 'SHIPPER', 'PRODUCT_MANAGER', 'ACCOUNTANT', 'CSKH')")
    public ApiResponse getOrdersByCustomerId(@PathVariable Long customerId) {
        return orderService.getOrdersByCustomerId(customerId);
    }

    // Helper method
    private Long getCustomerIdFromAuth(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new RuntimeException("Không tìm thấy thông tin xác thực");
        }
        String email = authentication.getName();
        return orderService.getCustomerIdByEmail(email);
    }
}
