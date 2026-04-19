package com.doan.WEB_TMDT.service;

import com.doan.WEB_TMDT.module.inventory.entity.ProductDetail;
import com.doan.WEB_TMDT.module.inventory.repository.ProductDetailRepository;
import com.doan.WEB_TMDT.module.product.service.impl.ProductDetailServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ProductDetailServiceTest {

    @Mock
    private ProductDetailRepository productDetailRepository;

    @InjectMocks
    private ProductDetailServiceImpl productDetailService;

    private ProductDetail detail1;
    private ProductDetail detail2;

    @BeforeEach
    void setUp() {
        detail1 = ProductDetail.builder()
                .id(1L)
                .serialNumber("SN-123456")
                .importPrice(10000000.0)
                .note("Test product detail 1")
                .build();

        detail2 = ProductDetail.builder()
                .id(2L)
                .serialNumber("SN-789012")
                .importPrice(15000000.0)
                .build();
    }

    @Test
    @DisplayName("TC_PD_01: Lấy toàn bộ ProductDetail")
    void tc_pd_01_getAll() {
        when(productDetailRepository.findAll()).thenReturn(Arrays.asList(detail1, detail2));

        List<ProductDetail> list = productDetailService.getAll();

        assertEquals(2, list.size());
        verify(productDetailRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("TC_PD_02: Lấy ProductDetail theo ID thành công")
    void tc_pd_02_getById_Success() {
        when(productDetailRepository.findById(1L)).thenReturn(Optional.of(detail1));

        Optional<ProductDetail> result = productDetailService.getById(1L);

        assertTrue(result.isPresent());
        assertEquals("SN-123456", result.get().getSerialNumber());
    }

    @Test
    @DisplayName("TC_PD_03: Lấy ProductDetail theo ID không tồn tại")
    void tc_pd_03_getById_NotFound() {
        when(productDetailRepository.findById(99L)).thenReturn(Optional.empty());

        Optional<ProductDetail> result = productDetailService.getById(99L);

        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("TC_PD_04: Tạo mới ProductDetail")
    void tc_pd_04_create() {
        when(productDetailRepository.save(any(ProductDetail.class))).thenReturn(detail1);

        ProductDetail created = productDetailService.create(detail1);

        assertNotNull(created);
        assertEquals(1L, created.getId());
        verify(productDetailRepository, times(1)).save(detail1);
    }

    @Test
    @DisplayName("TC_PD_05: Cập nhật ProductDetail thành công")
    void tc_pd_05_update_Success() {
        when(productDetailRepository.existsById(1L)).thenReturn(true);
        when(productDetailRepository.save(any(ProductDetail.class))).thenReturn(detail1);

        ProductDetail updateInfo = ProductDetail.builder().serialNumber("SN-UPDATED").build();
        ProductDetail updated = productDetailService.update(1L, updateInfo);

        assertNotNull(updated);
        assertEquals(1L, updateInfo.getId()); // ID should be set within update method
        verify(productDetailRepository, times(1)).save(updateInfo);
    }

    @Test
    @DisplayName("TC_PD_06: Cập nhật ProductDetail không tồn tại")
    void tc_pd_06_update_NotFound() {
        when(productDetailRepository.existsById(99L)).thenReturn(false);

        ProductDetail updated = productDetailService.update(99L, detail1);

        assertNull(updated);
        verify(productDetailRepository, never()).save(any());
    }

    @Test
    @DisplayName("TC_PD_07: Xóa ProductDetail")
    void tc_pd_07_delete() {
        doNothing().when(productDetailRepository).deleteById(1L);

        productDetailService.delete(1L);

        verify(productDetailRepository, times(1)).deleteById(1L);
    }
}
