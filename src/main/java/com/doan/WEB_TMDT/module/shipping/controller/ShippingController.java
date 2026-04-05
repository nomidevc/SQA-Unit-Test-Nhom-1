package com.doan.WEB_TMDT.module.shipping.controller;

import com.doan.WEB_TMDT.common.dto.ApiResponse;
import com.doan.WEB_TMDT.module.shipping.dto.CalculateShippingFeeRequest;
import com.doan.WEB_TMDT.module.shipping.dto.ShippingFeeResponse;
import com.doan.WEB_TMDT.module.shipping.service.ShippingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/shipping")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class ShippingController {

    private final ShippingService shippingService;

    @PostMapping("/calculate-fee")
    public ResponseEntity<?> calculateShippingFee(
            @RequestBody CalculateShippingFeeRequest request) {
        try {
            log.info("Calculating shipping fee for: {}", request);
            ShippingFeeResponse response = shippingService.calculateShippingFee(request);
            return ResponseEntity.ok(ApiResponse.success("Tính phí thành công", response));
        } catch (Exception e) {
            log.error("Error calculating shipping fee", e);
            return ResponseEntity.ok(ApiResponse.error("Không thể tính phí vận chuyển: " + e.getMessage()));
        }
    }
    
    @GetMapping("/provinces")
    public ResponseEntity<?> getProvinces() {
        try {
            log.info("Getting provinces from GHN");
            var provinces = shippingService.getProvinces();
            return ResponseEntity.ok(ApiResponse.success("Lấy danh sách tỉnh/thành phố thành công", provinces));
        } catch (Exception e) {
            log.error("Error getting provinces", e);
            return ResponseEntity.ok(ApiResponse.error("Không thể lấy danh sách tỉnh/thành phố: " + e.getMessage()));
        }
    }
    
    @GetMapping("/districts/{provinceId}")
    public ResponseEntity<?> getDistricts(@PathVariable Integer provinceId) {
        try {
            log.info("Getting districts for province ID: {}", provinceId);
            var districts = shippingService.getDistricts(provinceId);
            return ResponseEntity.ok(ApiResponse.success("Lấy danh sách quận/huyện thành công", districts));
        } catch (Exception e) {
            log.error("Error getting districts", e);
            return ResponseEntity.ok(ApiResponse.error("Không thể lấy danh sách quận/huyện: " + e.getMessage()));
        }
    }
    
    @GetMapping("/wards/{districtId}")
    public ResponseEntity<?> getWards(@PathVariable Integer districtId) {
        try {
            log.info("Getting wards for district ID: {}", districtId);
            var wards = shippingService.getWards(districtId);
            return ResponseEntity.ok(ApiResponse.success("Lấy danh sách phường/xã thành công", wards));
        } catch (Exception e) {
            log.error("Error getting wards", e);
            return ResponseEntity.ok(ApiResponse.error("Không thể lấy danh sách phường/xã: " + e.getMessage()));
        }
    }
    
    @GetMapping("/ward-name/{districtId}/{wardCode}")
    public ResponseEntity<?> getWardName(@PathVariable Integer districtId, @PathVariable String wardCode) {
        try {
            log.info("Getting ward name for district ID: {}, ward code: {}", districtId, wardCode);
            var wards = shippingService.getWards(districtId);
            
            // Find ward by code
            var ward = wards.stream()
                .filter(w -> wardCode.equals(w.get("code")))
                .findFirst();
            
            if (ward.isPresent()) {
                return ResponseEntity.ok(ApiResponse.success("Lấy tên phường/xã thành công", ward.get()));
            } else {
                return ResponseEntity.ok(ApiResponse.error("Không tìm thấy phường/xã với mã: " + wardCode));
            }
        } catch (Exception e) {
            log.error("Error getting ward name", e);
            return ResponseEntity.ok(ApiResponse.error("Không thể lấy tên phường/xã: " + e.getMessage()));
        }
    }
    
    @PostMapping("/fix-ward-names")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> fixAllWardNames() {
        try {
            log.info("🔧 Starting to fix all ward names...");
            var result = shippingService.fixAllWardNames();
            return ResponseEntity.ok(ApiResponse.success("Đã cập nhật tên phường/xã cho tất cả đơn hàng", result));
        } catch (Exception e) {
            log.error("Error fixing ward names", e);
            return ResponseEntity.ok(ApiResponse.error("Lỗi khi cập nhật tên phường/xã: " + e.getMessage()));
        }
    }
}
