package com.doan.WEB_TMDT.controller;

import com.doan.WEB_TMDT.common.dto.ApiResponse;
import com.doan.WEB_TMDT.common.exception.GlobalExceptionHandler;
import com.doan.WEB_TMDT.config.SecurityConfig;
import com.doan.WEB_TMDT.module.auth.controller.AuthController;
import com.doan.WEB_TMDT.module.auth.dto.*;
import com.doan.WEB_TMDT.module.auth.service.AuthService;
import com.doan.WEB_TMDT.module.auth.service.UserService;
import com.doan.WEB_TMDT.security.JwtAuthenticationFilter;
import com.doan.WEB_TMDT.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ================================================================
 * SQA UNIT TEST — MODULE: AUTH CONTROLLER (Web Layer)
 * ================================================================
 * Test Suite ID  : TS_AUTH_CTRL
 * Số test cases  : 15 (TC_AUTH_CTRL_001 – TC_AUTH_CTRL_015)
 * Framework      : JUnit 5 + Mockito + MockMvc (@WebMvcTest)
 * SUT            : com.doan.WEB_TMDT.module.auth.controller.AuthController
 *
 * Endpoints được kiểm thử:
 *   POST /api/auth/register/send-otp      (public)
 *   POST /api/auth/register/verify-otp    (public, try-catch nội bộ)
 *   POST /api/auth/login                  (public)
 *   POST /api/auth/first-change-password  (public)
 *   POST /api/auth/change-password        (yêu cầu authentication)
 *   GET  /api/auth/me                     (kiểm tra authentication != null)
 *
 * Kiến trúc quan trọng cần hiểu:
 *   ▸ AuthController trả về ApiResponse (KHÔNG phải ResponseEntity).
 *     → HTTP status mặc định luôn là 200 dù success/error, TRỪ KHI exception
 *       bị ném ra khỏi controller → GlobalExceptionHandler → 400 hoặc 500.
 *   ▸ /api/auth/** là permitAll() → Spring Security KHÔNG chặn 401/403.
 *   ▸ GET /me: controller tự kiểm tra authentication == null → trả error 200.
 *   ▸ POST /change-password: gọi authentication.getName() mà không null-check
 *     → NullPointerException khi không có auth → GlobalExceptionHandler → 400.
 *   ▸ POST /register/verify-otp: có try-catch nội bộ → exception bị bắt TRƯỚC
 *     GlobalExceptionHandler → luôn trả HTTP 200.
 *
 * Mock infrastructure:
 *   @MockBean JwtService          — tránh @Value injection cho app.jwt.secret
 *   @MockBean UserDetailsService  — Spring Security auto-config yêu cầu
 *   (JwtAuthenticationFilter chạy thật nhưng dùng mocked deps → vô hại)
 * ================================================================
 */
@WebMvcTest(value = AuthController.class, properties = {
        "server.port=0"
})
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JwtAuthenticationFilter.class})
@DisplayName("AuthController — Unit Tests (WebMvcTest + MockMvc)")
class AuthControllerTest {

    // ================================================================
    //  INFRASTRUCTURE
    // ================================================================

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // Service mocks (SUT dependencies)
    @MockBean
    private AuthService authService;

    @MockBean
    private UserService userService;

    // Spring Security / JWT infrastructure mocks
    // (required so JwtAuthenticationFilter can be instantiated with mocked deps)
    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserDetailsService userDetailsService;

        // Override application startup runner so WebMvcTest fails only on controller behavior,
        // not on unrelated application bootstrap wiring.
        @MockBean(name = "initAdmin")
        private CommandLineRunner initAdmin;

    // ================================================================
    //  CONSTANTS
    // ================================================================

    private static final String BASE = "/api/auth";
    private static final String MOCK_TOKEN =
            "eyJhbGciOiJIUzI1NiJ9.mockPayload.mockSignature";
    private static final String USER_EMAIL = "test@example.com";

