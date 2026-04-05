package com.doan.WEB_TMDT.module.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateWarehouseProductRequest {
    @NotBlank(message = "SKU không được để trống")
    private String sku;
    
    @NotBlank(message = "Tên sản phẩm không được để trống")
    private String internalName;
    
    private Long supplierId;
    private String description;
    private String techSpecsJson;
}
