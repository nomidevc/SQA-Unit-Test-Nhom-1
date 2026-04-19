package com.doan.WEB_TMDT.controller;

import com.doan.WEB_TMDT.common.dto.ApiResponse;
import com.doan.WEB_TMDT.module.inventory.entity.ProductDetail;
import com.doan.WEB_TMDT.module.product.controller.ProductDetailController;
import com.doan.WEB_TMDT.module.product.service.ProductDetailService;
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
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
public class ProductDetailControllerTest {

    @Mock
    private ProductDetailService productDetailService;

    @InjectMocks
    private ProductDetailController productDetailController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(productDetailController).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("TC_PD_CTRL_01: Lấy toàn bộ Product Detail")
    void tc_pd_ctrl_01_getAll() throws Exception {
        when(productDetailService.getAll()).thenReturn(Arrays.asList(new ProductDetail()));

        mockMvc.perform(get("/api/product-details"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("TC_PD_CTRL_02: Lấy Product Detail theo ID")
    void tc_pd_ctrl_02_getById_Success() throws Exception {
        ProductDetail pd = new ProductDetail(); pd.setId(1L);
        when(productDetailService.getById(1L)).thenReturn(Optional.of(pd));

        mockMvc.perform(get("/api/product-details/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("TC_PD_CTRL_03: Lấy Product Detail theo ID không tồn tại")
    void tc_pd_ctrl_03_getById_NotFound() throws Exception {
        when(productDetailService.getById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/product-details/99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("TC_PD_CTRL_04: Tạo Product Detail")
    void tc_pd_ctrl_04_create() throws Exception {
        ProductDetail pd = new ProductDetail(); pd.setId(1L);
        when(productDetailService.create(any())).thenReturn(pd);

        mockMvc.perform(post("/api/product-details")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ProductDetail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("TC_PD_CTRL_05: Cập nhật Product Detail thành công")
    void tc_pd_ctrl_05_update_Success() throws Exception {
        ProductDetail pd = new ProductDetail(); pd.setId(1L);
        when(productDetailService.update(eq(1L), any())).thenReturn(pd);

        mockMvc.perform(put("/api/product-details/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(pd)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("TC_PD_CTRL_06: Cập nhật Product Detail không tồn tại")
    void tc_pd_ctrl_06_update_NotFound() throws Exception {
        when(productDetailService.update(eq(99L), any())).thenReturn(null);

        mockMvc.perform(put("/api/product-details/99")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ProductDetail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("TC_PD_CTRL_07: Xóa Product Detail")
    void tc_pd_ctrl_07_delete() throws Exception {
        mockMvc.perform(delete("/api/product-details/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Xóa thành công"));
    }
}
