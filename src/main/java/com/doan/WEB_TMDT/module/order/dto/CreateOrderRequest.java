package com.doan.WEB_TMDT.module.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {
    
    // Thông tin giao hàng
    @NotBlank(message = "Tỉnh/Thành phố không được để trống")
    private String province;
    
    @NotBlank(message = "Quận/Huyện không được để trống")
    private String district;
    
    // Phường/Xã (ward code for GHN)
    private String ward;
    
    // Tên phường/xã (for display)
    private String wardName;
    
    @NotBlank(message = "Địa chỉ cụ thể không được để trống")
    private String address;
    
    private String note; // Ghi chú
    
    // Phí vận chuyển (đã tính từ frontend)
    @NotNull(message = "Phí vận chuyển không được để trống")
    private Double shippingFee;
    
    // Phương thức thanh toán
    private String paymentMethod; // COD hoặc SEPAY
    
    // Danh sách cart item IDs đã chọn (nếu null hoặc rỗng thì lấy tất cả)
    private List<Long> selectedItemIds;
}
