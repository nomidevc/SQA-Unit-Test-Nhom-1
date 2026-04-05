package com.doan.WEB_TMDT.module.product.entity;

import com.doan.WEB_TMDT.module.inventory.entity.ProductDetail;
import com.doan.WEB_TMDT.module.inventory.entity.WarehouseProduct;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(nullable = false)
    private String name;

    private Double price;

    @Column(unique = true)
    private String sku;

    @Column(columnDefinition = "TEXT")
    private String description;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.List<ProductImage> images = new java.util.ArrayList<>();

    private Long stockQuantity;      // Tồn kho thực tế
    
    private Long reservedQuantity;   // Số lượng đang giữ cho đơn hàng
    
    @Column(name = "tech_specs_json", columnDefinition = "TEXT")
    private String techSpecsJson;

    @OneToOne
    @JoinColumn(name = "product_detail_id")
    private ProductDetail productDetail;

    @OneToOne
    @JoinColumn(name = "warehouse_product_id")
    private WarehouseProduct warehouseProduct;

    @Column(name = "active")
    @Builder.Default
    private Boolean active = true;  // Trạng thái đang bán hay ngừng bán (mặc định là hiện)
}
