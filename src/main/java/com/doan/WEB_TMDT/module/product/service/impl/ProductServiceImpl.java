package com.doan.WEB_TMDT.module.product.service.impl;

import com.doan.WEB_TMDT.module.inventory.entity.ProductSpecification;
import com.doan.WEB_TMDT.module.inventory.repository.WarehouseProductRepository;
import com.doan.WEB_TMDT.module.product.dto.ProductWithSpecsDTO;
import com.doan.WEB_TMDT.module.product.dto.PublishProductRequest;
import com.doan.WEB_TMDT.module.product.entity.Product;
import com.doan.WEB_TMDT.module.product.repository.CategoryRepository;
import com.doan.WEB_TMDT.module.product.repository.ProductRepository;
import com.doan.WEB_TMDT.module.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final WarehouseProductRepository warehouseProductRepository;
    private final CategoryRepository categoryRepository;
    private final com.doan.WEB_TMDT.module.inventory.repository.InventoryStockRepository inventoryStockRepository;
    private final com.doan.WEB_TMDT.module.product.repository.ProductImageRepository imageRepository;

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<Product> getAll() {
        List<Product> products = productRepository.findAll();
        // Eager load category để tránh lazy loading exception
        products.forEach(product -> {
            if (product.getCategory() != null) {
                product.getCategory().getName(); // Trigger lazy load
            }
        });
        return products;
    }

    @Override
    public Optional<Product> getById(Long id) {
        return productRepository.findById(id);
    }

    @Override
    public Product create(Product product) {
        return productRepository.save(product);
    }

    @Override
    public Product update(Long id, Product product) {
        return productRepository.findById(id)
                .map(existingProduct -> {
                    // Chỉ update các field cần thiết, không động vào images
                    if (product.getName() != null) {
                        existingProduct.setName(product.getName());
                    }
                    if (product.getPrice() != null) {
                        existingProduct.setPrice(product.getPrice());
                    }
                    if (product.getDescription() != null) {
                        existingProduct.setDescription(product.getDescription());
                    }
                    if (product.getCategory() != null) {
                        existingProduct.setCategory(product.getCategory());
                    } else if (product.getCategory() == null && existingProduct.getCategory() != null) {
                        // Nếu gửi category = null, giữ nguyên category cũ (không xóa)
                        // Không làm gì
                    }
                    if (product.getSku() != null) {
                        existingProduct.setSku(product.getSku());
                    }
                    if (product.getStockQuantity() != null) {
                        existingProduct.setStockQuantity(product.getStockQuantity());
                    }
                    if (product.getReservedQuantity() != null) {
                        existingProduct.setReservedQuantity(product.getReservedQuantity());
                    }
                    if (product.getTechSpecsJson() != null) {
                        existingProduct.setTechSpecsJson(product.getTechSpecsJson());
                    }
                    // Không update images collection - quản lý riêng qua addProductImage/deleteProductImage
                    return productRepository.save(existingProduct);
                })
                .orElse(null);
    }

    @Override
    public void delete(Long id) {
        productRepository.deleteById(id);
    }

    @Override
    public ProductWithSpecsDTO toProductWithSpecs(Product product) {
        try {
            // Lấy danh sách ảnh
            List<com.doan.WEB_TMDT.module.product.dto.ProductImageDTO> images = 
                imageRepository.findByProductIdOrderByDisplayOrderAsc(product.getId())
                    .stream()
                    .map(this::toImageDTO)
                    .collect(Collectors.toList());
            
            // Tính số lượng khả dụng từ InventoryStock (đã trừ reserved và damaged)
            Long availableQty = 0L;
            try {
                if (product.getWarehouseProduct() != null) {
                    Long wpId = product.getWarehouseProduct().getId();
                    if (wpId != null) {
                        Optional<com.doan.WEB_TMDT.module.inventory.entity.InventoryStock> stockOpt = 
                                inventoryStockRepository.findByWarehouseProduct_Id(wpId);
                        if (stockOpt.isPresent()) {
                            availableQty = stockOpt.get().getSellable(); // onHand - reserved - damaged
                        }
                    }
                }
            } catch (Exception e) {
                // Fallback nếu có lỗi lazy loading
                System.err.println("Error loading warehouse product for product " + product.getId() + ": " + e.getMessage());
            }
            
            if (availableQty == 0L) {
                // Fallback nếu không có WarehouseProduct hoặc có lỗi
                Long stockQty = product.getStockQuantity() != null ? product.getStockQuantity() : 0L;
                Long reservedQty = product.getReservedQuantity() != null ? product.getReservedQuantity() : 0L;
                availableQty = Math.max(0, stockQty - reservedQty);
            }
        
            var dto = ProductWithSpecsDTO.builder()
                    .id(product.getId())
                    .name(product.getName())
                    .sku(product.getSku())
                    .price(product.getPrice())
                    .description(product.getDescription())
                    .stockQuantity(product.getStockQuantity())
                    .reservedQuantity(product.getReservedQuantity())
                    .availableQuantity(availableQty.intValue()) // Số lượng thực sự có thể bán
                    .categoryId(product.getCategory() != null ? product.getCategory().getId() : null)
                    .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                    .images(images)
                    .build();

            // Lấy specifications từ techSpecsJson của Product
            if (product.getTechSpecsJson() != null && !product.getTechSpecsJson().isEmpty()) {
                try {
                    // Parse JSON string thành Map
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    @SuppressWarnings("unchecked")
                    Map<String, String> specs = mapper.readValue(
                        product.getTechSpecsJson(), 
                        Map.class
                    );
                    dto.setSpecifications(specs);
                } catch (Exception e) {
                    System.err.println("Error parsing techSpecsJson: " + e.getMessage());
                }
            }

            return dto;
        } catch (Exception e) {
            System.err.println("Error in toProductWithSpecs for product " + product.getId() + ": " + e.getMessage());
            // Return basic DTO without specs
            return ProductWithSpecsDTO.builder()
                    .id(product.getId())
                    .name(product.getName())
                    .price(product.getPrice())
                    .build();
        }
    }

    @Override
    public Product publishProduct(PublishProductRequest request) {
        // 1. Lấy WarehouseProduct
        var warehouseProduct = warehouseProductRepository.findById(request.getWarehouseProductId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm trong kho với ID: " + request.getWarehouseProductId()));

        // 2. Kiểm tra đã đăng bán chưa
        if (warehouseProduct.getProduct() != null) {
            throw new RuntimeException("Sản phẩm này đã được đăng bán rồi!");
        }

        // 3. Lấy Category
        var category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy danh mục với ID: " + request.getCategoryId()));

        // 4. Tạo Product mới
        Product product = Product.builder()
                .name(request.getName())
                .sku(warehouseProduct.getSku())
                .price(request.getPrice())
                .description(request.getDescription())
                .category(category)
                .warehouseProduct(warehouseProduct)
                .stockQuantity(warehouseProduct.getQuantityInStock())
                .build();

        // 5. Lưu Product
        return productRepository.save(product);
    }

    @Override
    public com.doan.WEB_TMDT.common.dto.ApiResponse getWarehouseProductsForPublish() {
        List<com.doan.WEB_TMDT.module.inventory.entity.WarehouseProduct> warehouseProducts = 
                warehouseProductRepository.findAll();
        
        List<com.doan.WEB_TMDT.module.product.dto.WarehouseProductListResponse> response = 
                warehouseProducts.stream().map(wp -> {
            // Kiểm tra xem đã có Product nào liên kết chưa
            Optional<Product> existingProduct = productRepository.findAll().stream()
                    .filter(p -> p.getWarehouseProduct() != null && 
                                 p.getWarehouseProduct().getId().equals(wp.getId()))
                    .findFirst();
            
            // Lấy số lượng tồn kho từ InventoryStock
            Long stockQuantity = 0L;
            Long sellableQuantity = 0L;
            Optional<com.doan.WEB_TMDT.module.inventory.entity.InventoryStock> stockOpt = 
                    inventoryStockRepository.findByWarehouseProduct_Id(wp.getId());
            if (stockOpt.isPresent()) {
                stockQuantity = stockOpt.get().getOnHand();
                sellableQuantity = stockOpt.get().getSellable();
            }
            
            return com.doan.WEB_TMDT.module.product.dto.WarehouseProductListResponse.builder()
                    .id(wp.getId())
                    .sku(wp.getSku())
                    .internalName(wp.getInternalName())
                    .description(wp.getDescription())
                    .techSpecsJson(wp.getTechSpecsJson())
                    .lastImportDate(wp.getLastImportDate())
                    .stockQuantity(stockQuantity)
                    .sellableQuantity(sellableQuantity)
                    .supplierName(wp.getSupplier() != null ? wp.getSupplier().getName() : null)
                    .isPublished(existingProduct.isPresent())
                    .publishedProductId(existingProduct.map(Product::getId).orElse(null))
                    .active(existingProduct.map(Product::getActive).orElse(null))
                    .build();
        }).collect(Collectors.toList());
        
        return com.doan.WEB_TMDT.common.dto.ApiResponse.success(
                "Danh sách sản phẩm trong kho", response);
    }

    @Override
    public com.doan.WEB_TMDT.common.dto.ApiResponse createProductFromWarehouse(
            com.doan.WEB_TMDT.module.product.dto.CreateProductFromWarehouseRequest request) {
        
        // 1. Lấy WarehouseProduct
        var warehouseProduct = warehouseProductRepository.findById(request.getWarehouseProductId())
                .orElseThrow(() -> new RuntimeException(
                        "Không tìm thấy sản phẩm trong kho với ID: " + request.getWarehouseProductId()));

        // 2. Kiểm tra đã đăng bán chưa
        Optional<Product> existingProduct = productRepository.findAll().stream()
                .filter(p -> p.getWarehouseProduct() != null && 
                             p.getWarehouseProduct().getId().equals(warehouseProduct.getId()))
                .findFirst();
        
        if (existingProduct.isPresent()) {
            return com.doan.WEB_TMDT.common.dto.ApiResponse.error(
                    "Sản phẩm này đã được đăng bán rồi!");
        }

        // 3. Lấy Category
        var category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new RuntimeException(
                        "Không tìm thấy danh mục với ID: " + request.getCategoryId()));

        // 4. Lấy số lượng tồn kho từ InventoryStock
        Long stockQuantity = 0L;
        Optional<com.doan.WEB_TMDT.module.inventory.entity.InventoryStock> stockOpt = 
                inventoryStockRepository.findByWarehouseProduct_Id(warehouseProduct.getId());
        if (stockOpt.isPresent()) {
            stockQuantity = stockOpt.get().getSellable();
        }

        // 5. Tạo Product mới
        Product product = Product.builder()
                .name(request.getName())
                .sku(warehouseProduct.getSku())
                .price(request.getPrice())
                .description(request.getDescription())
                .category(category)
                .warehouseProduct(warehouseProduct)
                .stockQuantity(stockQuantity)
                .techSpecsJson(warehouseProduct.getTechSpecsJson()) // Copy thông số từ WarehouseProduct
                .build();

        // 6. Lưu Product
        Product savedProduct = productRepository.save(product);
        
        return com.doan.WEB_TMDT.common.dto.ApiResponse.success(
                "Đăng bán sản phẩm thành công", savedProduct);
    }

    @Override
    public com.doan.WEB_TMDT.common.dto.ApiResponse updatePublishedProduct(
            Long productId, 
            com.doan.WEB_TMDT.module.product.dto.CreateProductFromWarehouseRequest request) {
        
        // 1. Lấy Product
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm với ID: " + productId));

        // 2. Lấy Category nếu thay đổi
        if (request.getCategoryId() != null) {
            var category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new RuntimeException(
                            "Không tìm thấy danh mục với ID: " + request.getCategoryId()));
            product.setCategory(category);
        }

        // 3. Cập nhật thông tin
        if (request.getName() != null) {
            product.setName(request.getName());
        }
        if (request.getPrice() != null) {
            product.setPrice(request.getPrice());
        }
        if (request.getDescription() != null) {
            product.setDescription(request.getDescription());
        }

        // 4. Cập nhật số lượng tồn kho từ InventoryStock
        if (product.getWarehouseProduct() != null) {
            Optional<com.doan.WEB_TMDT.module.inventory.entity.InventoryStock> stockOpt = 
                    inventoryStockRepository.findByWarehouseProduct_Id(product.getWarehouseProduct().getId());
            if (stockOpt.isPresent()) {
                product.setStockQuantity(stockOpt.get().getSellable());
            }
        }

        // 5. Lưu Product
        Product updatedProduct = productRepository.save(product);
        
        return com.doan.WEB_TMDT.common.dto.ApiResponse.success(
                "Cập nhật sản phẩm thành công", updatedProduct);
    }

    @Override
    public com.doan.WEB_TMDT.common.dto.ApiResponse unpublishProduct(Long productId) {
        // 1. Lấy Product
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm với ID: " + productId));

        // 2. Xóa Product (gỡ khỏi trang bán)
        productRepository.delete(product);
        
        return com.doan.WEB_TMDT.common.dto.ApiResponse.success(
                "Gỡ sản phẩm khỏi trang bán thành công", null);
    }

    // === Product Images Implementation với Validation ===
    
    private static final int MAX_IMAGES_PER_PRODUCT = 9;
    
    @Override
    @org.springframework.transaction.annotation.Transactional
    public com.doan.WEB_TMDT.common.dto.ApiResponse addProductImage(Long productId, String imageUrl, Boolean isPrimary) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm"));

        // Nếu set làm ảnh chính, bỏ primary của ảnh khác
        if (isPrimary != null && isPrimary) {
            imageRepository.findByProductIdAndIsPrimaryTrue(productId)
                    .ifPresent(img -> {
                        img.setIsPrimary(false);
                        imageRepository.save(img);
                    });
        }

        // Tính display order
        long count = imageRepository.countByProductId(productId);
        
        com.doan.WEB_TMDT.module.product.entity.ProductImage image = 
            com.doan.WEB_TMDT.module.product.entity.ProductImage.builder()
                .product(product)
                .imageUrl(imageUrl)
                .displayOrder((int) count)
                .isPrimary(isPrimary != null ? isPrimary : count == 0)
                .build();

        com.doan.WEB_TMDT.module.product.entity.ProductImage saved = imageRepository.save(image);

        return com.doan.WEB_TMDT.common.dto.ApiResponse.success("Thêm ảnh thành công", toImageDTO(saved));
    }

    @Override
    public com.doan.WEB_TMDT.common.dto.ApiResponse getProductImages(Long productId) {
        List<com.doan.WEB_TMDT.module.product.dto.ProductImageDTO> images = 
            imageRepository.findByProductIdOrderByDisplayOrderAsc(productId)
                .stream()
                .map(this::toImageDTO)
                .collect(Collectors.toList());
        
        return com.doan.WEB_TMDT.common.dto.ApiResponse.success("Lấy danh sách ảnh thành công", images);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public com.doan.WEB_TMDT.common.dto.ApiResponse setPrimaryImage(Long productId, Long imageId) {
        // Bỏ primary của tất cả ảnh
        List<com.doan.WEB_TMDT.module.product.entity.ProductImage> images = 
            imageRepository.findByProductIdOrderByDisplayOrderAsc(productId);
        images.forEach(img -> img.setIsPrimary(false));
        imageRepository.saveAll(images);

        // Set primary cho ảnh được chọn
        com.doan.WEB_TMDT.module.product.entity.ProductImage image = imageRepository.findById(imageId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy ảnh"));
        
        image.setIsPrimary(true);
        com.doan.WEB_TMDT.module.product.entity.ProductImage saved = imageRepository.save(image);

        return com.doan.WEB_TMDT.common.dto.ApiResponse.success("Đã đặt làm ảnh chính", toImageDTO(saved));
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public com.doan.WEB_TMDT.common.dto.ApiResponse deleteProductImage(Long imageId) {
        com.doan.WEB_TMDT.module.product.entity.ProductImage image = imageRepository.findById(imageId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy ảnh"));
        
        Long productId = image.getProduct().getId();
        boolean wasPrimary = image.getIsPrimary();
        
        imageRepository.delete(image);
        
        // Nếu xóa ảnh chính, set ảnh đầu tiên còn lại làm primary
        if (wasPrimary) {
            List<com.doan.WEB_TMDT.module.product.entity.ProductImage> remaining = 
                imageRepository.findByProductIdOrderByDisplayOrderAsc(productId);
            if (!remaining.isEmpty()) {
                com.doan.WEB_TMDT.module.product.entity.ProductImage newPrimary = remaining.get(0);
                newPrimary.setIsPrimary(true);
                imageRepository.save(newPrimary);
            }
        }
        
        return com.doan.WEB_TMDT.common.dto.ApiResponse.success("Xóa ảnh thành công", null);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public com.doan.WEB_TMDT.common.dto.ApiResponse reorderProductImages(Long productId, List<Long> imageIds) {
        for (int i = 0; i < imageIds.size(); i++) {
            Long imageId = imageIds.get(i);
            final int order = i;
            imageRepository.findById(imageId).ifPresent(img -> {
                img.setDisplayOrder(order);
                imageRepository.save(img);
            });
        }
        
        return com.doan.WEB_TMDT.common.dto.ApiResponse.success("Sắp xếp lại thành công", null);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public com.doan.WEB_TMDT.common.dto.ApiResponse updateProductImage(
            Long imageId, 
            com.doan.WEB_TMDT.module.product.dto.ProductImageDTO dto) {
        
        com.doan.WEB_TMDT.module.product.entity.ProductImage image = imageRepository.findById(imageId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy ảnh"));

        // Cập nhật thông tin
        if (dto.getImageUrl() != null) {
            image.setImageUrl(dto.getImageUrl());
        }
        if (dto.getAltText() != null) {
            image.setAltText(dto.getAltText());
        }
        if (dto.getDisplayOrder() != null) {
            image.setDisplayOrder(dto.getDisplayOrder());
        }
        if (dto.getIsPrimary() != null && dto.getIsPrimary()) {
            // Nếu set làm ảnh chính, bỏ primary của ảnh khác
            Long productId = image.getProduct().getId();
            imageRepository.findByProductIdAndIsPrimaryTrue(productId)
                    .ifPresent(img -> {
                        img.setIsPrimary(false);
                        imageRepository.save(img);
                    });
            image.setIsPrimary(true);
        }

        com.doan.WEB_TMDT.module.product.entity.ProductImage updated = imageRepository.save(image);
        
        return com.doan.WEB_TMDT.common.dto.ApiResponse.success(
                "Cập nhật ảnh thành công", toImageDTO(updated));
    }

    private com.doan.WEB_TMDT.module.product.dto.ProductImageDTO toImageDTO(
            com.doan.WEB_TMDT.module.product.entity.ProductImage image) {
        return com.doan.WEB_TMDT.module.product.dto.ProductImageDTO.builder()
                .id(image.getId())
                .imageUrl(image.getImageUrl())
                .displayOrder(image.getDisplayOrder())
                .isPrimary(image.getIsPrimary())
                .altText(image.getAltText())
                .build();
    }

}
