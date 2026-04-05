package com.doan.WEB_TMDT.module.product.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductDTO {
    private Long id;
    private String name;
    private String description;
    private Double price;
    private Integer stockQuantity;        
    private Integer reservedQuantity;     
    private Integer availableQuantity;   
    private String imageUrl;
    private Long categoryId;
}
