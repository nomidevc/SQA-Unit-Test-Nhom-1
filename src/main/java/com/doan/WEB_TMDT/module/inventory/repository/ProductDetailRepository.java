package com.doan.WEB_TMDT.module.inventory.repository;

import com.doan.WEB_TMDT.module.inventory.entity.ProductDetail;
import com.doan.WEB_TMDT.module.inventory.entity.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductDetailRepository extends JpaRepository<ProductDetail, Long> {
    boolean existsBySerialNumber(String serialNumber);
    Optional<ProductDetail> findBySerialNumber(String serialNumber);
    List<ProductDetail> findAllByWarehouseProduct_Id(Long warehouseProductId);
    List<ProductDetail> findByStatus(ProductStatus status);
    List<ProductDetail> findByWarehouseProduct_SkuAndStatus(String sku, ProductStatus status);
}
