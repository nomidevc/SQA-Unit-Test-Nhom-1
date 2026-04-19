package com.doan.WEB_TMDT.controller;

import com.doan.WEB_TMDT.common.dto.ApiResponse;
import com.doan.WEB_TMDT.module.cart.controller.CartController;
import com.doan.WEB_TMDT.module.cart.dto.AddToCartRequest;
import com.doan.WEB_TMDT.module.cart.dto.UpdateCartItemRequest;
import com.doan.WEB_TMDT.module.cart.service.CartService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * WEB LAYER TEST FOR CART CONTROLLER (15 TEST CASES)
 * Kiểm tra HTTP Status Code, URL Mapping, Request Validation, Authentication.
 * CartService được MOCK hoàn toàn — không cần DB.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class CartControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CartService cartService;

    // =========================================================================
    // GROUP 1: AUTHENTICATION (TC_API_01 -> TC_API_03)
    // =========================================================================

    @Test
    @DisplayName("TC_API_01: Gọi API không có Token -> Phải trả về 401 hoặc 403")
    public void tc_api_01() throws Exception {
        mockMvc.perform(get("/api/cart"))
                .andExpect(status().is4xxClientError()); // 401 Unauthorized hoặc 403 Forbidden
    }

    @Test
    @DisplayName("TC_API_02: Gọi API với Token hợp lệ (Role=CUSTOMER) -> Phải được phép")
    @WithMockUser(username = "customer@test.com", authorities = "CUSTOMER")
    public void tc_api_02() throws Exception {
        when(cartService.getCustomerIdByEmail(anyString())).thenReturn(1L);
        when(cartService.getCart(anyLong())).thenReturn(ApiResponse.success("Giỏ hàng của bạn", null));

        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC_API_03: Gọi API với Token hợp lệ (Role=ADMIN) -> Phải được phép")
    @WithMockUser(username = "admin@test.com", authorities = "ADMIN")
    public void tc_api_03() throws Exception {
        when(cartService.getCustomerIdByEmail(anyString())).thenReturn(99L);
        when(cartService.getCart(anyLong())).thenReturn(ApiResponse.success("Giỏ hàng của bạn", null));

        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // GROUP 2: GET CART (TC_API_04 -> TC_API_05)
    // =========================================================================

    @Test
    @DisplayName("TC_API_04: GET /api/cart -> Trả về HTTP 200 và JSON đúng format")
    @WithMockUser(username = "customer@test.com", authorities = "CUSTOMER")
    public void tc_api_04() throws Exception {
        when(cartService.getCustomerIdByEmail(anyString())).thenReturn(1L);
        when(cartService.getCart(1L)).thenReturn(ApiResponse.success("Giỏ hàng của bạn", null));

        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Giỏ hàng của bạn"));
    }

    @Test
    @DisplayName("TC_API_05: GET /api/cart -> CartService.getCart() được gọi đúng 1 lần")
    @WithMockUser(username = "customer@test.com", authorities = "CUSTOMER")
    public void tc_api_05() throws Exception {
        when(cartService.getCustomerIdByEmail(anyString())).thenReturn(1L);
        when(cartService.getCart(anyLong())).thenReturn(ApiResponse.success("Giỏ hàng của bạn", null));

        mockMvc.perform(get("/api/cart"));

        verify(cartService, times(1)).getCart(1L);
    }

    // =========================================================================
    // GROUP 3: ADD TO CART (TC_API_06 -> TC_API_09)
    // =========================================================================

    @Test
    @DisplayName("TC_API_06: POST /api/cart/items với body hợp lệ -> HTTP 200")
    @WithMockUser(username = "customer@test.com", authorities = "CUSTOMER")
    public void tc_api_06() throws Exception {
        AddToCartRequest req = new AddToCartRequest();
        req.setProductId(1L);
        req.setQuantity(2);

        when(cartService.getCustomerIdByEmail(anyString())).thenReturn(1L);
        when(cartService.addToCart(anyLong(), any())).thenReturn(ApiResponse.success("Đã thêm vào giỏ hàng", null));

        mockMvc.perform(post("/api/cart/items")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đã thêm vào giỏ hàng"));
    }

    @Test
    @DisplayName("TC_API_07: POST /api/cart/items thiếu productId trong body -> HTTP 400")
    @WithMockUser(username = "customer@test.com", authorities = "CUSTOMER")
    public void tc_api_07() throws Exception {
        // Body rỗng - thiếu productId và quantity
        String emptyBody = "{}";

        mockMvc.perform(post("/api/cart/items")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emptyBody))
                .andExpect(status().is4xxClientError()); // 400 Bad Request
    }

    @Test
    @DisplayName("TC_API_08: POST /api/cart/items không có body -> HTTP 400")
    @WithMockUser(username = "customer@test.com", authorities = "CUSTOMER")
    public void tc_api_08() throws Exception {
        mockMvc.perform(post("/api/cart/items")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("TC_API_09: POST /api/cart/items -> CartService.addToCart() được gọi đúng 1 lần")
    @WithMockUser(username = "customer@test.com", authorities = "CUSTOMER")
    public void tc_api_09() throws Exception {
        AddToCartRequest req = new AddToCartRequest();
        req.setProductId(1L);
        req.setQuantity(1);

        when(cartService.getCustomerIdByEmail(anyString())).thenReturn(1L);
        when(cartService.addToCart(anyLong(), any())).thenReturn(ApiResponse.success("Đã thêm vào giỏ hàng", null));

        mockMvc.perform(post("/api/cart/items")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)));

        verify(cartService, times(1)).addToCart(eq(1L), any(AddToCartRequest.class));
    }

    // =========================================================================
    // GROUP 4: UPDATE CART ITEM (TC_API_10 -> TC_API_11)
    // =========================================================================

    @Test
    @DisplayName("TC_API_10: PUT /api/cart/items/{itemId} hợp lệ -> HTTP 200")
    @WithMockUser(username = "customer@test.com", authorities = "CUSTOMER")
    public void tc_api_10() throws Exception {
        UpdateCartItemRequest req = new UpdateCartItemRequest();
        req.setQuantity(5);

        when(cartService.getCustomerIdByEmail(anyString())).thenReturn(1L);
        when(cartService.updateCartItem(anyLong(), anyLong(), any())).thenReturn(ApiResponse.success("Đã cập nhật giỏ hàng", null));

        mockMvc.perform(put("/api/cart/items/100")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC_API_11: PUT /api/cart/items/{itemId} với itemId không phải số -> HTTP 400")
    @WithMockUser(username = "customer@test.com", authorities = "CUSTOMER")
    public void tc_api_11() throws Exception {
        mockMvc.perform(put("/api/cart/items/abc") // ID là chữ, không phải số
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": 5}"))
                .andExpect(status().is4xxClientError());
    }

    // =========================================================================
    // GROUP 5: REMOVE & CLEAR (TC_API_12 -> TC_API_15)
    // =========================================================================

    @Test
    @DisplayName("TC_API_12: DELETE /api/cart/items/{itemId} hợp lệ -> HTTP 200")
    @WithMockUser(username = "customer@test.com", authorities = "CUSTOMER")
    public void tc_api_12() throws Exception {
        when(cartService.getCustomerIdByEmail(anyString())).thenReturn(1L);
        when(cartService.removeCartItem(anyLong(), anyLong())).thenReturn(ApiResponse.success("Đã xóa sản phẩm khỏi giỏ hàng", null));

        mockMvc.perform(delete("/api/cart/items/100").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đã xóa sản phẩm khỏi giỏ hàng"));
    }

    @Test
    @DisplayName("TC_API_13: DELETE /api/cart/items/{itemId} -> CartService.removeCartItem() được gọi đúng 1 lần")
    @WithMockUser(username = "customer@test.com", authorities = "CUSTOMER")
    public void tc_api_13() throws Exception {
        when(cartService.getCustomerIdByEmail(anyString())).thenReturn(1L);
        when(cartService.removeCartItem(anyLong(), anyLong())).thenReturn(ApiResponse.success("Đã xóa sản phẩm khỏi giỏ hàng", null));

        mockMvc.perform(delete("/api/cart/items/100").with(csrf()));

        verify(cartService, times(1)).removeCartItem(eq(1L), eq(100L));
    }

    @Test
    @DisplayName("TC_API_14: DELETE /api/cart (Clear All) -> HTTP 200")
    @WithMockUser(username = "customer@test.com", authorities = "CUSTOMER")
    public void tc_api_14() throws Exception {
        when(cartService.getCustomerIdByEmail(anyString())).thenReturn(1L);
        when(cartService.clearCart(anyLong())).thenReturn(ApiResponse.success("Đã xóa tất cả sản phẩm", null));

        mockMvc.perform(delete("/api/cart").with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC_API_15: DELETE /api/cart/items/{itemId} không có CSRF token -> HTTP 403")
    @WithMockUser(username = "customer@test.com", authorities = "CUSTOMER")
    public void tc_api_15() throws Exception {
        // Gọi DELETE mà không có .with(csrf()) -> Sẽ bị reject
        mockMvc.perform(delete("/api/cart/items/100"))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // GROUP 6: METHOD NOT ALLOWED (TC_API_16 -> TC_API_18)
    // Kiểm tra xem endpoint có từ chối đúng HTTP method không
    // =========================================================================

    @Test
    @DisplayName("TC_API_16: POST /api/cart (sai method, đúng phải là GET) -> HTTP 405")
    @WithMockUser(username = "customer@test.com", authorities = "CUSTOMER")
    public void tc_api_16() throws Exception {
        mockMvc.perform(post("/api/cart").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("TC_API_17: GET /api/cart/items/100 (sai method, đúng phải là PUT/DELETE) -> HTTP 405")
    @WithMockUser(username = "customer@test.com", authorities = "CUSTOMER")
    public void tc_api_17() throws Exception {
        mockMvc.perform(get("/api/cart/items/100"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("TC_API_18: PATCH /api/cart/items/100 (method không được hỗ trợ) -> HTTP 405")
    @WithMockUser(username = "customer@test.com", authorities = "CUSTOMER")
    public void tc_api_18() throws Exception {
        mockMvc.perform(patch("/api/cart/items/100").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": 5}"))
                .andExpect(status().isMethodNotAllowed());
    }

    // =========================================================================
    // GROUP 7: CONTENT-TYPE VALIDATION (TC_API_19 -> TC_API_21)
    // Kiểm tra xem API có yêu cầu đúng Content-Type không
    // =========================================================================

    @Test
    @DisplayName("TC_API_19: POST /api/cart/items với Content-Type text/plain -> HTTP 415")
    @WithMockUser(username = "customer@test.com", authorities = "CUSTOMER")
    public void tc_api_19() throws Exception {
        mockMvc.perform(post("/api/cart/items").with(csrf())
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("productId=1&quantity=1"))
                .andExpect(status().is4xxClientError()); // 415 Unsupported Media Type
    }

    @Test
    @DisplayName("TC_API_20: PUT /api/cart/items/{itemId} với Content-Type XML -> HTTP 415")
    @WithMockUser(username = "customer@test.com", authorities = "CUSTOMER")
    public void tc_api_20() throws Exception {
        mockMvc.perform(put("/api/cart/items/100").with(csrf())
                        .contentType(MediaType.APPLICATION_XML)
                        .content("<quantity>5</quantity>"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("TC_API_21: POST /api/cart/items với JSON bị lỗi cú pháp (malformed JSON) -> HTTP 400")
    @WithMockUser(username = "customer@test.com", authorities = "CUSTOMER")
    public void tc_api_21() throws Exception {
        String malformedJson = "{productId: 1, quantity: }"; // JSON sai cú pháp

        mockMvc.perform(post("/api/cart/items").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedJson))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // GROUP 8: SERVICE FAILURE PROPAGATION (TC_API_22 -> TC_API_25)
    // Khi CartService trả về lỗi, Controller phải truyền đúng response xuống client
    // =========================================================================

    @Test
    @DisplayName("TC_API_22: addToCart -> Service trả về failure -> Controller phải trả đúng response lỗi")
    @WithMockUser(username = "customer@test.com", authorities = "CUSTOMER")
    public void tc_api_22() throws Exception {
        AddToCartRequest req = new AddToCartRequest();
        req.setProductId(1L);
        req.setQuantity(1);

        when(cartService.getCustomerIdByEmail(anyString())).thenReturn(1L);
        when(cartService.addToCart(anyLong(), any()))
                .thenReturn(ApiResponse.error("Sản phẩm đang tạm hết hàng"));

        mockMvc.perform(post("/api/cart/items").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk()) // HTTP vẫn 200, nhưng body có success=false
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Sản phẩm đang tạm hết hàng"));
    }

    @Test
    @DisplayName("TC_API_23: updateCartItem -> Service trả về failure -> Controller truyền đúng response")
    @WithMockUser(username = "customer@test.com", authorities = "CUSTOMER")
    public void tc_api_23() throws Exception {
        UpdateCartItemRequest req = new UpdateCartItemRequest();
        req.setQuantity(999);

        when(cartService.getCustomerIdByEmail(anyString())).thenReturn(1L);
        when(cartService.updateCartItem(anyLong(), anyLong(), any()))
                .thenReturn(ApiResponse.error("Số lượng sản phẩm không đủ"));

        mockMvc.perform(put("/api/cart/items/100").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("TC_API_24: removeCartItem -> Service trả về failure -> Controller truyền đúng response")
    @WithMockUser(username = "customer@test.com", authorities = "CUSTOMER")
    public void tc_api_24() throws Exception {
        when(cartService.getCustomerIdByEmail(anyString())).thenReturn(1L);
        when(cartService.removeCartItem(anyLong(), anyLong()))
                .thenReturn(ApiResponse.error("Bạn không có quyền xóa sản phẩm này"));

        mockMvc.perform(delete("/api/cart/items/100").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Bạn không có quyền xóa sản phẩm này"));
    }

    @Test
    @DisplayName("TC_API_25: getCart -> Service throw RuntimeException -> Controller phải xử lý")
    @WithMockUser(username = "customer@test.com", authorities = "CUSTOMER")
    public void tc_api_25() throws Exception {
        when(cartService.getCustomerIdByEmail(anyString())).thenReturn(1L);
        when(cartService.getCart(anyLong())).thenThrow(new RuntimeException("Lỗi hệ thống"));

        mockMvc.perform(get("/api/cart"))
                .andExpect(status().is5xxServerError()); // 500 Internal Server Error
    }

    // =========================================================================
    // GROUP 9: ROLE & AUTHORIZATION (TC_API_26 -> TC_API_28)
    // =========================================================================

    @Test
    @DisplayName("TC_API_26: Gọi API với Role=USER (không phải CUSTOMER/ADMIN) -> HTTP 403")
    @WithMockUser(username = "basic@test.com", authorities = "USER")
    public void tc_api_26() throws Exception {
        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TC_API_27: Gọi API với Role=STAFF -> HTTP 403")
    @WithMockUser(username = "staff@test.com", authorities = "STAFF")
    public void tc_api_27() throws Exception {
        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("TC_API_28: Gọi API với nhiều Role, có CUSTOMER -> HTTP 200")
    @WithMockUser(username = "multi@test.com", authorities = {"CUSTOMER", "REVIEWER"})
    public void tc_api_28() throws Exception {
        when(cartService.getCustomerIdByEmail(anyString())).thenReturn(5L);
        when(cartService.getCart(anyLong())).thenReturn(ApiResponse.success("Giỏ hàng của bạn", null));

        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // GROUP 10: RESPONSE STRUCTURE VALIDATION (TC_API_29 -> TC_API_30)
    // Kiểm tra cấu trúc JSON response trả về đúng format không
    // =========================================================================

    @Test
    @DisplayName("TC_API_29: Response phải luôn có field 'success' và 'message'")
    @WithMockUser(username = "customer@test.com", authorities = "CUSTOMER")
    public void tc_api_29() throws Exception {
        when(cartService.getCustomerIdByEmail(anyString())).thenReturn(1L);
        when(cartService.getCart(anyLong())).thenReturn(ApiResponse.success("Giỏ hàng của bạn", null));

        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").exists())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("TC_API_30: addToCart thành công -> Response có success=true và message đúng")
    @WithMockUser(username = "customer@test.com", authorities = "CUSTOMER")
    public void tc_api_30() throws Exception {
        AddToCartRequest req = new AddToCartRequest();
        req.setProductId(1L);
        req.setQuantity(2);

        when(cartService.getCustomerIdByEmail(anyString())).thenReturn(1L);
        when(cartService.addToCart(anyLong(), any())).thenReturn(ApiResponse.success("Đã thêm vào giỏ hàng", null));

        mockMvc.perform(post("/api/cart/items").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Đã thêm vào giỏ hàng"));
    }
}
