package com.doan.WEB_TMDT.controller;

import com.doan.WEB_TMDT.common.dto.ApiResponse;
import com.doan.WEB_TMDT.module.product.controller.CategoryController;
import com.doan.WEB_TMDT.module.product.dto.CreateCategoryRequest;
import com.doan.WEB_TMDT.module.product.service.CategoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
public class CategoryControllerTest {

    @Mock
    private CategoryService categoryService;

    @InjectMocks
    private CategoryController categoryController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(categoryController).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void getAll() throws Exception {
        when(categoryService.getAll()).thenReturn(Collections.emptyList());
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk());
    }

    @Test
    void getTree() throws Exception {
        when(categoryService.getAllCategoriesTree()).thenReturn(ApiResponse.success("Tree", null));
        mockMvc.perform(get("/api/categories/tree"))
                .andExpect(status().isOk());
    }

    @Test
    void getActive() throws Exception {
        when(categoryService.getActiveCategories()).thenReturn(ApiResponse.success("Active", null));
        mockMvc.perform(get("/api/categories/active"))
                .andExpect(status().isOk());
    }

    @Test
    void getById() throws Exception {
        when(categoryService.getCategoryWithProducts(1L)).thenReturn(ApiResponse.success("Success", null));
        mockMvc.perform(get("/api/categories/1"))
                .andExpect(status().isOk());
    }

    @Test
    void create_WithAuthentication() throws Exception {
        CreateCategoryRequest req = new CreateCategoryRequest();
        req.setName("Test");
        when(categoryService.createCategory(any())).thenReturn(ApiResponse.success("Created", null));

        Authentication auth = new UsernamePasswordAuthenticationToken(
            "admin", "password", Collections.singletonList(new SimpleGrantedAuthority("ADMIN")));

        mockMvc.perform(post("/api/categories")
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void create_WithoutAuthentication() throws Exception {
        CreateCategoryRequest req = new CreateCategoryRequest();
        req.setName("Test");
        when(categoryService.createCategory(any())).thenReturn(ApiResponse.success("Created", null));

        mockMvc.perform(post("/api/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void update() throws Exception {
        CreateCategoryRequest req = new CreateCategoryRequest();
        req.setName("Updated");
        when(categoryService.updateCategory(eq(1L), any())).thenReturn(ApiResponse.success("Updated", null));
        mockMvc.perform(put("/api/categories/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void deleteCategory() throws Exception {
        mockMvc.perform(delete("/api/categories/1"))
                .andExpect(status().isOk());
    }

    @Test
    void testAuth_Authenticated() throws Exception {
        Authentication auth = new UsernamePasswordAuthenticationToken(
            "admin", "password", Collections.singletonList(new SimpleGrantedAuthority("ADMIN")));
            
        mockMvc.perform(get("/api/categories/test-auth").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void testAuth_NotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/categories/test-auth"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }
}
