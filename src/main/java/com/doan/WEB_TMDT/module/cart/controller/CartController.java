package com.doan.WEB_TMDT.module.cart.controller;

import com.doan.WEB_TMDT.common.dto.ApiResponse;
import com.doan.WEB_TMDT.module.cart.dto.AddToCartRequest;
import com.doan.WEB_TMDT.module.cart.dto.UpdateCartItemRequest;
import com.doan.WEB_TMDT.module.cart.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('CUSTOMER', 'ADMIN')")
public class CartController {

    private final CartService cartService;

    /**
     * Lấy giỏ hàng của customer
     */
    @GetMapping
    public ApiResponse getCart(Authentication authentication) {
        Long customerId = getCustomerIdFromAuth(authentication);
        return cartService.getCart(customerId);
    }

    /**
     * Thêm sản phẩm vào giỏ hàng
     */
    @PostMapping("/items")
    public ApiResponse addToCart(
            @Valid @RequestBody AddToCartRequest request,
            Authentication authentication) {
        Long customerId = getCustomerIdFromAuth(authentication);
        ApiResponse response = cartService.addToCart(customerId, request);
        return response;
    }

    /**
     * Cập nhật số lượng sản phẩm trong giỏ
     */
    @PutMapping("/items/{itemId}")
    public ApiResponse updateCartItem(
            @PathVariable Long itemId,
            @Valid @RequestBody UpdateCartItemRequest request,
            Authentication authentication) {
        Long customerId = getCustomerIdFromAuth(authentication);
        return cartService.updateCartItem(customerId, itemId, request);
    }

    /**
     * Xóa sản phẩm khỏi giỏ hàng
     */
    @DeleteMapping("/items/{itemId}")
    public ApiResponse removeCartItem(
            @PathVariable Long itemId,
            Authentication authentication) {
        Long customerId = getCustomerIdFromAuth(authentication);
        return cartService.removeCartItem(customerId, itemId);
    }

    /**
     * Xóa tất cả sản phẩm trong giỏ
     */
    @DeleteMapping
    public ApiResponse clearCart(Authentication authentication) {
        Long customerId = getCustomerIdFromAuth(authentication);
        return cartService.clearCart(customerId);
    }

    // Helper method
    private Long getCustomerIdFromAuth(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new RuntimeException("Không tìm thấy thông tin xác thực");
        }
        String email = authentication.getName();
        Long customerId = cartService.getCustomerIdByEmail(email);
        System.out.println("🔍 CartController - Email from token: " + email + ", CustomerId: " + customerId);
        return customerId;
    }
}
