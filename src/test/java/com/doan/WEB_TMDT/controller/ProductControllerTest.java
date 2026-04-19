package com.doan.WEB_TMDT.controller;

import com.doan.WEB_TMDT.common.dto.ApiResponse;
import com.doan.WEB_TMDT.module.inventory.entity.WarehouseProduct;
import com.doan.WEB_TMDT.module.inventory.service.ProductSpecificationService;
import com.doan.WEB_TMDT.module.product.controller.ProductController;
import com.doan.WEB_TMDT.module.product.dto.CreateProductFromWarehouseRequest;
import com.doan.WEB_TMDT.module.product.dto.ProductWithSpecsDTO;
import com.doan.WEB_TMDT.module.product.dto.PublishProductRequest;
import com.doan.WEB_TMDT.module.product.entity.Product;
import com.doan.WEB_TMDT.module.product.service.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
public class ProductControllerTest {

    @Mock
    private ProductService productService;

    @Mock
    private ProductSpecificationService productSpecificationService;

    @InjectMocks
    private ProductController productController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(productController).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("Lấy danh sách Product thành công (đủ nhánh active)")
    void getAll() throws Exception {
        Product p1 = new Product(); p1.setId(1L); p1.setActive(true);
        Product p2 = new Product(); p2.setId(2L); p2.setActive(false);
        Product p3 = new Product(); p3.setId(3L); p3.setActive(null);
        ProductWithSpecsDTO dto1 = new ProductWithSpecsDTO(); dto1.setId(1L);
        ProductWithSpecsDTO dto3 = new ProductWithSpecsDTO(); dto3.setId(3L);
        
        when(productService.getAll()).thenReturn(Arrays.asList(p1, p2, p3));
        when(productService.toProductWithSpecs(p1)).thenReturn(dto1);
        when(productService.toProductWithSpecs(p3)).thenReturn(dto3);

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("Lấy Product theo ID")
    void getById() throws Exception {
        Product p1 = new Product(); p1.setId(1L);
        ProductWithSpecsDTO dto = new ProductWithSpecsDTO(); dto.setId(1L);
        when(productService.getById(1L)).thenReturn(Optional.of(p1));
        when(productService.toProductWithSpecs(p1)).thenReturn(dto);

        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("Lấy Product theo ID không tồn tại")
    void getById_NotFound() throws Exception {
        when(productService.getById(99L)).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/products/99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("Lấy Product with-specs")
    void getByIdWithSpecs() throws Exception {
        Product p1 = new Product(); p1.setId(1L);
        ProductWithSpecsDTO dto = new ProductWithSpecsDTO(); dto.setId(1L);
        when(productService.getById(1L)).thenReturn(Optional.of(p1));
        when(productService.toProductWithSpecs(p1)).thenReturn(dto);

        mockMvc.perform(get("/api/products/1/with-specs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getWarehouseProductsForPublish() throws Exception {
        when(productService.getWarehouseProductsForPublish())
                .thenReturn(ApiResponse.success("OK", null));
        mockMvc.perform(get("/api/products/warehouse/list"))
                .andExpect(status().isOk());
    }

    @Test
    void createProductFromWarehouse() throws Exception {
        CreateProductFromWarehouseRequest req = new CreateProductFromWarehouseRequest();
        when(productService.createProductFromWarehouse(any())).thenReturn(ApiResponse.success("Created", null));
        mockMvc.perform(post("/api/products/warehouse/publish")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void updatePublishedProduct() throws Exception {
        CreateProductFromWarehouseRequest req = new CreateProductFromWarehouseRequest();
        when(productService.updatePublishedProduct(eq(1L), any())).thenReturn(ApiResponse.success("Updated", null));
        mockMvc.perform(put("/api/products/warehouse/publish/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void unpublishProduct() throws Exception {
        when(productService.unpublishProduct(1L)).thenReturn(ApiResponse.success("Unpublished", null));
        mockMvc.perform(delete("/api/products/warehouse/unpublish/1"))
                .andExpect(status().isOk());
    }

    @Test
    void publishProduct_Success() throws Exception {
        PublishProductRequest req = new PublishProductRequest();
        Product p = new Product();
        when(productService.publishProduct(any())).thenReturn(p);
        mockMvc.perform(post("/api/products/publish")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void publishProduct_Exception() throws Exception {
        PublishProductRequest req = new PublishProductRequest();
        when(productService.publishProduct(any())).thenThrow(new RuntimeException("Error"));
        mockMvc.perform(post("/api/products/publish")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void create() throws Exception {
        Product p = new Product();
        when(productService.create(any())).thenReturn(p);
        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(p)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void update_Success() throws Exception {
        Product p = new Product();
        when(productService.update(eq(1L), any())).thenReturn(p);
        mockMvc.perform(put("/api/products/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(p)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void update_NotFound() throws Exception {
        Product p = new Product();
        when(productService.update(eq(1L), any())).thenReturn(null);
        mockMvc.perform(put("/api/products/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(p)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void deleteProduct() throws Exception {
        doNothing().when(productService).delete(1L);
        mockMvc.perform(delete("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void searchBySpecs() throws Exception {
        WarehouseProduct wp1 = new WarehouseProduct(); 
        Product p = new Product(); wp1.setProduct(p);
        WarehouseProduct wp2 = new WarehouseProduct(); // null product
        when(productSpecificationService.searchBySpecValue("test"))
                .thenReturn(Arrays.asList(wp1, wp2));
                
        mockMvc.perform(get("/api/products/search-by-specs").param("keyword", "test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void filterBySpecs() throws Exception {
        WarehouseProduct wp1 = new WarehouseProduct(); 
        Product p = new Product(); wp1.setProduct(p);
        when(productSpecificationService.searchBySpecKeyAndValue("ram", "8GB"))
                .thenReturn(Arrays.asList(wp1));
                
        mockMvc.perform(get("/api/products/filter-by-specs")
                .param("key", "ram")
                .param("value", "8GB"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getProductImages() throws Exception {
        when(productService.getProductImages(1L)).thenReturn(ApiResponse.success("OK", null));
        mockMvc.perform(get("/api/products/1/images"))
                .andExpect(status().isOk());
    }

    @Test
    void addProductImage() throws Exception {
        Map<String, Object> req = new HashMap<>();
        req.put("imageUrl", "url");
        when(productService.addProductImage(eq(1L), eq("url"), eq(false)))
                .thenReturn(ApiResponse.success("OK", null));
        mockMvc.perform(post("/api/products/1/images")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void setPrimaryImage() throws Exception {
        when(productService.setPrimaryImage(1L, 2L)).thenReturn(ApiResponse.success("OK", null));
        mockMvc.perform(put("/api/products/1/images/2/primary"))
                .andExpect(status().isOk());
    }

    @Test
    void deleteProductImage() throws Exception {
        when(productService.deleteProductImage(1L)).thenReturn(ApiResponse.success("OK", null));
        mockMvc.perform(delete("/api/products/images/1"))
                .andExpect(status().isOk());
    }

    @Test
    void reorderProductImages() throws Exception {
        Map<String, List<Long>> req = new HashMap<>();
        req.put("imageIds", Arrays.asList(1L, 2L));
        when(productService.reorderProductImages(eq(1L), any())).thenReturn(ApiResponse.success("OK", null));
        mockMvc.perform(put("/api/products/1/images/reorder")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void toggleActive_Success() throws Exception {
        Product p = new Product(); p.setId(1L); p.setActive(false);
        when(productService.getById(1L)).thenReturn(Optional.of(p));
        when(productService.update(eq(1L), any())).thenReturn(p);
        mockMvc.perform(put("/api/products/1/toggle-active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Đã chuyển sản phẩm sang trạng thái đang bán"));
                
        // Test trường hợp currentActive = null
        Product p2 = new Product(); p2.setId(2L); p2.setActive(null);
        when(productService.getById(2L)).thenReturn(Optional.of(p2));
        when(productService.update(eq(2L), any())).thenReturn(p2);
        mockMvc.perform(put("/api/products/2/toggle-active"))
                .andExpect(status().isOk());
    }

    @Test
    void toggleActive_Exception() throws Exception {
        when(productService.getById(1L)).thenReturn(Optional.empty());
        mockMvc.perform(put("/api/products/1/toggle-active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }
}
