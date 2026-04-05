package com.doan.WEB_TMDT.module.product.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductWithSpecsDTO {
    private Long id;
    private String name;
    private String sku;
    private Double price;
    private String description;
    private Long stockQuantity;        // Tổng tồn kho (cho admin)
    private Long reservedQuantity;     // Số lượng đang giữ (cho admin)
    private Integer availableQuantity; // Số lượng khả dụng = stock - reserved (cho customer)
    private Long categoryId;
    private String categoryName;
    
    // Danh sách ảnh sản phẩm
    private java.util.List<ProductImageDTO> images;
    
    // Thông số kỹ thuật dạng Map để dễ hiển thị
    private Map<String, String> specifications;
}