    @AfterEach
    void resetMocks() {
                reset(authService, userService, jwtService, userDetailsService, initAdmin);
    }

    // ================================================================
    // ================================================================
    //  [REGISTER API] POST /api/auth/register/send-otp
    // ================================================================
    // ================================================================

    /**
     * TC_AUTH_CTRL_001 — REGISTER: sendOtp thành công → HTTP 200, success=true
     * ─────────────────────────────────────────────────────────────────
     * GIVEN: authService.sendOtp() trả về ApiResponse.success(...)
     * WHEN : POST /api/auth/register/send-otp với RegisterRequest hợp lệ
     * THEN : HTTP 200
     *        $.success = true
     *        $.message = "Mã OTP đã được gửi đến email của bạn!"
     *        authService.sendOtp() được gọi đúng 1 lần
     */
    @Test
    @DisplayName("TC_AUTH_CTRL_001 — Register sendOtp: Success → HTTP 200, success=true")
    void TC_AUTH_CTRL_001_register_sendOtp_success() throws Exception {
        // GIVEN
        RegisterRequest req = new RegisterRequest();
        req.setEmail(USER_EMAIL);
        req.setFullName("Nguyễn Văn A");
        req.setPassword("ValidPass@123");
        req.setPhone("0911111111");
        req.setAddress("123 Đường Test, TP.HCM");

        when(authService.sendOtp(any(RegisterRequest.class)))
                .thenReturn(ApiResponse.success("Mã OTP đã được gửi đến email của bạn!"));

        // WHEN & THEN
        mockMvc.perform(post(BASE + "/register/send-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Mã OTP đã được gửi đến email của bạn!"))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(authService, times(1)).sendOtp(any(RegisterRequest.class));
        verify(userService, never()).login(any());
    }

    /**
     * TC_AUTH_CTRL_002 — REGISTER: Email đã tồn tại → HTTP 200, success=false
     * ─────────────────────────────────────────────────────────────────
     * GIVEN: authService.sendOtp() trả về ApiResponse.error("Email đã được sử dụng!")
     * WHEN : POST /api/auth/register/send-otp với email đã có trong DB
     * THEN : HTTP 200  (controller trả ApiResponse — không ném exception)
     *        $.success = false
     *        $.message = "Email đã được sử dụng!"
     */
    @Test
    @DisplayName("TC_AUTH_CTRL_002 — Register sendOtp: Email duplicate → HTTP 200, success=false")
    void TC_AUTH_CTRL_002_register_sendOtp_emailAlreadyExists() throws Exception {
        // GIVEN
        RegisterRequest req = new RegisterRequest();
        req.setEmail("existing@example.com");
        req.setFullName("Trần Thị B");
        req.setPassword("ValidPass@123");
        req.setPhone("0922222222");

        when(authService.sendOtp(any(RegisterRequest.class)))
                .thenReturn(ApiResponse.error("Email đã được sử dụng!"));

        // WHEN & THEN
        mockMvc.perform(post(BASE + "/register/send-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Email đã được sử dụng!"));

        verify(authService, times(1)).sendOtp(any(RegisterRequest.class));
    }

