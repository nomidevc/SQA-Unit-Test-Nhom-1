package com.doan.WEB_TMDT.module.product.controller;

import com.doan.WEB_TMDT.common.dto.ApiResponse;
import com.doan.WEB_TMDT.module.inventory.service.ProductSpecificationService;
import com.doan.WEB_TMDT.module.product.dto.ProductWithSpecsDTO;
import com.doan.WEB_TMDT.module.product.dto.PublishProductRequest;
import com.doan.WEB_TMDT.module.product.entity.Product;
import com.doan.WEB_TMDT.module.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ProductSpecificationService productSpecificationService;

    @GetMapping
    public ApiResponse getAll(@RequestParam(defaultValue = "false") boolean includeInactive) {
        List<Product> products = productService.getAll();
        boolean canViewInactive = includeInactive && hasBackOfficeProductAccess();

        List<ProductWithSpecsDTO> productsWithSpecs = products.stream()
                .filter(product -> canViewInactive || product.getActive() == null || product.getActive())
                .map(productService::toProductWithSpecs)
                .collect(java.util.stream.Collectors.toList());
        return ApiResponse.success("Danh sách sản phẩm", productsWithSpecs);
    }

    private boolean hasBackOfficeProductAccess() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }

        return authentication.getAuthorities().stream()
                .map(grantedAuthority -> grantedAuthority.getAuthority())
                .anyMatch(authority -> "ADMIN".equals(authority) || "PRODUCT_MANAGER".equals(authority));
    }

    @GetMapping("/{id}")
    public ApiResponse getById(@PathVariable Long id) {
        return productService.getById(id)
                .map(product -> {
                    ProductWithSpecsDTO dto = productService.toProductWithSpecs(product);
                    return ApiResponse.success("Thông tin sản phẩm", dto);
                })
                .orElse(ApiResponse.error("Không tìm thấy sản phẩm"));
    }

    @GetMapping("/{id}/with-specs")
    public ApiResponse getByIdWithSpecs(@PathVariable Long id) {
        // Giờ endpoint này giống với /{id}, có thể deprecated
        return getById(id);
    }

    // ===== Quản lý đăng bán sản phẩm từ kho (PRODUCT_MANAGER & ADMIN) =====
    
    @GetMapping("/warehouse/list")
    @PreAuthorize("hasAnyAuthority('PRODUCT_MANAGER', 'ADMIN')")
    public ApiResponse getWarehouseProductsForPublish() {
        return productService.getWarehouseProductsForPublish();
    }
    
    @PostMapping("/warehouse/publish")
    @PreAuthorize("hasAnyAuthority('PRODUCT_MANAGER', 'ADMIN')")
    public ApiResponse createProductFromWarehouse(
            @RequestBody com.doan.WEB_TMDT.module.product.dto.CreateProductFromWarehouseRequest request) {
        return productService.createProductFromWarehouse(request);
    }
    
    @PutMapping("/warehouse/publish/{productId}")
    @PreAuthorize("hasAnyAuthority('PRODUCT_MANAGER', 'ADMIN')")
    public ApiResponse updatePublishedProduct(
            @PathVariable Long productId,
            @RequestBody com.doan.WEB_TMDT.module.product.dto.CreateProductFromWarehouseRequest request) {
        return productService.updatePublishedProduct(productId, request);
    }
    
    @DeleteMapping("/warehouse/unpublish/{productId}")
    @PreAuthorize("hasAnyAuthority('PRODUCT_MANAGER', 'ADMIN')")
    public ApiResponse unpublishProduct(@PathVariable Long productId) {
        return productService.unpublishProduct(productId);
    }
    
    @PostMapping("/publish")
    @PreAuthorize("hasAnyAuthority('PRODUCT_MANAGER', 'ADMIN')")
    public ApiResponse publishProduct(@RequestBody PublishProductRequest request) {
        try {
            Product product = productService.publishProduct(request);
            return ApiResponse.success("Đăng bán sản phẩm thành công!", product);
        } catch (RuntimeException e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    // ===== CRUD sản phẩm (PRODUCT_MANAGER & ADMIN) =====
    
    @PostMapping
    @PreAuthorize("hasAnyAuthority('PRODUCT_MANAGER', 'ADMIN')")
    public ApiResponse create(@RequestBody Product product) {
        return ApiResponse.success("Tạo sản phẩm thành công", productService.create(product));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PRODUCT_MANAGER', 'ADMIN')")
    public ApiResponse update(@PathVariable Long id, @RequestBody Product product) {
        Product updated = productService.update(id, product);
        return updated != null ? 
                ApiResponse.success("Cập nhật sản phẩm thành công", updated) : 
                ApiResponse.error("Không tìm thấy sản phẩm");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse delete(@PathVariable Long id) {
        productService.delete(id);
        return ApiResponse.success("Xóa sản phẩm thành công");
    }

    // ===== Search by Specifications (Cho khách hàng) =====
    
    @GetMapping("/search-by-specs")
    public ApiResponse searchBySpecs(@RequestParam String keyword) {
        var warehouseProducts = productSpecificationService.searchBySpecValue(keyword);
        
        List<Product> products = warehouseProducts.stream()
                .map(wp -> wp.getProduct())
                .filter(p -> p != null)
                .toList();
        
        return ApiResponse.success("Tìm thấy " + products.size() + " sản phẩm", products);
    }

    @GetMapping("/filter-by-specs")
    public ApiResponse filterBySpecs(
            @RequestParam String key,
            @RequestParam String value
    ) {
        var warehouseProducts = productSpecificationService.searchBySpecKeyAndValue(key, value);
        
        List<Product> products = warehouseProducts.stream()
                .map(wp -> wp.getProduct())
                .filter(p -> p != null)
                .toList();
        
        return ApiResponse.success("Tìm thấy " + products.size() + " sản phẩm", products);
    }

    // ===== Product Images Management =====
    
    @GetMapping("/{productId}/images")
    public ApiResponse getProductImages(@PathVariable Long productId) {
        return productService.getProductImages(productId);
    }

    @PostMapping("/{productId}/images")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'PRODUCT_MANAGER')")
    public ApiResponse addProductImage(
            @PathVariable Long productId,
            @RequestBody java.util.Map<String, Object> request
    ) {
        String imageUrl = (String) request.get("imageUrl");
        Boolean isPrimary = (Boolean) request.getOrDefault("isPrimary", false);
        return productService.addProductImage(productId, imageUrl, isPrimary);
    }

    @PutMapping("/{productId}/images/{imageId}/primary")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'PRODUCT_MANAGER')")
    public ApiResponse setPrimaryImage(
            @PathVariable Long productId,
            @PathVariable Long imageId
    ) {
        return productService.setPrimaryImage(productId, imageId);
    }

    @DeleteMapping("/images/{imageId}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'PRODUCT_MANAGER')")
    public ApiResponse deleteProductImage(@PathVariable Long imageId) {
        return productService.deleteProductImage(imageId);
    }

    @PutMapping("/{productId}/images/reorder")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'PRODUCT_MANAGER')")
    public ApiResponse reorderProductImages(
            @PathVariable Long productId,
            @RequestBody java.util.Map<String, java.util.List<Long>> request
    ) {
        java.util.List<Long> imageIds = request.get("imageIds");
        return productService.reorderProductImages(productId, imageIds);
    }

    @PutMapping("/{id}/toggle-active")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'PRODUCT_MANAGER')")
    public ApiResponse toggleActive(@PathVariable Long id) {
        try {
            Product product = productService.getById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm"));
            
            Boolean currentActive = product.getActive();
            if (currentActive == null) currentActive = true;
            
            product.setActive(!currentActive);
            productService.update(id, product);
            
            String status = product.getActive() ? "đang bán" : "ngừng bán";
            return ApiResponse.success("Đã chuyển sản phẩm sang trạng thái " + status, null);
        } catch (Exception e) {
            return ApiResponse.error("Lỗi: " + e.getMessage());
        }
    }

}