    /**
     * TC_AUTH_CTRL_003 — REGISTER: authService ném RuntimeException → HTTP 400
     * ─────────────────────────────────────────────────────────────────
     * GIVEN: authService.sendOtp() ném RuntimeException (DB down, timeout...)
     * WHEN : POST /api/auth/register/send-otp
     * THEN : RuntimeException propagate ra khỏi controller
     *        GlobalExceptionHandler.handleRuntimeException() bắt → HTTP 400
     *        $.success = false
     *        $.message = thông điệp của exception
     */
    @Test
    @DisplayName("TC_AUTH_CTRL_003 — Register sendOtp: RuntimeException → GlobalExceptionHandler → HTTP 400")
    void TC_AUTH_CTRL_003_register_sendOtp_runtimeException_returns400() throws Exception {
        // GIVEN
        RegisterRequest req = new RegisterRequest();
        req.setEmail(USER_EMAIL);
        req.setFullName("Lê Văn C");
        req.setPassword("ValidPass@123");
        req.setPhone("0933333333");

        when(authService.sendOtp(any(RegisterRequest.class)))
                .thenThrow(new RuntimeException("Lỗi kết nối cơ sở dữ liệu: Connection refused"));

        // WHEN & THEN
        mockMvc.perform(post(BASE + "/register/send-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())                           // HTTP 400
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message")
                        .value("Lỗi kết nối cơ sở dữ liệu: Connection refused"));

        verify(authService, times(1)).sendOtp(any(RegisterRequest.class));
    }

    // ================================================================
    // ================================================================
    //  [REGISTER API] POST /api/auth/register/verify-otp
    // ================================================================
    // ================================================================

    /**
     * TC_AUTH_CTRL_004 — VERIFY OTP: Xác minh thành công → HTTP 200, success=true
     * ─────────────────────────────────────────────────────────────────
     * GIVEN: authService.verifyOtpAndRegister() trả về ApiResponse.success(...)
     * WHEN : POST /api/auth/register/verify-otp với OTP đúng và còn hạn
     * THEN : HTTP 200
     *        $.success = true
     *        $.message = "Xác minh OTP thành công, tài khoản đã được tạo!"
     */
    @Test
    @DisplayName("TC_AUTH_CTRL_004 — VerifyOtp: Success → HTTP 200, success=true")
    void TC_AUTH_CTRL_004_verifyOtp_success() throws Exception {
        // GIVEN
        OtpVerifyRequest req = new OtpVerifyRequest();
        req.setEmail(USER_EMAIL);
        req.setOtpCode("123456");

        when(authService.verifyOtpAndRegister(any(OtpVerifyRequest.class)))
                .thenReturn(ApiResponse.success("Xác minh OTP thành công, tài khoản đã được tạo!"));

        // WHEN & THEN
        mockMvc.perform(post(BASE + "/register/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message")
                        .value("Xác minh OTP thành công, tài khoản đã được tạo!"));

        verify(authService, times(1)).verifyOtpAndRegister(any(OtpVerifyRequest.class));
    }

    /**
     * TC_AUTH_CTRL_005 — VERIFY OTP: OTP không hợp lệ → HTTP 200, success=false
     * ─────────────────────────────────────────────────────────────────
     * GIVEN: authService.verifyOtpAndRegister() trả về ApiResponse.error("Mã OTP không hợp lệ!")
     * WHEN : POST /api/auth/register/verify-otp với OTP sai
     * THEN : HTTP 200
     *        $.success = false
     *        $.message = "Mã OTP không hợp lệ!"
     */
    @Test
    @DisplayName("TC_AUTH_CTRL_005 — VerifyOtp: Invalid OTP → HTTP 200, success=false")
    void TC_AUTH_CTRL_005_verifyOtp_invalidOtp() throws Exception {
        // GIVEN
        OtpVerifyRequest req = new OtpVerifyRequest();
        req.setEmail(USER_EMAIL);
        req.setOtpCode("000000");

        when(authService.verifyOtpAndRegister(any(OtpVerifyRequest.class)))
                .thenReturn(ApiResponse.error("Mã OTP không hợp lệ!"));

        // WHEN & THEN
        mockMvc.perform(post(BASE + "/register/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Mã OTP không hợp lệ!"));
    }

    /**
     * TC_AUTH_CTRL_006 — VERIFY OTP: service ném RuntimeException → controller bắt → HTTP 200
     * ─────────────────────────────────────────────────────────────────
     * Ghi chú kiến trúc:
     *   verifyOtp() có try-catch nội bộ → exception KHÔNG propagate ra GlobalExceptionHandler.
     *   Controller trả ApiResponse.error("Lỗi xác thực OTP: " + e.getMessage()) với HTTP 200.
     *   Đây KHÁC với các endpoint khác (HTTP 400 từ GlobalExceptionHandler).
     *
     * GIVEN: authService.verifyOtpAndRegister() ném RuntimeException("User creation failed")
     * WHEN : POST /api/auth/register/verify-otp
     * THEN : HTTP 200  (controller bắt exception, KHÔNG phải GlobalExceptionHandler)
     *        $.success = false
     *        $.message bắt đầu bằng "Lỗi xác thực OTP:"
     */
    @Test
    @DisplayName("TC_AUTH_CTRL_006 — VerifyOtp: RuntimeException → controller bắt nội bộ → HTTP 200 (không phải 400)")
    void TC_AUTH_CTRL_006_verifyOtp_runtimeException_caughtByControllerNotGlobalHandler() throws Exception {
        // GIVEN
        OtpVerifyRequest req = new OtpVerifyRequest();
        req.setEmail(USER_EMAIL);
        req.setOtpCode("999999");

        when(authService.verifyOtpAndRegister(any(OtpVerifyRequest.class)))
                .thenThrow(new RuntimeException("User creation failed: constraint violation"));

        // WHEN & THEN — HTTP 200 vì controller bắt exception trong try-catch
        mockMvc.perform(post(BASE + "/register/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())                                    // HTTP 200, NOT 400
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message",
                        Matchers.startsWith("Lỗi xác thực OTP:")));

        verify(authService, times(1)).verifyOtpAndRegister(any(OtpVerifyRequest.class));
    }

    // ================================================================
    // ================================================================
    //  [LOGIN API] POST /api/auth/login
    // ================================================================
    // ================================================================

    /**
     * TC_AUTH_CTRL_007 — LOGIN: Đăng nhập thành công → HTTP 200, success=true, token trả về
     * ─────────────────────────────────────────────────────────────────
     * GIVEN: userService.login() trả về success với LoginResponse chứa JWT token
     * WHEN : POST /api/auth/login với email và password hợp lệ
     * THEN : HTTP 200
     *        $.success = true
     *        $.message = "Đăng nhập thành công!"
     *        $.data.token = MOCK_TOKEN
     *        $.data.email = USER_EMAIL
     *        $.data.role  = "CUSTOMER"
     *        userService.login() được gọi đúng 1 lần
     */
    @Test
    @DisplayName("TC_AUTH_CTRL_007 — Login: Success → HTTP 200, success=true, JWT token trong data")
    void TC_AUTH_CTRL_007_login_success_tokenReturned() throws Exception {
        // GIVEN
        LoginRequest req = new LoginRequest();
        req.setEmail(USER_EMAIL);
        req.setPassword("ValidPass@123");

        LoginResponse loginResp = new LoginResponse(
                MOCK_TOKEN, 1L, USER_EMAIL, "Nguyễn Văn A",
                "0911111111", "123 Đường Test", "CUSTOMER", null, "ACTIVE", null
        );

        when(userService.login(any(LoginRequest.class)))
                .thenReturn(ApiResponse.success("Đăng nhập thành công!", loginResp));

        // WHEN & THEN
        mockMvc.perform(post(BASE + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Đăng nhập thành công!"))
                .andExpect(jsonPath("$.data.token").value(MOCK_TOKEN))
                .andExpect(jsonPath("$.data.email").value(USER_EMAIL))
                .andExpect(jsonPath("$.data.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.data.userId").value(1));

        verify(userService, times(1)).login(any(LoginRequest.class));
        verify(authService, never()).sendOtp(any());
    }

    /**
     * TC_AUTH_CTRL_008 — LOGIN: Sai mật khẩu → HTTP 200, success=false, không có token
     * ─────────────────────────────────────────────────────────────────
     * GIVEN: userService.login() trả về ApiResponse.error("Mật khẩu không đúng!")
     * WHEN : POST /api/auth/login với password sai
     * THEN : HTTP 200
     *        $.success = false
     *        $.message = "Mật khẩu không đúng!"
     *        $.data không tồn tại (null)
     */
    @Test
    @DisplayName("TC_AUTH_CTRL_008 — Login: Wrong password → HTTP 200, success=false, no token")
    void TC_AUTH_CTRL_008_login_wrongPassword() throws Exception {
        // GIVEN
        LoginRequest req = new LoginRequest();
        req.setEmail(USER_EMAIL);
        req.setPassword("SaiMatKhau!");

        when(userService.login(any(LoginRequest.class)))
                .thenReturn(ApiResponse.error("Mật khẩu không đúng!"));

        // WHEN & THEN
        mockMvc.perform(post(BASE + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Mật khẩu không đúng!"))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(userService, times(1)).login(any(LoginRequest.class));
    }

    /**
     * TC_AUTH_CTRL_009 — LOGIN: Email không tồn tại → HTTP 200, success=false
     * ─────────────────────────────────────────────────────────────────
     * GIVEN: userService.login() trả về ApiResponse.error("Email không tồn tại!")
     * WHEN : POST /api/auth/login với email chưa đăng ký
     * THEN : HTTP 200
     *        $.success = false
     *        $.message = "Email không tồn tại!"
     */
    @Test
    @DisplayName("TC_AUTH_CTRL_009 — Login: Email not found → HTTP 200, success=false")
    void TC_AUTH_CTRL_009_login_emailNotFound() throws Exception {
        // GIVEN
        LoginRequest req = new LoginRequest();
        req.setEmail("chuadangky@example.com");
        req.setPassword("AnyPassword@123");

        when(userService.login(any(LoginRequest.class)))
                .thenReturn(ApiResponse.error("Email không tồn tại!"));

        // WHEN & THEN
        mockMvc.perform(post(BASE + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Email không tồn tại!"));
    }

    /**
     * TC_AUTH_CTRL_010 — LOGIN: userService ném RuntimeException → GlobalExceptionHandler → HTTP 400
     * ─────────────────────────────────────────────────────────────────
     * GIVEN: userService.login() ném RuntimeException (DB timeout, lỗi không lường trước)
     * WHEN : POST /api/auth/login
     * THEN : Exception propagate ra ngoài controller
     *        GlobalExceptionHandler.handleRuntimeException() bắt → HTTP 400
     *        $.success = false; $.message = thông điệp lỗi
     */
    @Test
    @DisplayName("TC_AUTH_CTRL_010 — Login: RuntimeException → GlobalExceptionHandler → HTTP 400")
    void TC_AUTH_CTRL_010_login_runtimeException_returns400() throws Exception {
        // GIVEN
        LoginRequest req = new LoginRequest();
        req.setEmail(USER_EMAIL);
        req.setPassword("Pass@123");

        when(userService.login(any(LoginRequest.class)))
                .thenThrow(new RuntimeException("DB timeout: cannot connect to SQL Server"));

        // WHEN & THEN
        mockMvc.perform(post(BASE + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())                           // HTTP 400
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message")
                        .value("DB timeout: cannot connect to SQL Server"));

        verify(userService, times(1)).login(any(LoginRequest.class));
    }

    // ================================================================
    // ================================================================
    //  [SECURITY] GET /api/auth/me
    // ================================================================
    // ================================================================

    /**
     * TC_AUTH_CTRL_011 — SECURITY: GET /me với authentication hợp lệ → HTTP 200, success=true
     * ─────────────────────────────────────────────────────────────────
     * GIVEN: @WithMockUser(username="test@example.com") đặt Authentication trong SecurityContext
     *        userService.getCurrentUser(USER_EMAIL) trả về success với user data
     * WHEN : GET /api/auth/me
     * THEN : HTTP 200
     *        $.success = true
     *        $.message = "Lấy thông tin người dùng thành công"
     *        $.data.email = USER_EMAIL
     *        userService.getCurrentUser(USER_EMAIL) được gọi đúng 1 lần
     */
    @Test
    @WithMockUser(username = "test@example.com")
    @DisplayName("TC_AUTH_CTRL_011 — Security: GET /me với auth hợp lệ → HTTP 200, success=true")
    void TC_AUTH_CTRL_011_getMe_withValidAuthentication_success() throws Exception {
        // GIVEN
        when(userService.getCurrentUser(USER_EMAIL))
                .thenReturn(ApiResponse.success(
                        "Lấy thông tin người dùng thành công",
                        Map.of("id", 1L, "email", USER_EMAIL, "role", "CUSTOMER")
                ));

        // WHEN & THEN
        mockMvc.perform(get(BASE + "/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Lấy thông tin người dùng thành công"))
                .andExpect(jsonPath("$.data.email").value(USER_EMAIL));

        verify(userService, times(1)).getCurrentUser(USER_EMAIL);
    }

    /**
     * TC_AUTH_CTRL_012 — SECURITY: GET /me không có authentication (missing token) → HTTP 200, error
     * ─────────────────────────────────────────────────────────────────
     * Ghi chú kiến trúc:
     *   /api/auth/** là permitAll() → Spring Security KHÔNG trả 401.
     *   Controller tự kiểm tra: if (authentication == null) → trả ApiResponse.error("Chưa đăng nhập")
     *   → HTTP status là 200, không phải 401.
     *   Bảo vệ 401 thực sự phải được cấu hình ở SecurityConfig cho từng endpoint cụ thể.
     *
     * GIVEN: Không có @WithMockUser → SecurityContext rỗng → authentication = null
     *        Không có Authorization header
     * WHEN : GET /api/auth/me
     * THEN : HTTP 200  (permitAll → Spring Security không chặn)
     *        $.success = false
     *        $.message = "Chưa đăng nhập"
     *        userService.getCurrentUser() KHÔNG được gọi
     */
    @Test
    @DisplayName("TC_AUTH_CTRL_012 — Security: GET /me missing token → HTTP 200, 'Chưa đăng nhập' (permitAll behavior)")
    void TC_AUTH_CTRL_012_getMe_missingAuthentication_returnsNotLoggedInError() throws Exception {
        // WHEN & THEN (không có @WithMockUser → authentication là null)
        mockMvc.perform(get(BASE + "/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Chưa đăng nhập"));

        // Service không được gọi khi chưa xác thực
        verify(userService, never()).getCurrentUser(anyString());
    }

    // ================================================================
    // ================================================================
    //  [SECURITY] POST /api/auth/change-password
    // ================================================================
    // ================================================================

    /**
     * TC_AUTH_CTRL_013 — SECURITY: POST /change-password với auth hợp lệ → HTTP 200, success=true
     * ─────────────────────────────────────────────────────────────────
     * GIVEN: @WithMockUser(username="test@example.com") đặt Authentication
     *        userService.changePassword(USER_EMAIL, req) trả về success
     * WHEN : POST /api/auth/change-password với ChangePasswordRequest hợp lệ
     * THEN : HTTP 200
     *        $.success = true
     *        $.message = "Đổi mật khẩu thành công!"
     *        userService.changePassword(USER_EMAIL, req) được gọi với đúng email
     */
    @Test
    @WithMockUser(username = "test@example.com")
    @DisplayName("TC_AUTH_CTRL_013 — Security: POST /change-password với auth hợp lệ → HTTP 200, success=true")
    void TC_AUTH_CTRL_013_changePassword_withValidAuth_success() throws Exception {
        // GIVEN
        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setOldPassword("OldPass@123");
        req.setNewPassword("NewPass@456");
        req.setConfirmPassword("NewPass@456");

        when(userService.changePassword(eq(USER_EMAIL), any(ChangePasswordRequest.class)))
                .thenReturn(ApiResponse.success("Đổi mật khẩu thành công!"));

        // WHEN & THEN
        mockMvc.perform(post(BASE + "/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Đổi mật khẩu thành công!"));

        verify(userService, times(1))
                .changePassword(eq(USER_EMAIL), any(ChangePasswordRequest.class));
    }

    /**
     * TC_AUTH_CTRL_014 — SECURITY: POST /change-password không có auth → NullPointerException → HTTP 400
     * ─────────────────────────────────────────────────────────────────
     * Ghi chú kiến trúc (lỗ hổng trong controller):
     *   Controller gọi authentication.getName() mà KHÔNG kiểm tra null trước.
     *   Khi authentication = null → NullPointerException.
     *   NullPointerException extends RuntimeException → GlobalExceptionHandler bắt → HTTP 400.
     *   [Khuyến nghị fix]: Thêm null-check hoặc annotation @AuthenticationPrincipal.
     *
     * GIVEN: Không có @WithMockUser → authentication = null trong controller
     * WHEN : POST /api/auth/change-password
     * THEN : controller gọi authentication.getName() → NullPointerException
     *        GlobalExceptionHandler.handleRuntimeException() → HTTP 400
     *        userService.changePassword() KHÔNG được gọi (exception xảy ra trước)
     */
    @Test
    @DisplayName("TC_AUTH_CTRL_014 — Security: POST /change-password không có auth → NPE → HTTP 400 (bug: thiếu null-check)")
    void TC_AUTH_CTRL_014_changePassword_missingAuthentication_npeReturns400() throws Exception {
        // GIVEN
        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setOldPassword("OldPass@123");
        req.setNewPassword("NewPass@456");
        req.setConfirmPassword("NewPass@456");

        // WHEN & THEN — authentication = null → NPE → GlobalExceptionHandler → HTTP 400
        mockMvc.perform(post(BASE + "/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());                          // HTTP 400

        // changePassword KHÔNG được gọi — NPE xảy ra khi đọc authentication.getName()
        verify(userService, never()).changePassword(anyString(), any());
    }

    // ================================================================
    // ================================================================
    //  [ERROR] POST /api/auth/first-change-password
    // ================================================================
    // ================================================================

    /**
     * TC_AUTH_CTRL_015 — ERROR: POST /first-change-password → service ném RuntimeException → HTTP 400
     * ─────────────────────────────────────────────────────────────────
     * GIVEN: userService.firstChangePassword() ném RuntimeException("Không tìm thấy người dùng!")
     * WHEN : POST /api/auth/first-change-password
     * THEN : RuntimeException propagate ra khỏi controller
     *        GlobalExceptionHandler.handleRuntimeException() → HTTP 400
     *        $.success = false
     *        $.message = "Không tìm thấy người dùng!"
     *        userService.firstChangePassword() được gọi đúng 1 lần
     */
    @Test
    @DisplayName("TC_AUTH_CTRL_015 — Error: POST /first-change-password → RuntimeException → HTTP 400")
    void TC_AUTH_CTRL_015_firstChangePassword_runtimeException_returns400() throws Exception {
        // GIVEN
        FirstChangePasswordRequest req = new FirstChangePasswordRequest();
        req.setEmail(USER_EMAIL);
        req.setCurrentPassword("TempPass@123");
        req.setNewPassword("NewPass@456");
        req.setConfirmPassword("NewPass@456");

        when(userService.firstChangePassword(any(FirstChangePasswordRequest.class)))
                .thenThrow(new RuntimeException("Không tìm thấy người dùng!"));

        // WHEN & THEN
        mockMvc.perform(post(BASE + "/first-change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())                           // HTTP 400
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Không tìm thấy người dùng!"));

        verify(userService, times(1))
                .firstChangePassword(any(FirstChangePasswordRequest.class));
    }

    // ================================================================
    //  [REQUIREMENT GAP TESTS]
    //  Các case dưới đây bám theo nghiệp vụ chuẩn. Chúng có thể fail
    //  để chứng minh lệch chuẩn giữa spec và implementation hiện tại.
    // ================================================================

    @Test
        @DisplayName("TC_AUTH_CTRL_016 — Spec: GET /me thiếu auth phải trả 401 Unauthorized")
        void TC_AUTH_CTRL_016_getMe_missingAuthentication_shouldReturn401BySpec() throws Exception {
        mockMvc.perform(get(BASE + "/me"))
                .andExpect(status().isUnauthorized());

        verify(userService, never()).getCurrentUser(anyString());
    }

    @Test
        @DisplayName("TC_AUTH_CTRL_017 — Spec: POST /change-password thiếu auth phải trả 401 Unauthorized")
        void TC_AUTH_CTRL_017_changePassword_missingAuthentication_shouldReturn401BySpec() throws Exception {
        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setOldPassword("OldPass@123");
        req.setNewPassword("NewPass@456");
        req.setConfirmPassword("NewPass@456");

        mockMvc.perform(post(BASE + "/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());

        verify(userService, never()).changePassword(anyString(), any());
    }

    @Test
        @DisplayName("TC_AUTH_CTRL_018 — Spec: verify-otp gặp lỗi hệ thống phải nổi ra 400 để client biết xử lý")
        void TC_AUTH_CTRL_018_verifyOtp_runtimeException_shouldReturn400BySpec() throws Exception {
        OtpVerifyRequest req = new OtpVerifyRequest();
        req.setEmail(USER_EMAIL);
        req.setOtpCode("999999");

        when(authService.verifyOtpAndRegister(any(OtpVerifyRequest.class)))
                .thenThrow(new RuntimeException("User creation failed: constraint violation"));

        mockMvc.perform(post(BASE + "/register/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message")
                        .value("User creation failed: constraint violation"));
    }

    @Test
        @DisplayName("TC_AUTH_CTRL_019 — Spec: send-otp payload thiếu email phải bị chặn 400 ngay tại controller")
        void TC_AUTH_CTRL_019_sendOtp_missingEmail_shouldReturn400ByValidation() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setEmail(null);
        req.setFullName("Nguyễn Văn A");
        req.setPassword("ValidPass@123");
        req.setPhone("0911111111");

        when(authService.sendOtp(any(RegisterRequest.class)))
                .thenReturn(ApiResponse.success("Mã OTP đã được gửi đến email của bạn!"));

        mockMvc.perform(post(BASE + "/register/send-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Email không được để trống!"));

        verify(authService, never()).sendOtp(any(RegisterRequest.class));
    }

    @Test
        @DisplayName("TC_AUTH_CTRL_020 — Spec: login thiếu password phải bị chặn 400 thay vì đẩy xuống service")
        void TC_AUTH_CTRL_020_login_missingPassword_shouldReturn400ByValidation() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail(USER_EMAIL);
        req.setPassword(null);

        LoginResponse loginResp = new LoginResponse(
                MOCK_TOKEN, 1L, USER_EMAIL, "Nguyễn Văn A",
                "0911111111", "123 Đường Test", "CUSTOMER", null, "ACTIVE", null
        );
        when(userService.login(any(LoginRequest.class)))
                .thenReturn(ApiResponse.success("Đăng nhập thành công!", loginResp));

        mockMvc.perform(post(BASE + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Mật khẩu không được để trống!"));

        verify(userService, never()).login(any(LoginRequest.class));
    }

    @Test
        @DisplayName("TC_AUTH_CTRL_021 — Spec: first-change-password thiếu newPassword phải trả 400 validation")
        void TC_AUTH_CTRL_021_firstChangePassword_missingNewPassword_shouldReturn400() throws Exception {
        FirstChangePasswordRequest req = new FirstChangePasswordRequest();
        req.setEmail(USER_EMAIL);
        req.setCurrentPassword("TempPass@123");
        req.setNewPassword(null);
        req.setConfirmPassword("NewPass@456");

        when(userService.firstChangePassword(any(FirstChangePasswordRequest.class)))
                .thenReturn(ApiResponse.success("Đổi mật khẩu thành công!"));

        mockMvc.perform(post(BASE + "/first-change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Mật khẩu mới không được để trống!"));

        verify(userService, never()).firstChangePassword(any(FirstChangePasswordRequest.class));
    }
}
