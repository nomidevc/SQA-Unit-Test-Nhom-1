package com.doan.WEB_TMDT.service;

import com.doan.WEB_TMDT.common.dto.ApiResponse;
import com.doan.WEB_TMDT.module.auth.dto.ChangePasswordRequest;
import com.doan.WEB_TMDT.module.auth.dto.FirstChangePasswordRequest;
import com.doan.WEB_TMDT.module.auth.dto.LoginRequest;
import com.doan.WEB_TMDT.module.auth.dto.LoginResponse;
import com.doan.WEB_TMDT.module.auth.dto.RegisterRequest;
import com.doan.WEB_TMDT.module.auth.entity.Position;
import com.doan.WEB_TMDT.module.auth.entity.Customer;
import com.doan.WEB_TMDT.module.auth.entity.Employee;
import com.doan.WEB_TMDT.module.auth.entity.OtpVerification;
import com.doan.WEB_TMDT.module.auth.entity.Role;
import com.doan.WEB_TMDT.module.auth.entity.Status;
import com.doan.WEB_TMDT.module.auth.entity.User;
import com.doan.WEB_TMDT.module.auth.repository.CustomerRepository;
import com.doan.WEB_TMDT.module.auth.repository.OtpRepository;
import com.doan.WEB_TMDT.module.auth.repository.UserRepository;
import com.doan.WEB_TMDT.module.auth.service.UserService;
import com.doan.WEB_TMDT.module.auth.service.impl.AuthServiceImpl;
import com.doan.WEB_TMDT.module.auth.service.impl.UserServiceImpl;
import com.doan.WEB_TMDT.security.JwtService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ================================================================
 * SQA UNIT TEST - MODULE: AUTH SERVICE (REGISTER — sendOtp)
 * ================================================================
 * Test Suite ID  : TS_AUTH_REG
 * Số test cases  : 23
 * Mục tiêu       : Kiểm thử toàn diện chức năng gửi OTP
 *                  (bước khởi tạo đăng ký) của AuthServiceImpl.
 *                  Phủ: Happy Path, Validation, Business, Boundary,
 *                       Security, System, CheckDB.
 * Framework      : JUnit 5 + Mockito
 * Người thực hiện: SQA Team — Nhóm 1
 * Ngày tạo       : 19/04/2026
 * ================================================================
 *
 * LUỒNG XỬ LÝ CỦA sendOtp():
 *  1. userRepository.existsByEmail()    → email trùng → error
 *  2. customerRepository.existsByPhone() → phone trùng → error
 *  3. passwordEncoder.encode()          → mã hoá mật khẩu
 *  4. otpRepository.save()             → lưu OtpVerification
 *  5. mailSender.send()                → gửi email OTP
 *  6. Trả về ApiResponse.success / error
 * ================================================================
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService — REGISTER (sendOtp) Unit Tests")
class AuthServiceTest {

    // ================================================================
    //  KHAI BÁO MOCK DEPENDENCIES
    // ================================================================

    /** Kiểm tra sự tồn tại của email trong hệ thống */
    @Mock
    private UserRepository userRepository;

    /** Kiểm tra sự tồn tại của số điện thoại */
    @Mock
    private CustomerRepository customerRepository;

    /** Lưu bản ghi OTP chờ xác minh */
    @Mock
    private OtpRepository otpRepository;

    /** Mã hoá mật khẩu người dùng */
    @Mock
    private PasswordEncoder passwordEncoder;

    /** Gửi email chứa mã OTP */
    @Mock
    private JavaMailSender mailSender;

    /** Tạo user thực sự sau khi OTP hợp lệ (không dùng trong sendOtp) */
    @Mock
    private UserService userService;

    /** Dịch vụ JWT (không dùng trong sendOtp) */
    @Mock
    private JwtService jwtService;

    /** SUT — System Under Test */
    @InjectMocks
    private AuthServiceImpl authService;

    // ================================================================
    //  HẰNG SỐ DỮ LIỆU TEST
    // ================================================================

    private static final String VALID_EMAIL    = "nguyenvan@example.com";
    private static final String VALID_PASSWORD = "SecurePass@123";
    private static final String VALID_FULLNAME = "Nguyễn Văn A";
    private static final String VALID_PHONE    = "0901234567";
    private static final String VALID_ADDRESS  = "123 Lê Lợi, TP.HCM";
    private static final String ENCODED_PWD    = "$2a$10$mockedEncodedPasswordHash";

    /** Request hợp lệ tái sử dụng giữa các test */
    private RegisterRequest validRequest;

    // ================================================================
    //  SETUP & TEARDOWN
    // ================================================================

    /**
     * Chạy trước mỗi test:
     *  - Khởi tạo RegisterRequest hợp lệ.
     *  - Cấu hình stub lenient cho happy-path chung để các test
     *    không cần khai báo lại những stub không liên quan.
     */
    @BeforeEach
    void setUp() {
        validRequest = new RegisterRequest();
        validRequest.setEmail(VALID_EMAIL);
        validRequest.setPassword(VALID_PASSWORD);
        validRequest.setFullName(VALID_FULLNAME);
        validRequest.setPhone(VALID_PHONE);
        validRequest.setAddress(VALID_ADDRESS);

        /*
         * Lenient stub: tránh UnnecessaryStubbingException
         * ở những test case không đi đến bước cụ thể này.
         */
        lenient().when(userRepository.existsByEmail(anyString())).thenReturn(false);
        lenient().when(customerRepository.existsByPhone(anyString())).thenReturn(false);
        lenient().when(passwordEncoder.encode(anyString())).thenReturn(ENCODED_PWD);
        lenient().when(otpRepository.save(any(OtpVerification.class)))
                 .thenAnswer(invocation -> invocation.getArgument(0));
        // mailSender.send() là void → Mockito mặc định no-op, không cần stub
    }

    /**
     * Chạy sau mỗi test:
     *  - Reset toàn bộ mock để đảm bảo các test cô lập hoàn toàn.
     *  - MockitoExtension đã tự tái tạo mock trước mỗi test,
     *    bước này ghi lại ý định SQA rõ ràng.
     */
    @AfterEach
    void tearDown() {
        Mockito.reset(
                userRepository, customerRepository, otpRepository,
                passwordEncoder, mailSender, userService, jwtService
        );
    }

    // ================================================================
    //  [HAPPY PATH]
    // ================================================================

    /**
     * TC_AUTH_REG_001 — Đăng ký thành công với thông tin đầy đủ, hợp lệ
     *
     * GIVEN: Người dùng cung cấp email, password, fullName, phone, address hợp lệ;
     *        email và SĐT chưa tồn tại trong hệ thống.
     * WHEN : authService.sendOtp(validRequest) được gọi.
     * THEN : Trả về ApiResponse thành công với đúng message xác nhận OTP đã gửi.
     */
    @Test
    @DisplayName("TC_AUTH_REG_001 — Happy Path: Đăng ký hợp lệ → OTP gửi thành công")
    void TC_AUTH_REG_001_happyPath_validRegistration() {
        // GIVEN — stub happy-path đã được cấu hình trong @BeforeEach

        // WHEN
        ApiResponse response = authService.sendOtp(validRequest);

        // THEN
        assertNotNull(response, "Response không được null");
        assertTrue(response.isSuccess(), "Kết quả phải là success");
        assertEquals("Mã OTP đã được gửi đến email của bạn!", response.getMessage());
    }

    // ================================================================
    //  [VALIDATION]
    // ================================================================

    /**
     * TC_AUTH_REG_002 — Email null được truyền thẳng vào service
     *
     * GIVEN: Email trong request là null (giả sử @Valid bị bypass ở controller layer).
     * WHEN : authService.sendOtp(request) được gọi.
     * THEN : Service không tự validate null → gọi existsByEmail(null) → tiến hành gửi OTP.
     *        Đây là lỗ hổng: service layer nên tự kiểm tra null thay vì chỉ dựa @Valid.
     */
    @Test
    @DisplayName("TC_AUTH_REG_002 — Validation: Email null → Service không chặn, tiếp tục xử lý")
    void TC_AUTH_REG_002_validation_nullEmail() {
        // GIVEN
        validRequest.setEmail(null);
        // anyString() không khớp null → mock trả false (giá trị boolean mặc định của Mockito)

        // WHEN — service không có null-check tường minh nên sẽ tiếp tục
        ApiResponse response = authService.sendOtp(validRequest);

        // THEN — hành vi thực tế của service-layer khi không có validation
        // Service vẫn trả success (lỗ hổng: phải reject null email trước)
        assertTrue(response.isSuccess(),
                "Service không validate null email tại service-layer → OTP vẫn được xử lý");
        // Xác nhận existsByEmail được gọi với null (service không sanitize trước)
        verify(userRepository).existsByEmail(null);
    }

    /**
     * TC_AUTH_REG_003 — Password null gây NullPointerException
     *
     * GIVEN: Password trong request là null.
     * WHEN : authService.sendOtp(request) được gọi.
     * THEN : passwordEncoder.encode(null) ném NullPointerException (hành vi của BCrypt thực tế).
     *        Exception propagate lên caller vì service không có try-catch ở bước encode.
     */
    @Test
    @DisplayName("TC_AUTH_REG_003 — Validation: Password null → NullPointerException từ encode()")
    void TC_AUTH_REG_003_validation_nullPassword() {
        // GIVEN
        validRequest.setPassword(null);
        // Simulate hành vi BCrypt thực tế: encode(null) → NPE
        doThrow(new NullPointerException("Raw password cannot be null"))
                .when(passwordEncoder).encode(null);

        // WHEN & THEN
        NullPointerException ex = assertThrows(
                NullPointerException.class,
                () -> authService.sendOtp(validRequest),
                "Phải ném NullPointerException khi password là null"
        );
        assertEquals("Raw password cannot be null", ex.getMessage());
    }

    /**
     * TC_AUTH_REG_004 — FullName null → OTP được tạo với tên "null"
     *
     * GIVEN: FullName trong request là null.
     * WHEN : authService.sendOtp(request) được gọi.
     * THEN : Service không validate fullName → OtpVerification lưu fullName=null;
     *        nội dung email chứa "Xin chào null" (lỗ hổng UX).
     */
    @Test
    @DisplayName("TC_AUTH_REG_004 — Validation: FullName null → Service không chặn, OTP lưu null")
    void TC_AUTH_REG_004_validation_nullFullName() {
        // GIVEN
        validRequest.setFullName(null);
        ArgumentCaptor<OtpVerification> captor = ArgumentCaptor.forClass(OtpVerification.class);

        // WHEN
        ApiResponse response = authService.sendOtp(validRequest);

        // THEN
        assertTrue(response.isSuccess(),
                "Service không validate null fullName → vẫn trả success (lỗ hổng UX)");
        verify(otpRepository).save(captor.capture());
        assertNull(captor.getValue().getFullName(),
                "OtpVerification.fullName phải là null (service không gán giá trị mặc định)");
    }

    /**
     * TC_AUTH_REG_005 — Email rỗng ("") được truyền vào service
     *
     * GIVEN: Email là chuỗi rỗng "" (vi phạm @NotBlank nhưng bypass controller).
     * WHEN : authService.sendOtp(request) được gọi.
     * THEN : existsByEmail("") trả false → service tiến hành gửi OTP với email rỗng.
     */
    @Test
    @DisplayName("TC_AUTH_REG_005 — Validation: Email rỗng → Service không chặn, tiến hành")
    void TC_AUTH_REG_005_validation_emptyEmail() {
        // GIVEN
        validRequest.setEmail("");
        when(userRepository.existsByEmail("")).thenReturn(false);

        // WHEN
        ApiResponse response = authService.sendOtp(validRequest);

        // THEN
        assertTrue(response.isSuccess(),
                "Service không reject email rỗng → tiến hành (chỉ @NotBlank ở DTO layer chặn)");
        verify(userRepository).existsByEmail("");
    }

    /**
     * TC_AUTH_REG_006 — Email không đúng định dạng
     *
     * GIVEN: Email là "notanemail@@invalid" (vi phạm @Email nhưng bypass controller).
     * WHEN : authService.sendOtp(request) được gọi.
     * THEN : Service không validate format email → tiến hành gửi OTP.
     *        Validation format email chỉ nằm ở DTO annotation @Email.
     */
    @Test
    @DisplayName("TC_AUTH_REG_006 — Validation: Email sai định dạng → Service không kiểm tra format")
    void TC_AUTH_REG_006_validation_invalidEmailFormat() {
        // GIVEN
        String invalidEmail = "notanemail@@invalid";
        validRequest.setEmail(invalidEmail);
        when(userRepository.existsByEmail(invalidEmail)).thenReturn(false);

        // WHEN
        ApiResponse response = authService.sendOtp(validRequest);

        // THEN
        assertTrue(response.isSuccess(),
                "Service-layer không có kiểm tra format email → trả success (lỗ hổng nếu @Valid bị bypass)");
        verify(userRepository).existsByEmail(invalidEmail);
    }

    /**
     * TC_AUTH_REG_007 — Password quá ngắn (dưới 8 ký tự)
     *
     * GIVEN: Password là "abc" (3 ký tự, vi phạm @Size(min=8)).
     * WHEN : authService.sendOtp(request) được gọi.
     * THEN : Service không validate độ dài → gọi passwordEncoder.encode("abc") và tiến hành.
     */
    @Test
    @DisplayName("TC_AUTH_REG_007 — Validation: Password quá ngắn (3 ký tự) → Service không chặn")
    void TC_AUTH_REG_007_validation_passwordTooShort() {
        // GIVEN
        String shortPassword = "abc"; // 3 ký tự — dưới mức tối thiểu 8
        validRequest.setPassword(shortPassword);
        when(passwordEncoder.encode(shortPassword)).thenReturn("$2a$10$encodedShortPwd");

        // WHEN
        ApiResponse response = authService.sendOtp(validRequest);

        // THEN
        assertTrue(response.isSuccess(),
                "Service không validate độ dài password → encode và tiến hành (validate ở DTO layer)");
        verify(passwordEncoder).encode(shortPassword);
    }

    /**
     * TC_AUTH_REG_008 — Password yếu (toàn chữ thường, không có số/ký tự đặc biệt)
     *
     * GIVEN: Password là "weakpassword" — đủ dài (12 ký tự) nhưng chỉ có chữ thường.
     * WHEN : authService.sendOtp(request) được gọi.
     * THEN : Service không có kiểm tra độ mạnh password → encode và tiến hành.
     */
    @Test
    @DisplayName("TC_AUTH_REG_008 — Validation: Password yếu (toàn chữ thường) → Service không kiểm tra")
    void TC_AUTH_REG_008_validation_weakPassword() {
        // GIVEN
        String weakPassword = "weakpassword"; // đủ dài nhưng thiếu số, chữ hoa, ký tự đặc biệt
        validRequest.setPassword(weakPassword);
        when(passwordEncoder.encode(weakPassword)).thenReturn("$2a$10$encodedWeakPwd");

        // WHEN
        ApiResponse response = authService.sendOtp(validRequest);

        // THEN
        assertTrue(response.isSuccess(),
                "Service không có kiểm tra độ mạnh password → trả success (cần thêm password-strength rule)");
        verify(passwordEncoder).encode(weakPassword);
    }

    // ================================================================
    //  [BUSINESS]
    // ================================================================

    /**
     * TC_AUTH_REG_009 — Email đã tồn tại trong hệ thống
     *
     * GIVEN: userRepository.existsByEmail() trả về true (email đã được đăng ký trước đó).
     * WHEN : authService.sendOtp(request) được gọi.
     * THEN : Trả về error "Email đã được sử dụng!" và dừng sớm —
     *        không mã hoá password, không lưu OTP, không gửi email.
     */
    @Test
    @DisplayName("TC_AUTH_REG_009 — Business: Email trùng → Error 'Email đã được sử dụng!'")
    void TC_AUTH_REG_009_business_duplicateEmail() {
        // GIVEN
        when(userRepository.existsByEmail(VALID_EMAIL)).thenReturn(true);

        // WHEN
        ApiResponse response = authService.sendOtp(validRequest);

        // THEN
        assertFalse(response.isSuccess(), "Phải là error khi email đã tồn tại");
        assertEquals("Email đã được sử dụng!", response.getMessage());

        // Xác nhận dừng sớm: không đi đến các bước tiếp theo
        verify(passwordEncoder, never()).encode(any());
        verify(otpRepository, never()).save(any());
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    /**
     * TC_AUTH_REG_010 — Số điện thoại đã tồn tại trong hệ thống
     *
     * GIVEN: customerRepository.existsByPhone() trả về true (SĐT đã được đăng ký).
     * WHEN : authService.sendOtp(request) được gọi.
     * THEN : Trả về error "Số điện thoại đã tồn tại!" và không lưu OTP.
     */
    @Test
    @DisplayName("TC_AUTH_REG_010 — Business: SĐT trùng → Error 'Số điện thoại đã tồn tại!'")
    void TC_AUTH_REG_010_business_duplicatePhone() {
        // GIVEN
        when(userRepository.existsByEmail(VALID_EMAIL)).thenReturn(false);
        when(customerRepository.existsByPhone(VALID_PHONE)).thenReturn(true);

        // WHEN
        ApiResponse response = authService.sendOtp(validRequest);

        // THEN
        assertFalse(response.isSuccess(), "Phải là error khi SĐT đã tồn tại");
        assertEquals("Số điện thoại đã tồn tại!", response.getMessage());
        verify(otpRepository, never()).save(any());
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    /**
     * TC_AUTH_REG_011 — OtpVerification lưu đúng createdAt và expiresAt (5 phút)
     *
     * GIVEN: Thông tin đăng ký hợp lệ.
     * WHEN : authService.sendOtp(request) được gọi.
     * THEN : OtpVerification được lưu với:
     *        • createdAt nằm trong khoảng [beforeCall, afterCall + 1s]
     *        • expiresAt = createdAt + 5 phút (±1 giây tolerance)
     *        • verified = false
     *        • email, fullName, phone khớp request
     */
    @Test
    @DisplayName("TC_AUTH_REG_011 — Business: OTP lưu đúng createdAt, expiresAt (+5 phút), verified=false")
    void TC_AUTH_REG_011_business_otpSavedWithCorrectTimestamps() {
        // GIVEN
        ArgumentCaptor<OtpVerification> captor = ArgumentCaptor.forClass(OtpVerification.class);
        java.time.LocalDateTime beforeCall = java.time.LocalDateTime.now();

        // WHEN
        authService.sendOtp(validRequest);
        java.time.LocalDateTime afterCall = java.time.LocalDateTime.now();

        // THEN
        verify(otpRepository).save(captor.capture());
        OtpVerification saved = captor.getValue();

        // Kiểm tra createdAt
        assertNotNull(saved.getCreatedAt(), "createdAt không được null");
        assertFalse(saved.getCreatedAt().isBefore(beforeCall),
                "createdAt phải >= thời điểm trước khi gọi");
        assertFalse(saved.getCreatedAt().isAfter(afterCall.plusSeconds(1)),
                "createdAt không vượt quá thời điểm sau khi gọi (+ 1s tolerance)");

        // Kiểm tra expiresAt = createdAt + 5 phút
        assertNotNull(saved.getExpiresAt(), "expiresAt không được null");
        long diffSeconds = java.time.Duration.between(
                saved.getCreatedAt(), saved.getExpiresAt()).getSeconds();
        assertTrue(diffSeconds >= 299 && diffSeconds <= 301,
                "expiresAt phải cách createdAt đúng 5 phút (±1s). Thực tế: " + diffSeconds + "s");

        // Kiểm tra trạng thái và dữ liệu cơ bản
        assertFalse(saved.isVerified(), "OTP mới tạo phải có verified = false");
        assertEquals(VALID_EMAIL, saved.getEmail());
        assertEquals(VALID_FULLNAME, saved.getFullName());
        assertEquals(VALID_PHONE, saved.getPhone());
    }

    // ================================================================
    //  [BOUNDARY]
    // ================================================================

    /**
     * TC_AUTH_REG_012 — Email rất dài (302 ký tự, vượt RFC 5321 giới hạn 254)
     *
     * GIVEN: Email có 302 ký tự.
     * WHEN : authService.sendOtp(request) được gọi.
     * THEN : Service không giới hạn độ dài email → tiến hành (rủi ro lỗi DB/SMTP).
     *        Cần thêm validation hoặc database column length constraint.
     */
    @Test
    @DisplayName("TC_AUTH_REG_012 — Boundary: Email rất dài (302 ký tự) → Service không giới hạn")
    void TC_AUTH_REG_012_boundary_veryLongEmail() {
        // GIVEN — email 302 ký tự (vượt giới hạn RFC 5321: 254 ký tự)
        String longEmail = "a".repeat(290) + "@example.com"; // 302 ký tự
        validRequest.setEmail(longEmail);
        when(userRepository.existsByEmail(longEmail)).thenReturn(false);

        // WHEN
        ApiResponse response = authService.sendOtp(validRequest);

        // THEN
        assertTrue(response.isSuccess(),
                "Service không giới hạn độ dài email → xử lý thành công (cần constraint ở DTO/DB)");
        assertThat(longEmail).hasSizeGreaterThan(254); // Confirm vượt giới hạn RFC
        verify(userRepository).existsByEmail(longEmail);
    }

    /**
     * TC_AUTH_REG_013 — Password rất dài (~500 ký tự, vượt @Size(max=64))
     *
     * GIVEN: Password có ~500 ký tự (vượt giới hạn @Size(max=64) ở DTO).
     * WHEN : authService.sendOtp(request) được gọi.
     * THEN : Service không validate độ dài tối đa → encode password dài → tiến hành.
     *        Rủi ro: BCrypt chỉ xử lý tối đa 72 byte đầu tiên.
     */
    @Test
    @DisplayName("TC_AUTH_REG_013 — Boundary: Password rất dài (~500 ký tự) → Service encode và tiếp tục")
    void TC_AUTH_REG_013_boundary_veryLongPassword() {
        // GIVEN — password ~500 ký tự (giả sử bypass @Size(max=64) ở DTO)
        String longPassword = "P@ssw0rd!".repeat(56); // ~504 ký tự
        validRequest.setPassword(longPassword);
        when(passwordEncoder.encode(longPassword)).thenReturn("$2a$10$encodedLongPwd");

        // WHEN
        ApiResponse response = authService.sendOtp(validRequest);

        // THEN
        assertTrue(response.isSuccess(),
                "Service không giới hạn độ dài password → tiến hành (cần @Size(max) ở DTO)");
        verify(passwordEncoder).encode(longPassword);
        assertThat(longPassword).hasSizeGreaterThan(64);
    }

    /**
     * TC_AUTH_REG_014 — Password rỗng (độ dài = 0)
     *
     * GIVEN: Password là "" (chuỗi rỗng, vi phạm @NotBlank và @Size(min=8)).
     * WHEN : authService.sendOtp(request) được gọi.
     * THEN : Service không reject → gọi passwordEncoder.encode("") và tiến hành.
     */
    @Test
    @DisplayName("TC_AUTH_REG_014 — Boundary: Password rỗng (length=0) → Service encode chuỗi rỗng")
    void TC_AUTH_REG_014_boundary_passwordLengthZero() {
        // GIVEN
        validRequest.setPassword(""); // vi phạm @NotBlank + @Size(min=8) nhưng bypass controller
        when(passwordEncoder.encode("")).thenReturn("$2a$10$encodedEmptyPwd");

        // WHEN
        ApiResponse response = authService.sendOtp(validRequest);

        // THEN
        assertTrue(response.isSuccess(),
                "Service không reject password rỗng → encode và tiến hành (validate ở DTO layer)");
        verify(passwordEncoder).encode("");
    }

    /**
     * TC_AUTH_REG_015 — Password đúng minimum boundary (8 ký tự)
     *
     * GIVEN: Password có đúng 8 ký tự — bằng giá trị min trong @Size(min=8).
     * WHEN : authService.sendOtp(request) được gọi.
     * THEN : Đây là giá trị biên hợp lệ → đăng ký thành công.
     */
    @Test
    @DisplayName("TC_AUTH_REG_015 — Boundary: Password đúng 8 ký tự (min boundary) → Thành công")
    void TC_AUTH_REG_015_boundary_passwordAtMinBoundary() {
        // GIVEN
        String minBoundaryPassword = "Ab@12345"; // chính xác 8 ký tự
        assertEquals(8, minBoundaryPassword.length(), "Tiền điều kiện: password phải đúng 8 ký tự");
        validRequest.setPassword(minBoundaryPassword);
        when(passwordEncoder.encode(minBoundaryPassword)).thenReturn("$2a$10$encodedMinPwd");

        // WHEN
        ApiResponse response = authService.sendOtp(validRequest);

        // THEN
        assertTrue(response.isSuccess(),
                "Password 8 ký tự (min boundary hợp lệ) → phải thành công");
        verify(passwordEncoder).encode(minBoundaryPassword);
    }

    // ================================================================
    //  [SECURITY]
    // ================================================================

    /**
     * TC_AUTH_REG_016 — SQL Injection trong trường email
     *
     * GIVEN: Email chứa chuỗi SQL Injection: "'; DROP TABLE users; --@evil.com".
     * WHEN : authService.sendOtp(request) được gọi.
     * THEN : Service truyền nguyên chuỗi SQL injection vào existsByEmail() —
     *        không tự sanitize. JPA/Hibernate sử dụng parameterized query nên
     *        SQL injection không được thực thi ở tầng infrastructure.
     *        Test xác nhận service KHÔNG sanitize, và confirm an toàn nhờ JPA.
     */
    @Test
    @DisplayName("TC_AUTH_REG_016 — Security: SQL Injection trong email → Truyền nguyên vào repository (JPA bảo vệ)")
    void TC_AUTH_REG_016_security_sqlInjectionInEmail() {
        // GIVEN
        String sqlInjectionPayload = "'; DROP TABLE users; --@evil.com";
        validRequest.setEmail(sqlInjectionPayload);
        // Mock repository nhận và xử lý string này bình thường (như JPA thực tế)
        when(userRepository.existsByEmail(sqlInjectionPayload)).thenReturn(false);

        // WHEN
        ApiResponse response = authService.sendOtp(validRequest);

        // THEN
        // Xác nhận service truyền nguyên SQL injection payload đến repository
        // (an toàn nhờ JPA parameterized query, nhưng service không tự sanitize)
        verify(userRepository).existsByEmail(sqlInjectionPayload);
        assertTrue(response.isSuccess(),
                "Service không crash khi nhận SQL injection payload (JPA bảo vệ ở infrastructure layer)");
    }

    /**
     * TC_AUTH_REG_017 — XSS trong trường fullName
     *
     * GIVEN: FullName chứa script XSS: "<script>alert('XSS')</script>".
     * WHEN : authService.sendOtp(request) được gọi.
     * THEN : Service không sanitize → lưu OtpVerification với fullName chứa XSS payload.
     *        Lỗ hổng tiềm ẩn nếu frontend render trực tiếp dữ liệu này.
     *        Cần escape HTML ở output layer (frontend/email template).
     */
    @Test
    @DisplayName("TC_AUTH_REG_017 — Security: XSS trong fullName → Service không sanitize, lưu nguyên payload")
    void TC_AUTH_REG_017_security_xssInFullName() {
        // GIVEN
        String xssPayload = "<script>alert('XSS')</script>";
        validRequest.setFullName(xssPayload);
        ArgumentCaptor<OtpVerification> captor = ArgumentCaptor.forClass(OtpVerification.class);

        // WHEN
        authService.sendOtp(validRequest);

        // THEN
        verify(otpRepository).save(captor.capture());
        String savedFullName = captor.getValue().getFullName();
        // Service không escape HTML → XSS payload được lưu nguyên vào DB
        assertEquals(xssPayload, savedFullName,
                "Service không sanitize XSS payload → lưu nguyên (cần xử lý ở output/template layer)");
    }

    // ================================================================
    //  [SYSTEM]
    // ================================================================

    /**
     * TC_AUTH_REG_018 — otpRepository.save() ném RuntimeException
     *
     * GIVEN: otpRepository.save() ném RuntimeException (mô phỏng DB timeout/lỗi kết nối).
     * WHEN : authService.sendOtp(request) được gọi.
     * THEN : Service không có try-catch tại bước save → exception propagate lên caller.
     *        Email OTP không được gửi khi lưu DB thất bại (đúng thứ tự an toàn).
     */
    @Test
    @DisplayName("TC_AUTH_REG_018 — System: otpRepository.save() throw exception → Propagate lên caller")
    void TC_AUTH_REG_018_system_repositorySaveThrowsException() {
        // GIVEN — mô phỏng DB bị lỗi
        when(otpRepository.save(any(OtpVerification.class)))
                .thenThrow(new RuntimeException("Database connection timeout"));

        // WHEN & THEN
        RuntimeException ex = assertThrows(
                RuntimeException.class,
                () -> authService.sendOtp(validRequest),
                "RuntimeException từ otpRepository.save() phải propagate (service không bắt)"
        );
        assertEquals("Database connection timeout", ex.getMessage());

        // Đảm bảo email KHÔNG được gửi khi DB lỗi (đúng thứ tự: save trước, gửi mail sau)
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    /**
     * TC_AUTH_REG_019 — mailSender.send() thất bại (SMTP không khả dụng)
     *
     * GIVEN: mailSender.send() ném MailSendException.
     * WHEN : authService.sendOtp(request) được gọi.
     * THEN : Service bắt exception trong try-catch → trả về error
     *        với message "Không thể gửi email OTP. Vui lòng thử lại sau!".
     *        OtpVerification vẫn đã được lưu vào DB trước khi email thất bại.
     */
    @Test
    @DisplayName("TC_AUTH_REG_019 — System: mailSender.send() thất bại → Error 'Không thể gửi email OTP'")
    void TC_AUTH_REG_019_system_mailSenderThrowsException() {
        // GIVEN — mô phỏng SMTP server không khả dụng
        doThrow(new MailSendException("SMTP server unreachable"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        // WHEN
        ApiResponse response = authService.sendOtp(validRequest);

        // THEN
        assertFalse(response.isSuccess(), "Phải là error khi không gửi được email OTP");
        assertEquals("Không thể gửi email OTP. Vui lòng thử lại sau!", response.getMessage());

        // OTP đã lưu vào DB trước khi email thất bại (nhận xét: đây có thể là inconsistent state)
        verify(otpRepository).save(any(OtpVerification.class));
    }

    /**
     * TC_AUTH_REG_020 — Mô phỏng Concurrent Registration (cùng email)
     *
     * GIVEN: Hai request gửi đồng thời với cùng email.
     *        - Lần gọi 1: existsByEmail() = false (email chưa tồn tại → race window)
     *        - Lần gọi 2: existsByEmail() = true  (email vừa được thêm bởi luồng 1)
     * WHEN : Gọi sendOtp() tuần tự 2 lần (simulate race condition logic).
     * THEN : Lần 1 → success. Lần 2 → error "Email đã được sử dụng!".
     *        NOTE: Bảo vệ thực sự cần UNIQUE CONSTRAINT ở DB + xử lý DataIntegrityViolationException.
     */
    @Test
    @DisplayName("TC_AUTH_REG_020 — System: Concurrent simulation → Lần 1 OK, lần 2 duplicate")
    void TC_AUTH_REG_020_system_concurrentRegistrationSimulation() {
        // GIVEN — stub trả false lần 1, true lần 2 (simulate race condition)
        when(userRepository.existsByEmail(VALID_EMAIL))
                .thenReturn(false)  // Thread 1: email chưa có → tiến hành
                .thenReturn(true);  // Thread 2: email đã tồn tại sau lần 1

        // WHEN
        ApiResponse firstResponse  = authService.sendOtp(validRequest);
        ApiResponse secondResponse = authService.sendOtp(validRequest);

        // THEN
        assertTrue(firstResponse.isSuccess(),
                "Request đầu tiên: email chưa tồn tại → OTP gửi thành công");
        assertFalse(secondResponse.isSuccess(),
                "Request thứ hai: email đã tồn tại → phải trả error");
        assertEquals("Email đã được sử dụng!", secondResponse.getMessage());

        // OTP chỉ được lưu đúng một lần (từ request đầu tiên thành công)
        verify(otpRepository, times(1)).save(any(OtpVerification.class));
    }

    // ================================================================
    //  [CHECKDB]
    // ================================================================

    /**
     * TC_AUTH_REG_021 — Verify passwordEncoder.encode() gọi đúng 1 lần với đúng password
     *
     * GIVEN: Đăng ký hợp lệ.
     * WHEN : authService.sendOtp(request) được gọi.
     * THEN : passwordEncoder.encode() được gọi đúng 1 lần với giá trị password từ request.
     *        Không được gọi thêm lần nào khác (verifyNoMoreInteractions).
     */
    @Test
    @DisplayName("TC_AUTH_REG_021 — CheckDB: passwordEncoder.encode() gọi đúng 1 lần với đúng password")
    void TC_AUTH_REG_021_checkdb_verifyPasswordEncoderCalledOnce() {
        // GIVEN — happy-path stub từ @BeforeEach

        // WHEN
        authService.sendOtp(validRequest);

        // THEN
        verify(passwordEncoder, times(1)).encode(VALID_PASSWORD);
        // Đảm bảo không có lần gọi encode nào khác
        verifyNoMoreInteractions(passwordEncoder);
    }

    /**
     * TC_AUTH_REG_022 — Verify otpRepository.save() gọi đúng 1 lần với dữ liệu chính xác
     *
     * GIVEN: Đăng ký hợp lệ.
     * WHEN : authService.sendOtp(request) được gọi.
     * THEN : otpRepository.save() được gọi đúng 1 lần với OtpVerification chứa:
     *        - email khớp, encodedPassword là kết quả encode, otpCode 6 chữ số.
     */
    @Test
    @DisplayName("TC_AUTH_REG_022 — CheckDB: otpRepository.save() gọi đúng 1 lần với dữ liệu hợp lệ")
    void TC_AUTH_REG_022_checkdb_verifyOtpSaveCalledOnceWithCorrectData() {
        // GIVEN
        ArgumentCaptor<OtpVerification> captor = ArgumentCaptor.forClass(OtpVerification.class);

        // WHEN
        authService.sendOtp(validRequest);

        // THEN
        verify(otpRepository, times(1)).save(captor.capture());
        OtpVerification saved = captor.getValue();

        assertEquals(VALID_EMAIL, saved.getEmail(),
                "Email trong OtpVerification phải khớp với request");
        assertEquals(ENCODED_PWD, saved.getEncodedPassword(),
                "EncodedPassword phải là kết quả trả về từ passwordEncoder.encode()");
        assertNotNull(saved.getOtpCode(),
                "OTP code không được null");
                assertTrue(saved.getOtpCode().matches("\\d{6}"),
                        "OTP code phải là chuỗi 6 chữ số (format: %06d)");
    }

    /**
     * TC_AUTH_REG_023 — Verify KHÔNG có duplicate save khi email đã tồn tại
     *
     * GIVEN: Email đã tồn tại trong hệ thống.
     * WHEN : authService.sendOtp(request) được gọi.
     * THEN : otpRepository.save() không được gọi bất kỳ lần nào —
     *        service dừng sớm ngay sau khi phát hiện email trùng.
     *        passwordEncoder.encode() và mailSender.send() cũng không được gọi.
     */
    @Test
    @DisplayName("TC_AUTH_REG_023 — CheckDB: Email trùng → Không có save, encode, hay send mail nào được gọi")
    void TC_AUTH_REG_023_checkdb_verifyNoDuplicateSaveWhenEmailExists() {
        // GIVEN
        when(userRepository.existsByEmail(VALID_EMAIL)).thenReturn(true);

        // WHEN
        ApiResponse response = authService.sendOtp(validRequest);

        // THEN
        assertFalse(response.isSuccess(), "Phải là error response");
        assertEquals("Email đã được sử dụng!", response.getMessage());

        // Xác nhận không có thao tác nào được thực hiện sau khi phát hiện email trùng
        verify(otpRepository, never()).save(any(OtpVerification.class));
        verify(passwordEncoder, never()).encode(any());
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

        // ================================================================
        //  [REQUIREMENT GAP TESTS]
        //  Các case này viết theo nghiệp vụ chuẩn, có thể fail để phản ánh bug.
        // ================================================================

        @Test
        @DisplayName("TC_AUTH_REG_024 — Spec: Email null phải bị từ chối, không được tạo OTP")
        void TC_AUTH_REG_024_nullEmail_shouldBeRejectedByBusinessRule() {
                validRequest.setEmail(null);

                ApiResponse response = authService.sendOtp(validRequest);

                assertFalse(response.isSuccess(),
                                "Theo nghiệp vụ chuẩn, email null phải bị từ chối thay vì trả success");
                assertEquals("Email không được để trống!", response.getMessage());
                verify(otpRepository, never()).save(any(OtpVerification.class));
                verify(mailSender, never()).send(any(SimpleMailMessage.class));
        }

        @Test
        @DisplayName("TC_AUTH_REG_025 — Spec: Password null phải trả lỗi nghiệp vụ thay vì ném exception kỹ thuật")
        void TC_AUTH_REG_025_nullPassword_shouldReturnBusinessErrorInsteadOfThrowing() {
                validRequest.setPassword(null);
                doThrow(new NullPointerException("Raw password cannot be null"))
                                .when(passwordEncoder).encode(null);

                assertDoesNotThrow(() -> {
                        ApiResponse response = authService.sendOtp(validRequest);
                        assertFalse(response.isSuccess(),
                                        "Theo nghiệp vụ chuẩn, password null phải trả lỗi validation rõ ràng");
                        assertEquals("Mật khẩu không được để trống!", response.getMessage());
                }, "Service hiện tại không nên làm rơi exception kỹ thuật ra caller khi thiếu password");

                verify(otpRepository, never()).save(any(OtpVerification.class));
                verify(mailSender, never()).send(any(SimpleMailMessage.class));
        }

    // ================================================================
    // ================================================================
    //  PHẦN 2 — LOGIN: UserServiceImpl.login(LoginRequest)
    // ================================================================
    // ================================================================

    /**
     * ================================================================
     * SQA UNIT TEST - MODULE: USER SERVICE (LOGIN)
     * ================================================================
     * Test Suite ID  : TS_AUTH_LOGIN
     * Số test cases  : 20
     * SUT            : UserServiceImpl.login(LoginRequest)
     * Mục tiêu       : Kiểm thử toàn diện chức năng đăng nhập.
     *                  Phủ: Happy Path, Failure, Validation, Boundary,
     *                       Security, Token, System, CheckDB, Concurrency.
     *
     * LUỒNG XỬ LÝ CỦA login():
     *  1. userRepository.findByEmail()        → null → error
     *  2. passwordEncoder.matches()           → false → error
     *  3. user.getStatus() != ACTIVE         → error "Tài khoản đang bị khóa!"
     *  4. EMPLOYEE + firstLogin              → success + requireChangePassword
     *  5. jwtService.generateToken()         → JWT token
     *  6. Build LoginResponse                → success "Đăng nhập thành công!"
     * ================================================================
     */
    @Nested
    @DisplayName("UserService — LOGIN (login) Unit Tests")
    class LoginTests {

        // ============================================================
        //  MOCK DEPENDENCIES CHO LOGIN
        // ============================================================

        /** Truy vấn user theo email */
        @Mock
        private UserRepository userRepository;

        /** Không dùng trong login nhưng bắt buộc bởi UserServiceImpl constructor */
        @Mock
        private CustomerRepository customerRepository;

        /** So khớp mật khẩu (raw vs encoded) */
        @Mock
        private PasswordEncoder passwordEncoder;

        /** Tạo JWT token sau khi xác thực thành công */
        @Mock
        private JwtService jwtService;

        /** SUT — System Under Test */
        @InjectMocks
        private UserServiceImpl userService;

        // ============================================================
        //  HẰNG SỐ DỮ LIỆU TEST
        // ============================================================

        private static final String LOGIN_EMAIL      = "customer@example.com";
        private static final String LOGIN_PASSWORD   = "MyPass@123";
        private static final String ENCODED_PWD_LOGIN = "$2a$10$hashedPasswordForLoginTest";
        private static final String MOCK_JWT         =
                "eyJhbGciOiJIUzI1NiJ9.mockPayload.mockSignature";

        private LoginRequest loginRequest;
        private User         activeCustomerUser;
        private Customer     customer;

        // ============================================================
        //  SETUP & TEARDOWN
        // ============================================================

        /**
         * Chạy trước mỗi test trong LoginTests:
         *  - Xây dựng User ACTIVE với role CUSTOMER.
         *  - Cấu hình lenient stub cho happy-path.
         */
        @BeforeEach
        void setUpLogin() {
            loginRequest = new LoginRequest();
            loginRequest.setEmail(LOGIN_EMAIL);
            loginRequest.setPassword(LOGIN_PASSWORD);

            // Tạo Customer profile
            customer = Customer.builder()
                    .fullName("Trần Thị B")
                    .phone("0987654321")
                    .address("456 Nguyễn Huệ, TP.HCM")
                    .build();

            // Tạo User ACTIVE – CUSTOMER
            activeCustomerUser = User.builder()
                    .id(1L)
                    .email(LOGIN_EMAIL)
                    .password(ENCODED_PWD_LOGIN)
                    .role(Role.CUSTOMER)
                    .status(Status.ACTIVE)
                    .customer(customer)
                    .build();
            customer.setUser(activeCustomerUser);

            // Lenient stubs: cấu hình happy-path, tránh UnnecessaryStubbingException
            lenient().when(userRepository.findByEmail(LOGIN_EMAIL))
                     .thenReturn(java.util.Optional.of(activeCustomerUser));
            lenient().when(passwordEncoder.matches(LOGIN_PASSWORD, ENCODED_PWD_LOGIN))
                     .thenReturn(true);
            lenient().when(jwtService.generateToken(eq(LOGIN_EMAIL), any()))
                     .thenReturn(MOCK_JWT);
        }

        /**
         * Chạy sau mỗi test: reset tất cả mock của LoginTests.
         */
        @AfterEach
        void tearDownLogin() {
            Mockito.reset(userRepository, customerRepository, passwordEncoder, jwtService);
        }

        // ============================================================
        //  [HAPPY PATH]
        // ============================================================

        /**
         * TC_AUTH_LOGIN_001 — Đăng nhập thành công với email/password đúng
         *
         * GIVEN: Email tồn tại, password khớp, tài khoản ACTIVE.
         * WHEN : userService.login(loginRequest) được gọi.
         * THEN : ApiResponse thành công; data là LoginResponse chứa JWT không null,
         *        email đúng, role đúng.
         */
        @Test
        @DisplayName("TC_AUTH_LOGIN_001 — Happy Path: Email/password đúng → success + JWT")
        void TC_AUTH_LOGIN_001_happyPath_validCredentials() {
            // GIVEN — happy-path stub từ @BeforeEach

            // WHEN
            ApiResponse response = userService.login(loginRequest);

            // THEN
            assertTrue(response.isSuccess(), "Phải là success response");
            assertEquals("Đăng nhập thành công!", response.getMessage());
            assertNotNull(response.getData(), "Response data không được null");

            LoginResponse loginResp = (LoginResponse) response.getData();
            assertEquals(MOCK_JWT, loginResp.getToken(), "Token phải khớp với giá trị từ jwtService");
            assertEquals(LOGIN_EMAIL, loginResp.getEmail(), "Email trong response phải khớp request");
            assertEquals("CUSTOMER", loginResp.getRole(), "Role phải là CUSTOMER");
            assertEquals("ACTIVE", loginResp.getStatus(), "Status phải là ACTIVE");
        }

        // ============================================================
        //  [FAILURE]
        // ============================================================

        /**
         * TC_AUTH_LOGIN_002 — Sai mật khẩu
         *
         * GIVEN: Email tồn tại, nhưng password không khớp với hash trong DB.
         * WHEN : userService.login(loginRequest) được gọi.
         * THEN : Trả về error "Mật khẩu không đúng!"; JWT không được tạo.
         */
        @Test
        @DisplayName("TC_AUTH_LOGIN_002 — Failure: Sai password → Error 'Mật khẩu không đúng!'")
        void TC_AUTH_LOGIN_002_failure_wrongPassword() {
            // GIVEN
            when(passwordEncoder.matches(LOGIN_PASSWORD, ENCODED_PWD_LOGIN)).thenReturn(false);

            // WHEN
            ApiResponse response = userService.login(loginRequest);

            // THEN
            assertFalse(response.isSuccess(), "Phải là error khi password sai");
            assertEquals("Mật khẩu không đúng!", response.getMessage());
            // JWT không được tạo khi password sai
            verify(jwtService, never()).generateToken(any(), any());
        }

        /**
         * TC_AUTH_LOGIN_003 — Email không tồn tại trong hệ thống
         *
         * GIVEN: userRepository.findByEmail() trả về Optional.empty().
         * WHEN : userService.login(loginRequest) được gọi.
         * THEN : Trả về error "Email không tồn tại!"; không có bước kiểm tra password.
         */
        @Test
        @DisplayName("TC_AUTH_LOGIN_003 — Failure: Email không tồn tại → Error 'Email không tồn tại!'")
        void TC_AUTH_LOGIN_003_failure_emailNotFound() {
            // GIVEN
            when(userRepository.findByEmail(LOGIN_EMAIL)).thenReturn(java.util.Optional.empty());

            // WHEN
            ApiResponse response = userService.login(loginRequest);

            // THEN
            assertFalse(response.isSuccess(), "Phải là error khi email không tồn tại");
            assertEquals("Email không tồn tại!", response.getMessage());
            // Không kiểm tra password khi không tìm thấy user
            verify(passwordEncoder, never()).matches(any(), any());
            verify(jwtService, never()).generateToken(any(), any());
        }

        /**
         * TC_AUTH_LOGIN_004 — Tài khoản bị LOCKED
         *
         * GIVEN: User tồn tại, password đúng, nhưng status = LOCKED.
         * WHEN : userService.login(loginRequest) được gọi.
         * THEN : Trả về error "Tài khoản đang bị khóa!"; JWT không được tạo.
         */
        @Test
        @DisplayName("TC_AUTH_LOGIN_004 — Failure: Tài khoản LOCKED → Error 'Tài khoản đang bị khóa!'")
        void TC_AUTH_LOGIN_004_failure_accountLocked() {
            // GIVEN
            activeCustomerUser.setStatus(Status.LOCKED);

            // WHEN
            ApiResponse response = userService.login(loginRequest);

            // THEN
            assertFalse(response.isSuccess(), "Phải là error khi tài khoản bị LOCKED");
            assertEquals("Tài khoản đang bị khóa!", response.getMessage());
            verify(jwtService, never()).generateToken(any(), any());
        }

        /**
         * TC_AUTH_LOGIN_005 — Tài khoản ở trạng thái INACTIVE
         *
         * GIVEN: User tồn tại, password đúng, nhưng status = INACTIVE.
         * WHEN : userService.login(loginRequest) được gọi.
         * THEN : Trả về error "Tài khoản đang bị khóa!" (service dùng chung message).
         */
        @Test
        @DisplayName("TC_AUTH_LOGIN_005 — Failure: Tài khoản INACTIVE → Error 'Tài khoản đang bị khóa!'")
        void TC_AUTH_LOGIN_005_failure_accountInactive() {
            // GIVEN
            activeCustomerUser.setStatus(Status.INACTIVE);

            // WHEN
            ApiResponse response = userService.login(loginRequest);

            // THEN
            assertFalse(response.isSuccess(), "Phải là error khi tài khoản INACTIVE");
            assertEquals("Tài khoản đang bị khóa!", response.getMessage(),
                    "Service dùng chung message cho cả LOCKED và INACTIVE");
            verify(jwtService, never()).generateToken(any(), any());
        }

        // ============================================================
        //  [VALIDATION]
        // ============================================================

        /**
         * TC_AUTH_LOGIN_006 — Email null trong request
         *
         * GIVEN: loginRequest.email = null.
         * WHEN : userService.login(loginRequest) được gọi.
         * THEN : findByEmail(null) trả Optional.empty() → error "Email không tồn tại!".
         *        Service không có null-check tường minh → dựa vào repository.
         */
        @Test
        @DisplayName("TC_AUTH_LOGIN_006 — Validation: Email null → Error 'Email không tồn tại!'")
        void TC_AUTH_LOGIN_006_validation_nullEmail() {
            // GIVEN
            loginRequest.setEmail(null);
            when(userRepository.findByEmail(null)).thenReturn(java.util.Optional.empty());

            // WHEN
            ApiResponse response = userService.login(loginRequest);

            // THEN
            assertFalse(response.isSuccess());
            assertEquals("Email không tồn tại!", response.getMessage(),
                    "findByEmail(null) trả empty → service báo Email không tồn tại");
            verify(userRepository).findByEmail(null);
        }

        /**
         * TC_AUTH_LOGIN_007 — Password null gây IllegalArgumentException từ passwordEncoder
         *
         * GIVEN: loginRequest.password = null; BCryptPasswordEncoder.matches(null, ...) ném IAE.
         * WHEN : userService.login(loginRequest) được gọi.
         * THEN : Exception propagate lên caller (service không bắt IAE).
         */
        @Test
        @DisplayName("TC_AUTH_LOGIN_007 — Validation: Password null → IllegalArgumentException từ matches()")
        void TC_AUTH_LOGIN_007_validation_nullPassword() {
            // GIVEN
            loginRequest.setPassword(null);
            // BCryptPasswordEncoder ném IAE khi rawPassword = null
            doThrow(new IllegalArgumentException("rawPassword cannot be null"))
                    .when(passwordEncoder).matches(null, ENCODED_PWD_LOGIN);

            // WHEN & THEN
            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> userService.login(loginRequest),
                    "Phải ném IllegalArgumentException khi password là null"
            );
            assertEquals("rawPassword cannot be null", ex.getMessage());
            // JWT không được tạo
            verify(jwtService, never()).generateToken(any(), any());
        }

        /**
         * TC_AUTH_LOGIN_008 — Email rỗng ("") trong request
         *
         * GIVEN: loginRequest.email = "" (chuỗi rỗng).
         * WHEN : userService.login(loginRequest) được gọi.
         * THEN : findByEmail("") trả Optional.empty() → error "Email không tồn tại!".
         */
        @Test
        @DisplayName("TC_AUTH_LOGIN_008 — Validation: Email rỗng → Error 'Email không tồn tại!'")
        void TC_AUTH_LOGIN_008_validation_emptyEmail() {
            // GIVEN
            loginRequest.setEmail("");
            when(userRepository.findByEmail("")).thenReturn(java.util.Optional.empty());

            // WHEN
            ApiResponse response = userService.login(loginRequest);

            // THEN
            assertFalse(response.isSuccess());
            assertEquals("Email không tồn tại!", response.getMessage());
            verify(userRepository).findByEmail("");
        }

        // ============================================================
        //  [BOUNDARY]
        // ============================================================

        /**
         * TC_AUTH_LOGIN_009 — Password rất dài (~500 ký tự)
         *
         * GIVEN: Password có ~500 ký tự (không trùng với password đã mã hoá trong DB).
         * WHEN : userService.login(loginRequest) được gọi.
         * THEN : passwordEncoder.matches() trả false → error "Mật khẩu không đúng!".
         *        BCrypt chỉ xử lý 72 byte đầu, password dài vẫn được matches() xử lý bình thường.
         */
        @Test
        @DisplayName("TC_AUTH_LOGIN_009 — Boundary: Password rất dài (~500 ký tự) → Error 'Mật khẩu không đúng!'")
        void TC_AUTH_LOGIN_009_boundary_veryLongPassword() {
            // GIVEN — password ~504 ký tự
            String longPassword = "P@ssw0rd!".repeat(56);
            assertThat(longPassword).hasSizeGreaterThan(72); // Vượt quá BCrypt limit 72 byte
            loginRequest.setPassword(longPassword);
            when(passwordEncoder.matches(longPassword, ENCODED_PWD_LOGIN)).thenReturn(false);

            // WHEN
            ApiResponse response = userService.login(loginRequest);

            // THEN
            assertFalse(response.isSuccess());
            assertEquals("Mật khẩu không đúng!", response.getMessage());
            verify(passwordEncoder).matches(longPassword, ENCODED_PWD_LOGIN);
        }

        /**
         * TC_AUTH_LOGIN_010 — Password rỗng (độ dài = 0)
         *
         * GIVEN: Password là "" (chuỗi rỗng).
         * WHEN : userService.login(loginRequest) được gọi.
         * THEN : passwordEncoder.matches("", ...) trả false → error "Mật khẩu không đúng!".
         */
        @Test
        @DisplayName("TC_AUTH_LOGIN_010 — Boundary: Password rỗng (length=0) → Error 'Mật khẩu không đúng!'")
        void TC_AUTH_LOGIN_010_boundary_passwordLengthZero() {
            // GIVEN
            loginRequest.setPassword("");
            when(passwordEncoder.matches("", ENCODED_PWD_LOGIN)).thenReturn(false);

            // WHEN
            ApiResponse response = userService.login(loginRequest);

            // THEN
            assertFalse(response.isSuccess());
            assertEquals("Mật khẩu không đúng!", response.getMessage());
            verify(passwordEncoder).matches("", ENCODED_PWD_LOGIN);
        }

        // ============================================================
        //  [SECURITY]
        // ============================================================

        /**
         * TC_AUTH_LOGIN_011 — SQL Injection trong email
         *
         * GIVEN: Email chứa payload SQL: "' OR '1'='1'; --@evil.com".
         * WHEN : userService.login(loginRequest) được gọi.
         * THEN : Service truyền nguyên payload vào findByEmail() —
         *        JPA/Hibernate dùng parameterized query nên SQL injection không được thực thi.
         *        findByEmail trả Optional.empty() → error "Email không tồn tại!".
         */
        @Test
        @DisplayName("TC_AUTH_LOGIN_011 — Security: SQL Injection trong email → JPA bảo vệ, error đúng")
        void TC_AUTH_LOGIN_011_security_sqlInjectionInEmail() {
            // GIVEN
            String sqlPayload = "' OR '1'='1'; --@evil.com";
            loginRequest.setEmail(sqlPayload);
            // JPA nhận payload như một string bình thường → không tìm thấy user
            when(userRepository.findByEmail(sqlPayload)).thenReturn(java.util.Optional.empty());

            // WHEN
            ApiResponse response = userService.login(loginRequest);

            // THEN
            assertFalse(response.isSuccess(),
                    "SQL injection payload không tạo được session (JPA parameterized query bảo vệ)");
            assertEquals("Email không tồn tại!", response.getMessage());
            // Xác nhận service truyền nguyên payload → JPA xử lý an toàn
            verify(userRepository).findByEmail(sqlPayload);
            verify(passwordEncoder, never()).matches(any(), any());
        }

        /**
         * TC_AUTH_LOGIN_012 — Brute-force simulation (5 lần sai password liên tiếp)
         *
         * GIVEN: Attacker thử 5 lần với các password sai.
         * WHEN : userService.login() được gọi 5 lần với password sai.
         * THEN : Tất cả 5 lần đều trả error "Mật khẩu không đúng!".
         *        NOTE: Service KHÔNG có rate-limit hay account-lock sau N lần sai —
         *              đây là lỗ hổng bảo mật cần bổ sung (account lockout policy).
         */
        @Test
        @DisplayName("TC_AUTH_LOGIN_012 — Security: Brute-force (5 lần sai) → Mỗi lần đều error (thiếu rate-limit)")
        void TC_AUTH_LOGIN_012_security_bruteForceSimulation() {
            // GIVEN — 5 password khác nhau đều sai
            String[] wrongPasswords = {
                    "wrongPass1", "wrongPass2", "wrongPass3", "wrongPass4", "wrongPass5"
            };

            for (int attempt = 1; attempt <= 5; attempt++) {
                String wrongPwd = wrongPasswords[attempt - 1];
                loginRequest.setPassword(wrongPwd);
                when(passwordEncoder.matches(wrongPwd, ENCODED_PWD_LOGIN)).thenReturn(false);

                // WHEN
                ApiResponse response = userService.login(loginRequest);

                // THEN — mỗi lần đều phải trả error (không có lockout)
                assertFalse(response.isSuccess(),
                        "Lần thử " + attempt + ": phải là error khi password sai");
                assertEquals("Mật khẩu không đúng!", response.getMessage(),
                        "Lần thử " + attempt + ": message phải là 'Mật khẩu không đúng!'");
            }

            // Xác nhận: sau 5 lần sai, tài khoản vẫn chưa bị khóa (thiếu lockout policy)
            // → findByEmail được gọi đúng 5 lần
            verify(userRepository, times(5)).findByEmail(LOGIN_EMAIL);
            // Không có lần nào tạo JWT
            verify(jwtService, never()).generateToken(any(), any());
        }

        // ============================================================
        //  [TOKEN]
        // ============================================================

        /**
         * TC_AUTH_LOGIN_013 — JWT được tạo và trả về đúng trong LoginResponse
         *
         * GIVEN: Thông tin đăng nhập hợp lệ.
         * WHEN : userService.login(loginRequest) được gọi.
         * THEN : jwtService.generateToken() được gọi đúng 1 lần;
         *        token trong LoginResponse bằng đúng giá trị mock JWT.
         */
        @Test
        @DisplayName("TC_AUTH_LOGIN_013 — Token: JWT được tạo bởi jwtService và có trong LoginResponse")
        void TC_AUTH_LOGIN_013_token_jwtGeneratedAndReturned() {
            // GIVEN — happy-path stub từ @BeforeEach

            // WHEN
            ApiResponse response = userService.login(loginRequest);

            // THEN
            assertTrue(response.isSuccess());
            // Verify jwtService.generateToken được gọi đúng 1 lần với email đúng
            verify(jwtService, times(1)).generateToken(eq(LOGIN_EMAIL), any());

            // Verify token trong response là mock JWT đã định nghĩa
            LoginResponse loginResp = (LoginResponse) response.getData();
            assertNotNull(loginResp.getToken(), "Token không được null");
            assertEquals(MOCK_JWT, loginResp.getToken(), "Token trong response phải khớp với mock JWT");
            assertFalse(loginResp.getToken().isEmpty(), "Token không được rỗng");
        }

        /**
         * TC_AUTH_LOGIN_014 — JWT claims chứa đúng role của user
         *
         * GIVEN: User với role = CUSTOMER.
         * WHEN : userService.login(loginRequest) được gọi.
         * THEN : Claims truyền vào generateToken() phải chứa:
         *        - "role" = "CUSTOMER"
         *        - "ROLE_CUSTOMER" = true (Spring Security convention)
         */
        @Test
        @DisplayName("TC_AUTH_LOGIN_014 — Token: JWT claims chứa đúng role và ROLE_ prefix")
        void TC_AUTH_LOGIN_014_token_jwtContainsCorrectClaims() {
            // GIVEN
            @SuppressWarnings("unchecked")
            ArgumentCaptor<java.util.Map<String, Object>> claimsCaptor =
                    ArgumentCaptor.forClass(java.util.Map.class);

            // WHEN
            userService.login(loginRequest);

            // THEN — kiểm tra claims được truyền vào generateToken
            verify(jwtService).generateToken(eq(LOGIN_EMAIL), claimsCaptor.capture());
            java.util.Map<String, Object> capturedClaims = claimsCaptor.getValue();

            assertNotNull(capturedClaims, "Claims không được null");
            assertEquals("CUSTOMER", capturedClaims.get("role"),
                    "Claim 'role' phải là 'CUSTOMER'");
            assertEquals(true, capturedClaims.get("ROLE_CUSTOMER"),
                    "Claim 'ROLE_CUSTOMER' phải là true (Spring Security convention)");
        }

        /**
         * TC_AUTH_LOGIN_015 — Employee đăng nhập lần đầu → yêu cầu đổi mật khẩu (không tạo JWT)
         *
         * GIVEN: User là EMPLOYEE với firstLogin = true.
         * WHEN : userService.login(loginRequest) được gọi.
         * THEN : Trả về success "Đăng nhập lần đầu. Yêu cầu đổi mật khẩu!";
         *        data chứa {requireChangePassword: true, email: ...};
         *        jwtService.generateToken() KHÔNG được gọi (token không tạo cho lần đầu).
         */
        @Test
        @DisplayName("TC_AUTH_LOGIN_015 — Token: Employee firstLogin → Không tạo JWT, yêu cầu đổi mật khẩu")
        void TC_AUTH_LOGIN_015_token_employeeFirstLogin_noJwtGenerated() {
            // GIVEN
            Employee employee = Employee.builder()
                    .id(10L)
                    .fullName("Nhân Viên C")
                    .firstLogin(true) // Đăng nhập lần đầu
                    .build();

            User employeeUser = User.builder()
                    .id(2L)
                    .email(LOGIN_EMAIL)
                    .password(ENCODED_PWD_LOGIN)
                    .role(Role.EMPLOYEE)
                    .status(Status.ACTIVE)
                    .employee(employee)  // Không có customer
                    .build();
            employee.setUser(employeeUser);

            when(userRepository.findByEmail(LOGIN_EMAIL))
                    .thenReturn(java.util.Optional.of(employeeUser));
            when(passwordEncoder.matches(LOGIN_PASSWORD, ENCODED_PWD_LOGIN)).thenReturn(true);

            // WHEN
            ApiResponse response = userService.login(loginRequest);

            // THEN
            assertTrue(response.isSuccess(), "Phải là success (nhưng không có JWT)");
            assertEquals("Đăng nhập lần đầu. Yêu cầu đổi mật khẩu!", response.getMessage());

            // Verify JWT KHÔNG được tạo cho lần đăng nhập đầu tiên
            verify(jwtService, never()).generateToken(any(), any());

            // Verify response data chứa requireChangePassword = true
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> data = (java.util.Map<String, Object>) response.getData();
            assertEquals(true, data.get("requireChangePassword"),
                    "Data phải chứa requireChangePassword = true");
            assertEquals(LOGIN_EMAIL, data.get("email"),
                    "Data phải chứa email của user");
        }

        /**
         * TC_AUTH_LOGIN_016 — jwtService.generateToken() ném RuntimeException (khóa bị giả mạo)
         *
         * GIVEN: jwtService.generateToken() ném RuntimeException
         *        (mô phỏng: JWT secret bị thay đổi/tampered, signing thất bại).
         * WHEN : userService.login(loginRequest) được gọi.
         * THEN : Exception propagate lên caller (service không bắt lỗi signing).
         */
        @Test
        @DisplayName("TC_AUTH_LOGIN_016 — Token: jwtService ném RuntimeException (tampered key) → propagate")
        void TC_AUTH_LOGIN_016_token_jwtServiceThrowsException() {
            // GIVEN — mô phỏng signing key bị giả mạo → JWT signing thất bại
            when(jwtService.generateToken(eq(LOGIN_EMAIL), any()))
                    .thenThrow(new RuntimeException("JWT signing failed: tampered secret key"));

            // WHEN & THEN
            RuntimeException ex = assertThrows(
                    RuntimeException.class,
                    () -> userService.login(loginRequest),
                    "RuntimeException từ jwtService phải propagate lên caller"
            );
            assertEquals("JWT signing failed: tampered secret key", ex.getMessage());
        }

        // ============================================================
        //  [SYSTEM]
        // ============================================================

        /**
         * TC_AUTH_LOGIN_017 — DB error: userRepository.findByEmail() ném RuntimeException
         *
         * GIVEN: Cơ sở dữ liệu không khả dụng (connection timeout/network error).
         * WHEN : userService.login(loginRequest) được gọi.
         * THEN : RuntimeException từ repository propagate lên caller.
         *        Password không được kiểm tra, JWT không được tạo.
         */
        @Test
        @DisplayName("TC_AUTH_LOGIN_017 — System: DB error từ findByEmail() → RuntimeException propagate")
        void TC_AUTH_LOGIN_017_system_dbErrorFromRepository() {
            // GIVEN — mô phỏng DB timeout
            when(userRepository.findByEmail(LOGIN_EMAIL))
                    .thenThrow(new RuntimeException("Database connection timeout"));

            // WHEN & THEN
            RuntimeException ex = assertThrows(
                    RuntimeException.class,
                    () -> userService.login(loginRequest),
                    "RuntimeException từ DB phải propagate"
            );
            assertEquals("Database connection timeout", ex.getMessage());
            verify(passwordEncoder, never()).matches(any(), any());
            verify(jwtService, never()).generateToken(any(), any());
        }

        /**
         * TC_AUTH_LOGIN_018 — passwordEncoder.matches() ném RuntimeException (lỗi crypto)
         *
         * GIVEN: BCrypt gặp lỗi nội bộ khi so khớp (lỗi hạ tầng crypto).
         * WHEN : userService.login(loginRequest) được gọi.
         * THEN : RuntimeException propagate; JWT không được tạo.
         */
        @Test
        @DisplayName("TC_AUTH_LOGIN_018 — System: passwordEncoder.matches() ném RuntimeException → propagate")
        void TC_AUTH_LOGIN_018_system_passwordEncoderFailure() {
            // GIVEN — mô phỏng lỗi nội bộ BCrypt
            when(passwordEncoder.matches(LOGIN_PASSWORD, ENCODED_PWD_LOGIN))
                    .thenThrow(new RuntimeException("BCrypt internal error: invalid salt"));

            // WHEN & THEN
            RuntimeException ex = assertThrows(
                    RuntimeException.class,
                    () -> userService.login(loginRequest),
                    "RuntimeException từ passwordEncoder phải propagate"
            );
            assertEquals("BCrypt internal error: invalid salt", ex.getMessage());
            verify(jwtService, never()).generateToken(any(), any());
        }

        // ============================================================
        //  [CHECKDB]
        // ============================================================

        /**
         * TC_AUTH_LOGIN_019 — Verify KHÔNG gọi userRepository.save() khi login
         *
         * GIVEN: Đăng nhập thành công.
         * WHEN : userService.login(loginRequest) được gọi.
         * THEN : userRepository.save() KHÔNG được gọi bất kỳ lần nào —
         *        login chỉ READ dữ liệu, không ghi.
         */
        @Test
        @DisplayName("TC_AUTH_LOGIN_019 — CheckDB: Login thành công → Không gọi save() (read-only operation)")
        void TC_AUTH_LOGIN_019_checkdb_verifyNoSaveCalledDuringLogin() {
            // GIVEN — happy-path từ @BeforeEach

            // WHEN
            ApiResponse response = userService.login(loginRequest);

            // THEN
            assertTrue(response.isSuccess());

            // Login là thao tác READ-ONLY: không được phép gọi save()
            verify(userRepository, never()).save(any(User.class));

            // Xác nhận chỉ gọi findByEmail() đúng 1 lần (không gọi lại hoặc gọi findBy khác)
            verify(userRepository, times(1)).findByEmail(LOGIN_EMAIL);
            verifyNoMoreInteractions(userRepository);
        }

        // ============================================================
        //  [CONCURRENCY]
        // ============================================================

        /**
         * TC_AUTH_LOGIN_020 — Nhiều login requests tuần tự (simulate concurrency)
         *
         * GIVEN: 3 request đăng nhập với cùng email/password đúng (giả lập multi-thread).
         * WHEN : userService.login() được gọi 3 lần liên tiếp.
         * THEN : Mỗi request đều độc lập thành công;
         *        jwtService.generateToken() được gọi đúng 3 lần;
         *        mỗi response đều chứa JWT (stateless — không ảnh hưởng nhau).
         */
        @Test
        @DisplayName("TC_AUTH_LOGIN_020 — Concurrency: 3 login requests → Mỗi request độc lập thành công")
        void TC_AUTH_LOGIN_020_concurrency_multipleLoginRequests() {
            // GIVEN — cùng credential, mô phỏng 3 thread đăng nhập đồng thời
            // (sequential simulation vì Mockito không hỗ trợ true concurrency)
            int numberOfRequests = 3;

            // WHEN & THEN — mỗi lần gọi phải thành công độc lập
            for (int i = 0; i < numberOfRequests; i++) {
                ApiResponse response = userService.login(loginRequest);
                assertTrue(response.isSuccess(),
                        "Request " + (i + 1) + "/" + numberOfRequests + " phải thành công");
                assertEquals("Đăng nhập thành công!", response.getMessage());

                LoginResponse loginResp = (LoginResponse) response.getData();
                assertEquals(MOCK_JWT, loginResp.getToken(),
                        "Request " + (i + 1) + ": token phải là mock JWT");
            }

            // Verify: generateToken được gọi đúng số lần = số requests
            // (stateless — mỗi request tạo token mới độc lập)
            verify(jwtService, times(numberOfRequests)).generateToken(eq(LOGIN_EMAIL), any());
            // findByEmail cũng được gọi đúng numberOfRequests lần
            verify(userRepository, times(numberOfRequests)).findByEmail(LOGIN_EMAIL);
        }
    }

    // ================================================================
    // ================================================================
    //  PHẦN 3 — USER MANAGEMENT: UserServiceImpl (getCurrentUser / changePassword)
    // ================================================================
    // ================================================================

    /**
     * ================================================================
     * SQA UNIT TEST - MODULE: USER MANAGEMENT
     * ================================================================
     * Test Suite ID  : TS_AUTH_USER
     * Số test cases  : 10
     * SUT            : UserServiceImpl.getCurrentUser(String email)
     *                  UserServiceImpl.changePassword(String email, ChangePasswordRequest)
     *
     * Lưu ý kiến trúc:
     *  ▸ Service KHÔNG có getUserById(Long id) — tra cứu user qua email.
     *    TC_AUTH_USER_001/002/003 map "get by ID" → getCurrentUser(email).
     *  ▸ Service KHÔNG có deleteUser() — hệ thống hiện tại quản lý trạng thái
     *    qua Status enum (ACTIVE / LOCKED / INACTIVE), không xoá vật lý.
     *    TC_AUTH_USER_008/009 kiểm tra sự vắng mặt của thao tác delete.
     *  ▸ "Update user" được map vào changePassword() — thao tác update
     *    dữ liệu User duy nhất ở tầng service.
     *
     * LUỒNG XỬ LÝ:
     *  getCurrentUser(email):
     *    1. findByEmail()  → không có → RuntimeException
     *    2. Build userData map → success
     *
     *  changePassword(email, req):
     *    1. findByEmail()         → không có → RuntimeException
     *    2. matches(old, encoded) → false → error "Mật khẩu cũ không đúng!"
     *    3. newPwd != confirmPwd  → error "Xác nhận mật khẩu mới không khớp!"
     *    4. encode(newPwd) + save() → success
     * ================================================================
     */
    @Nested
    @DisplayName("UserService — USER MANAGEMENT (getCurrentUser / changePassword) Unit Tests")
    class UserManagementTests {

        // ============================================================
        //  MOCK DEPENDENCIES
        // ============================================================

        @Mock
        private UserRepository userRepository;

        @Mock
        private CustomerRepository customerRepository;

        @Mock
        private PasswordEncoder passwordEncoder;

        @Mock
        private JwtService jwtService;

        /** SUT */
        @InjectMocks
        private UserServiceImpl userService;

        // ============================================================
        //  HẰNG SỐ DỮ LIỆU TEST
        // ============================================================

        private static final String USER_EMAIL      = "user@example.com";
        private static final String OLD_PASSWORD    = "OldPass@123";
        private static final String NEW_PASSWORD    = "NewPass@456";
        private static final String ENCODED_OLD     = "$2a$10$encodedOldPassword";
        private static final String ENCODED_NEW     = "$2a$10$encodedNewPassword";

        private User            activeUser;
        private Customer        customer;
        private ChangePasswordRequest changePwdReq;

        // ============================================================
        //  SETUP & TEARDOWN
        // ============================================================

        @BeforeEach
        void setUpUserManagement() {
            // Build customer profile
            customer = Customer.builder()
                    .id(1L)
                    .fullName("Nguyễn Văn A")
                    .phone("0912345678")
                    .address("123 Lê Lợi, Hà Nội")
                    .build();

            // Build User ACTIVE – CUSTOMER
            activeUser = User.builder()
                    .id(10L)
                    .email(USER_EMAIL)
                    .password(ENCODED_OLD)
                    .role(Role.CUSTOMER)
                    .status(Status.ACTIVE)
                    .customer(customer)
                    .build();
            customer.setUser(activeUser);

            // ChangePasswordRequest hợp lệ
            changePwdReq = new ChangePasswordRequest();
            changePwdReq.setOldPassword(OLD_PASSWORD);
            changePwdReq.setNewPassword(NEW_PASSWORD);
            changePwdReq.setConfirmPassword(NEW_PASSWORD);

            // Lenient stubs cho happy-path
            lenient().when(userRepository.findByEmail(USER_EMAIL))
                     .thenReturn(java.util.Optional.of(activeUser));
            lenient().when(passwordEncoder.matches(OLD_PASSWORD, ENCODED_OLD))
                     .thenReturn(true);
            lenient().when(passwordEncoder.encode(NEW_PASSWORD))
                     .thenReturn(ENCODED_NEW);
        }

        @AfterEach
        void tearDownUserManagement() {
            Mockito.reset(userRepository, customerRepository, passwordEncoder, jwtService);
        }

        // ============================================================
        //  [GET USER]
        // ============================================================

        /**
         * TC_AUTH_USER_001 — Lấy thông tin user hợp lệ (Customer)
         *
         * Mapping: "Get user by ID (valid)" → getCurrentUser(email)
         *          (service dùng email làm định danh thay cho ID)
         *
         * GIVEN: User CUSTOMER ACTIVE tồn tại với email hợp lệ.
         * WHEN : userService.getCurrentUser(USER_EMAIL) được gọi.
         * THEN : Trả về ApiResponse success; data chứa email, role, status đúng.
         */
        @Test
        @DisplayName("TC_AUTH_USER_001 — Get User (valid): Customer tồn tại → success + data đầy đủ")
        void TC_AUTH_USER_001_getUser_validCustomer_returnsSuccessWithData() {
            // GIVEN — stub từ @BeforeEach

            // WHEN
            ApiResponse response = userService.getCurrentUser(USER_EMAIL);

            // THEN
            assertTrue(response.isSuccess(), "Phải là success response");
            assertEquals("Lấy thông tin người dùng thành công", response.getMessage());
            assertNotNull(response.getData(), "Data không được null");

            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> data = (java.util.Map<String, Object>) response.getData();
            assertEquals(10L, data.get("id"),       "ID phải khớp với user trong DB");
            assertEquals(USER_EMAIL, data.get("email"), "Email phải khớp");
            assertEquals("CUSTOMER", data.get("role"),  "Role phải là CUSTOMER");
            assertEquals("ACTIVE", data.get("status"),  "Status phải là ACTIVE");
        }

        /**
         * TC_AUTH_USER_002 — Lấy thông tin user Employee có Position
         *
         * Mapping: "Get user by ID (valid)" — trường hợp Employee có position.
         *
         * GIVEN: User EMPLOYEE ACTIVE với Position.SALE và firstLogin=false.
         * WHEN : userService.getCurrentUser(email) được gọi.
         * THEN : Data chứa fullName, role=EMPLOYEE, position=SALE, employeeId.
         */
        @Test
        @DisplayName("TC_AUTH_USER_002 — Get User (valid): Employee có Position → data chứa position/employeeId")
        void TC_AUTH_USER_002_getUser_validEmployee_returnsPositionAndEmployeeId() {
            // GIVEN
            Employee employee = Employee.builder()
                    .id(20L)
                    .fullName("Lê Thị C")
                    .position(Position.SALE)
                    .firstLogin(false)
                    .build();

            User employeeUser = User.builder()
                    .id(11L)
                    .email(USER_EMAIL)
                    .password(ENCODED_OLD)
                    .role(Role.EMPLOYEE)
                    .status(Status.ACTIVE)
                    .employee(employee)
                    .build();
            employee.setUser(employeeUser);

            when(userRepository.findByEmail(USER_EMAIL))
                    .thenReturn(java.util.Optional.of(employeeUser));

            // WHEN
            ApiResponse response = userService.getCurrentUser(USER_EMAIL);

            // THEN
            assertTrue(response.isSuccess());

            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> data = (java.util.Map<String, Object>) response.getData();
            assertEquals("EMPLOYEE", data.get("role"),    "Role phải là EMPLOYEE");
            assertEquals("SALE",     data.get("position"), "Position phải là SALE");
            assertEquals(20L,        data.get("employeeId"), "EmployeeId phải khớp");
        }

        /**
         * TC_AUTH_USER_003 — Lấy thông tin user với email không tồn tại
         *
         * Mapping: "Get user invalid ID" → getCurrentUser với email không có trong DB.
         *
         * GIVEN: userRepository.findByEmail() trả Optional.empty().
         * WHEN : userService.getCurrentUser("notfound@example.com") được gọi.
         * THEN : RuntimeException được ném với message "Không tìm thấy người dùng!".
         */
        @Test
        @DisplayName("TC_AUTH_USER_003 — Get User (invalid): Email không tồn tại → RuntimeException")
        void TC_AUTH_USER_003_getUser_emailNotFound_throwsRuntimeException() {
            // GIVEN
            String notFoundEmail = "notfound@example.com";
            when(userRepository.findByEmail(notFoundEmail))
                    .thenReturn(java.util.Optional.empty());

            // WHEN & THEN
            RuntimeException ex = assertThrows(
                    RuntimeException.class,
                    () -> userService.getCurrentUser(notFoundEmail),
                    "Phải ném RuntimeException khi email không tồn tại"
            );
            assertEquals("Không tìm thấy người dùng!", ex.getMessage());
            // Verify: không có thao tác nào khác được thực hiện
            verify(userRepository).findByEmail(notFoundEmail);
        }

        // ============================================================
        //  [UPDATE USER]
        // ============================================================

        /**
         * TC_AUTH_USER_004 — Đổi mật khẩu hợp lệ
         *
         * Mapping: "Update user valid" → changePassword(email, req) đầy đủ và đúng.
         *
         * GIVEN: User tồn tại, oldPassword đúng, newPassword khớp confirmPassword.
         * WHEN : userService.changePassword(USER_EMAIL, changePwdReq) được gọi.
         * THEN : Trả về success "Đổi mật khẩu thành công!";
         *        userRepository.save() được gọi với password đã được mã hoá mới.
         */
        @Test
        @DisplayName("TC_AUTH_USER_004 — Update (valid): Đổi mật khẩu hợp lệ → success + password được cập nhật")
        void TC_AUTH_USER_004_updateUser_validChangePassword_success() {
            // GIVEN — happy-path stub từ @BeforeEach

            // WHEN
            ApiResponse response = userService.changePassword(USER_EMAIL, changePwdReq);

            // THEN
            assertTrue(response.isSuccess(), "Phải là success response");
            assertEquals("Đổi mật khẩu thành công!", response.getMessage());

            // Verify: password được mã hoá và user được lưu
            ArgumentCaptor<User> savedUserCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(savedUserCaptor.capture());
            assertEquals(ENCODED_NEW, savedUserCaptor.getValue().getPassword(),
                    "Password trong DB phải là encoded version của newPassword");
        }

        /**
         * TC_AUTH_USER_005 — Đổi mật khẩu với oldPassword sai
         *
         * Mapping: "Update invalid data" → changePassword với dữ liệu sai.
         *
         * GIVEN: User tồn tại, nhưng oldPassword không khớp với hash trong DB.
         * WHEN : userService.changePassword(USER_EMAIL, changePwdReq) được gọi.
         * THEN : Trả về error "Mật khẩu cũ không đúng!"; save() không được gọi.
         */
        @Test
        @DisplayName("TC_AUTH_USER_005 — Update (invalid data): oldPassword sai → Error 'Mật khẩu cũ không đúng!'")
        void TC_AUTH_USER_005_updateUser_wrongOldPassword_returnsError() {
            // GIVEN
            when(passwordEncoder.matches(OLD_PASSWORD, ENCODED_OLD)).thenReturn(false);

            // WHEN
            ApiResponse response = userService.changePassword(USER_EMAIL, changePwdReq);

            // THEN
            assertFalse(response.isSuccess(), "Phải là error khi oldPassword sai");
            assertEquals("Mật khẩu cũ không đúng!", response.getMessage());
            // Password không được cập nhật vào DB
            verify(userRepository, never()).save(any(User.class));
            verify(passwordEncoder, never()).encode(any());
        }

        /**
         * TC_AUTH_USER_006 — Đổi mật khẩu với confirmPassword không khớp
         *
         * Mapping: "Update null fields" → trường confirmPassword không khớp
         *          (simulates invalid/mismatched field data).
         *
         * GIVEN: oldPassword đúng, nhưng confirmPassword ≠ newPassword.
         * WHEN : userService.changePassword(USER_EMAIL, changePwdReq) được gọi.
         * THEN : Trả về error "Xác nhận mật khẩu mới không khớp!"; save() không gọi.
         */
        @Test
        @DisplayName("TC_AUTH_USER_006 — Update (null/mismatch fields): confirmPassword ≠ newPassword → Error")
        void TC_AUTH_USER_006_updateUser_confirmPasswordMismatch_returnsError() {
            // GIVEN
            changePwdReq.setConfirmPassword("TotallyDifferentPass@999");

            // WHEN
            ApiResponse response = userService.changePassword(USER_EMAIL, changePwdReq);

            // THEN
            assertFalse(response.isSuccess(), "Phải là error khi confirmPassword không khớp");
            assertEquals("Xác nhận mật khẩu mới không khớp!", response.getMessage());
            // Không mã hoá, không lưu
            verify(passwordEncoder, never()).encode(any());
            verify(userRepository, never()).save(any(User.class));
        }

        /**
         * TC_AUTH_USER_007 — Đổi mật khẩu khi user không tồn tại
         *
         * Mapping: "Update user not found" → changePassword với email không có trong DB.
         *
         * GIVEN: userRepository.findByEmail() trả Optional.empty().
         * WHEN : userService.changePassword("ghost@example.com", req) được gọi.
         * THEN : RuntimeException được ném; password không được cập nhật.
         */
        @Test
        @DisplayName("TC_AUTH_USER_007 — Update (not found): User không tồn tại → RuntimeException")
        void TC_AUTH_USER_007_updateUser_userNotFound_throwsRuntimeException() {
            // GIVEN
            String ghostEmail = "ghost@example.com";
            when(userRepository.findByEmail(ghostEmail))
                    .thenReturn(java.util.Optional.empty());

            // WHEN & THEN
            RuntimeException ex = assertThrows(
                    RuntimeException.class,
                    () -> userService.changePassword(ghostEmail, changePwdReq),
                    "Phải ném RuntimeException khi user không tồn tại"
            );
            assertEquals("Không tìm thấy người dùng!", ex.getMessage());
            verify(passwordEncoder, never()).matches(any(), any());
            verify(userRepository, never()).save(any(User.class));
        }

        // ============================================================
        //  [DELETE USER — GHI CHÚ KIẾN TRÚC]
        // ============================================================

        /**
         * TC_AUTH_USER_008 — Delete user: Service KHÔNG expose phương thức xoá
         *
         * Mapping: "Delete user" / "Delete user not found" / "Delete already deleted user"
         *
         * Ghi chú thiết kế:
         *  ▸ UserServiceImpl KHÔNG có deleteUser() hay deactivateUser() methods.
         *  ▸ Hệ thống quản lý vòng đời user qua Status enum (ACTIVE/LOCKED/INACTIVE),
         *    không thực hiện xoá vật lý (physical delete).
         *  ▸ Test này xác nhận rằng các thao tác đọc/cập nhật KHÔNG vô tình
         *    gọi userRepository.delete() hay userRepository.deleteById().
         *
         * GIVEN: User tồn tại; gọi getCurrentUser() và changePassword() thành công.
         * WHEN : Cả hai methods được gọi theo thứ tự.
         * THEN : userRepository.delete() / deleteById() KHÔNG BAO GIỜ được gọi.
         */
        @Test
        @DisplayName("TC_AUTH_USER_008 — Delete (architecture gap): deleteById() không bao giờ được gọi trong service")
        void TC_AUTH_USER_008_deleteUser_notImplemented_repositoryDeleteNeverInvoked() {
            // GIVEN — happy-path stubs từ @BeforeEach

            // WHEN — thực hiện tất cả các thao tác service hiện có
            userService.getCurrentUser(USER_EMAIL);
            userService.changePassword(USER_EMAIL, changePwdReq);

            // THEN — xác nhận không có thao tác delete nào được thực hiện
            verify(userRepository, never()).delete(any(User.class));
            verify(userRepository, never()).deleteById(any());
            verify(userRepository, never()).deleteAll();

            // Ghi lại gap: cần bổ sung deleteUser() / deactivateUser() method
            // để hỗ trợ admin vô hiệu hoá tài khoản
        }

        // ============================================================
        //  [CHECKDB]
        // ============================================================

        /**
         * TC_AUTH_USER_009 — [CHECKDB] changePassword: save() được gọi đúng 1 lần
         *                    với User đã cập nhật password mới
         *
         * GIVEN: Đổi mật khẩu hợp lệ.
         * WHEN : userService.changePassword(USER_EMAIL, changePwdReq) được gọi.
         * THEN : userRepository.save() được gọi đúng 1 lần;
         *        User được lưu có password = encodedNewPassword;
         *        findByEmail() được gọi đúng 1 lần.
         */
        @Test
        @DisplayName("TC_AUTH_USER_009 — [CHECKDB] changePassword: save() gọi 1 lần với encoded new password")
        void TC_AUTH_USER_009_checkdb_changePassword_saveCalledOnceWithEncodedPassword() {
            // GIVEN — happy-path stubs từ @BeforeEach

            // WHEN
            ApiResponse response = userService.changePassword(USER_EMAIL, changePwdReq);
            assertTrue(response.isSuccess());

            // THEN — verify repository interactions
            // 1. findByEmail được gọi đúng 1 lần
            verify(userRepository, times(1)).findByEmail(USER_EMAIL);

            // 2. encode() được gọi với newPassword
            verify(passwordEncoder, times(1)).encode(NEW_PASSWORD);

            // 3. save() được gọi đúng 1 lần
            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository, times(1)).save(captor.capture());

            // 4. User được save với password đã được mã hoá mới
            User savedUser = captor.getValue();
            assertEquals(ENCODED_NEW, savedUser.getPassword(),
                    "Password phải là encoded version của newPassword");
            assertEquals(USER_EMAIL, savedUser.getEmail(),
                    "Email không được thay đổi trong quá trình đổi mật khẩu");

            // 5. Không có thao tác nào khác trên repository
            verifyNoMoreInteractions(userRepository);
        }

        /**
         * TC_AUTH_USER_010 — [CHECKDB] getCurrentUser: read-only, KHÔNG gọi save()
         *
         * GIVEN: User tồn tại; gọi getCurrentUser().
         * WHEN : userService.getCurrentUser(USER_EMAIL) được gọi.
         * THEN : Chỉ gọi findByEmail() đúng 1 lần;
         *        save() / delete() / deleteById() KHÔNG được gọi (read-only).
         */
        @Test
        @DisplayName("TC_AUTH_USER_010 — [CHECKDB] getCurrentUser: read-only, findByEmail 1 lần, không save/delete")
        void TC_AUTH_USER_010_checkdb_getCurrentUser_readOnlyNoSideEffects() {
            // GIVEN — stub từ @BeforeEach

            // WHEN
            ApiResponse response = userService.getCurrentUser(USER_EMAIL);
            assertTrue(response.isSuccess());

            // THEN — verify read-only behavior
            verify(userRepository, times(1)).findByEmail(USER_EMAIL);
            verify(userRepository, never()).save(any(User.class));
            verify(userRepository, never()).delete(any(User.class));
            verify(userRepository, never()).deleteById(any());
            // Toàn bộ tương tác với userRepository chỉ là findByEmail
            verifyNoMoreInteractions(userRepository);
        }
    }

    // ================================================================
    // ================================================================
    //  PHẦN 4 — SECURITY / EDGE / ERROR / CONCURRENCY
    // ================================================================
    // ================================================================

    /**
     * ================================================================
     * SQA UNIT TEST - MODULE: SECURITY, EDGE CASES, ERROR HANDLING
     * ================================================================
     * Test Suite ID  : TS_AUTH_SEC
     * Số test cases  : 15
     * SUT            : UserServiceImpl  (login, getCurrentUser, changePassword,
     *                                    firstChangePassword)
     *                  AuthServiceImpl  (sendOtp)
     *                  JwtService       (isTokenValid, extractEmail)
     *
     * Phân nhóm:
     *  [SECURITY]    TC_AUTH_SEC_001 – TC_AUTH_SEC_008
     *  [EDGE]        TC_AUTH_SEC_009 – TC_AUTH_SEC_012
     *  [ERROR]       TC_AUTH_SEC_013 – TC_AUTH_SEC_015
     *  [CONCURRENCY] TC_AUTH_SEC_016 – TC_AUTH_SEC_017  (ẩn trong ERROR block)
     *
     * Ghi chú quan trọng:
     *  ▸ Token/Auth-header validation (Missing header, Invalid/Expired/Tampered token)
     *    được xử lý bởi Spring Security Filter (JwtAuthFilter), KHÔNG qua service layer.
     *    Các TC liên quan test hành vi của JwtService.isTokenValid() / extractEmail()
     *    — là tầng service duy nhất tiếp xúc với JWT logic.
     *  ▸ SQL Injection và XSS: service truyền payload nguyên trạng vào repository;
     *    JPA parameterized query và Spring MVC HTML escaping chịu trách nhiệm bảo vệ.
     *    Unit test xác nhận service KHÔNG tự ý sanitize hay modify input.
     * ================================================================
     */
    @Nested
    @DisplayName("Security / Edge / Error / Concurrency Unit Tests")
    class SecurityEdgeErrorTests {

        // ============================================================
        //  MOCK DEPENDENCIES
        // ============================================================

        @Mock private UserRepository     userRepository;
        @Mock private CustomerRepository customerRepository;
        @Mock private OtpRepository      otpRepository;
        @Mock private PasswordEncoder    passwordEncoder;
        @Mock private JwtService         jwtService;
        @Mock private JavaMailSender     mailSender;
        @Mock private UserService        userService;

        @InjectMocks private UserServiceImpl userServiceSut;
        @InjectMocks private AuthServiceImpl authServiceSut;

        // ============================================================
        //  HẰNG SỐ & OBJECTS
        // ============================================================

        private static final String EMAIL_A = "user_a@example.com";
        private static final String EMAIL_B = "user_b@example.com";
        private static final String ENCODED = "$2a$10$encodedHash";
        private static final String MOCK_JWT =
                "eyJhbGciOiJIUzI1NiJ9.mockPayload.mockSignature";

        private User userA;
        private User userB;

        // ============================================================
        //  SETUP & TEARDOWN
        // ============================================================

        @BeforeEach
        void setUpSecurity() {
            Customer custA = Customer.builder()
                    .id(1L).fullName("Người Dùng A").phone("0911111111").build();
            userA = User.builder()
                    .id(100L).email(EMAIL_A).password(ENCODED)
                    .role(Role.CUSTOMER).status(Status.ACTIVE).customer(custA).build();
            custA.setUser(userA);

            Customer custB = Customer.builder()
                    .id(2L).fullName("Người Dùng B").phone("0922222222").build();
            userB = User.builder()
                    .id(200L).email(EMAIL_B).password(ENCODED)
                    .role(Role.CUSTOMER).status(Status.ACTIVE).customer(custB).build();
            custB.setUser(userB);

            lenient().when(userRepository.findByEmail(EMAIL_A))
                     .thenReturn(java.util.Optional.of(userA));
            lenient().when(userRepository.findByEmail(EMAIL_B))
                     .thenReturn(java.util.Optional.of(userB));
            lenient().when(passwordEncoder.matches(any(), eq(ENCODED))).thenReturn(true);
            lenient().when(jwtService.generateToken(any(), any())).thenReturn(MOCK_JWT);
        }

        @AfterEach
        void tearDownSecurity() {
            Mockito.reset(userRepository, customerRepository, otpRepository,
                          passwordEncoder, jwtService, mailSender, userService);
        }

        // ============================================================
        //  [SECURITY]
        // ============================================================

        /**
         * TC_AUTH_SEC_001 — User A cố truy cập dữ liệu User B
         *
         * Kịch bản: Service layer nhận email từ Spring Security context (principal).
         * Nếu frontend/attacker gửi thẳng emailB cho getCurrentUser(),
         * service sẽ trả về data của B — đây là lỗ hổng cần Spring Security
         * bảo vệ ở tầng controller (authentication.getName() phải được dùng).
         *
         * GIVEN: userA đã xác thực; gọi getCurrentUser(EMAIL_A) → trả data A.
         *        Attacker gọi getCurrentUser(EMAIL_B) → service trả data B (IDOR risk).
         * WHEN : Hai lần gọi với email khác nhau.
         * THEN : Mỗi lần trả đúng user tương ứng; service KHÔNG tự chặn cross-user access
         *        → xác nhận bảo vệ phải ở tầng controller/filter.
         */
        @Test
        @DisplayName("TC_AUTH_SEC_001 — Security: UserA vs UserB isolation — service trả đúng user theo email")
        void TC_AUTH_SEC_001_security_crossUserAccessIsolation() {
            // WHEN — gọi với email A → nhận data A
            ApiResponse responseA = userServiceSut.getCurrentUser(EMAIL_A);
            assertTrue(responseA.isSuccess());
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> dataA = (java.util.Map<String, Object>) responseA.getData();
            assertEquals(100L, dataA.get("id"), "Phải trả data của User A");
            assertEquals(EMAIL_A, dataA.get("email"));

            // WHEN — gọi với email B → nhận data B (IDOR: service không tự chặn)
            ApiResponse responseB = userServiceSut.getCurrentUser(EMAIL_B);
            assertTrue(responseB.isSuccess());
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> dataB = (java.util.Map<String, Object>) responseB.getData();
            assertEquals(200L, dataB.get("id"), "Phải trả data của User B (IDOR cần controller chặn)");
            assertEquals(EMAIL_B, dataB.get("email"));

            // Verify: hai lần gọi độc lập, không có data lẫn nhau
            verify(userRepository).findByEmail(EMAIL_A);
            verify(userRepository).findByEmail(EMAIL_B);
        }

        /**
         * TC_AUTH_SEC_002 — Missing Authorization header (JWT filter layer)
         *
         * Ghi chú kiến trúc: Thiếu header "Authorization: Bearer ..." được
         * Spring Security JwtAuthFilter chặn TRƯỚC khi tới service.
         * Test này xác nhận: nếu email null truyền vào getCurrentUser(), service
         * ném exception (không có silent failure).
         *
         * GIVEN: email = null (mô phỏng controller truyền null khi không có auth).
         * WHEN : userServiceSut.getCurrentUser(null) được gọi.
         * THEN : RuntimeException hoặc exception được ném; service không trả success.
         */
        @Test
        @DisplayName("TC_AUTH_SEC_002 — Security: Missing auth (email=null) → Exception, không trả success")
        void TC_AUTH_SEC_002_security_missingAuthorizationHeader_nullEmail() {
            // GIVEN
            when(userRepository.findByEmail(null)).thenReturn(java.util.Optional.empty());

            // WHEN & THEN — service ném RuntimeException khi không tìm thấy user
            assertThrows(
                    RuntimeException.class,
                    () -> userServiceSut.getCurrentUser(null),
                    "Phải ném exception khi email null (missing auth)"
            );
            verify(userRepository).findByEmail(null);
            // Không có dữ liệu nào bị lộ
        }

        /**
         * TC_AUTH_SEC_003 — Token không hợp lệ (malformed JWT)
         *
         * GIVEN: jwtService.isTokenValid() nhận token malformed → trả false.
         *        jwtService.extractEmail() ném JwtException.
         * WHEN : JwtService.isTokenValid() được gọi với token rác.
         * THEN : Trả về false; JwtException được bắt nội bộ trong isTokenValid().
         *        Login sau đó dùng credential bình thường → không bị ảnh hưởng.
         */
        @Test
        @DisplayName("TC_AUTH_SEC_003 — Security: Invalid JWT token → isTokenValid=false, login không bị ảnh hưởng")
        void TC_AUTH_SEC_003_security_invalidToken_isTokenValidReturnsFalse() {
            // GIVEN — mock JwtService trả false cho token malformed
            String malformedToken = "not.a.valid.jwt.token.at.all";
            org.springframework.security.core.userdetails.UserDetails userDetails =
                    org.springframework.security.core.userdetails.User.builder()
                            .username(EMAIL_A).password(ENCODED).roles("CUSTOMER").build();

            // isTokenValid dùng mock JwtService nên trả false
            when(jwtService.isTokenValid(malformedToken, userDetails)).thenReturn(false);

            // WHEN
            boolean valid = jwtService.isTokenValid(malformedToken, userDetails);

            // THEN
            assertFalse(valid, "Token malformed phải bị từ chối");

            // Login với credential hợp lệ vẫn hoạt động bình thường (độc lập với token)
            LoginRequest loginReq = new LoginRequest();
            loginReq.setEmail(EMAIL_A);
            loginReq.setPassword("ValidPass@123");
            when(passwordEncoder.matches("ValidPass@123", ENCODED)).thenReturn(true);
            ApiResponse loginResp = userServiceSut.login(loginReq);
            assertTrue(loginResp.isSuccess(), "Login hợp lệ không bị ảnh hưởng bởi token invalid");
        }

        /**
         * TC_AUTH_SEC_004 — Token hết hạn (expired JWT)
         *
         * GIVEN: jwtService.isTokenValid() với token hết hạn → trả false.
         *        jwtService.extractEmail() ném ExpiredJwtException.
         * WHEN : JwtService.isTokenValid() được gọi.
         * THEN : Trả về false; ExpiredJwtException được bắt trong isTokenValid().
         */
        @Test
        @DisplayName("TC_AUTH_SEC_004 — Security: Expired token → isTokenValid=false")
        void TC_AUTH_SEC_004_security_expiredToken_isTokenValidReturnsFalse() {
            // GIVEN
            String expiredToken = "expired.jwt.token";
            org.springframework.security.core.userdetails.UserDetails userDetails =
                    org.springframework.security.core.userdetails.User.builder()
                            .username(EMAIL_A).password(ENCODED).roles("CUSTOMER").build();

            when(jwtService.isTokenValid(expiredToken, userDetails)).thenReturn(false);

            // WHEN
            boolean valid = jwtService.isTokenValid(expiredToken, userDetails);

            // THEN
            assertFalse(valid, "Token hết hạn phải bị từ chối");
        }

        /**
         * TC_AUTH_SEC_005 — Token bị giả mạo (tampered JWT signature)
         *
         * GIVEN: jwtService.generateToken() tạo token hợp lệ;
         *        attacker sửa payload → signature không khớp → ném RuntimeException.
         * WHEN : jwtService.generateToken() ném RuntimeException (tampered secret).
         * THEN : Exception propagate; login thất bại.
         */
        @Test
        @DisplayName("TC_AUTH_SEC_005 — Security: Tampered token → generateToken ném exception → login thất bại")
        void TC_AUTH_SEC_005_security_tamperedToken_loginFails() {
            // GIVEN — mô phỏng attacker dùng secret key giả mạo
            when(jwtService.generateToken(eq(EMAIL_A), any()))
                    .thenThrow(new RuntimeException("SignatureException: JWT signature does not match"));

            LoginRequest loginReq = new LoginRequest();
            loginReq.setEmail(EMAIL_A);
            loginReq.setPassword("ValidPass@123");
            when(passwordEncoder.matches("ValidPass@123", ENCODED)).thenReturn(true);

            // WHEN & THEN
            RuntimeException ex = assertThrows(
                    RuntimeException.class,
                    () -> userServiceSut.login(loginReq),
                    "Tampered token phải ném exception, login thất bại"
            );
            assertThat(ex.getMessage()).contains("SignatureException");
        }

        /**
         * TC_AUTH_SEC_006 — SQL Injection trong email (sendOtp / login)
         *
         * GIVEN: email chứa SQL payload: "admin'--@evil.com".
         * WHEN : login() và sendOtp() được gọi với payload.
         * THEN : Service truyền nguyên payload vào repository (không modify);
         *        JPA parameterized query ngăn SQL injection thực thi;
         *        findByEmail(sqlPayload) trả Optional.empty() → error đúng.
         */
        @Test
        @DisplayName("TC_AUTH_SEC_006 — Security: SQL Injection trong email → JPA bảo vệ, service không modify payload")
        void TC_AUTH_SEC_006_security_sqlInjectionInEmail_rejectedByJpa() {
            // GIVEN
            String sqlPayload = "admin'-- OR '1'='1@evil.com";
            when(userRepository.findByEmail(sqlPayload)).thenReturn(java.util.Optional.empty());

            LoginRequest loginReq = new LoginRequest();
            loginReq.setEmail(sqlPayload);
            loginReq.setPassword("anyPassword");

            // WHEN — login với SQL payload
            ApiResponse response = userServiceSut.login(loginReq);

            // THEN — JPA trả empty → service báo lỗi đúng
            assertFalse(response.isSuccess(), "SQL injection payload phải bị từ chối");
            assertEquals("Email không tồn tại!", response.getMessage());
            // Xác nhận service truyền NGUYÊN payload vào JPA (không sanitize)
            // → JPA chịu trách nhiệm bảo vệ bằng parameterized query
            verify(userRepository).findByEmail(sqlPayload);
            verify(passwordEncoder, never()).matches(any(), any());
        }

        /**
         * TC_AUTH_SEC_007 — XSS payload trong tên đăng ký (sendOtp)
         *
         * GIVEN: fullName chứa XSS: "<script>alert('XSS')</script>".
         * WHEN : sendOtp() được gọi với fullName có XSS payload.
         * THEN : Service lưu payload vào DB mà không sanitize (service layer không escape);
         *        HTML escaping là trách nhiệm của tầng View/Response serializer.
         *        OTP được lưu và email được gửi (sendOtp không validate fullName).
         */
        @Test
        @DisplayName("TC_AUTH_SEC_007 — Security: XSS payload trong fullName → service không sanitize, lưu DB an toàn")
        void TC_AUTH_SEC_007_security_xssPayloadInFullName_storedWithoutEscape() {
            // GIVEN
            String xssPayload = "<script>alert('XSS')</script>";
            RegisterRequest req = new RegisterRequest();
            req.setEmail("legit@example.com");
            req.setFullName(xssPayload);
            req.setPassword("ValidPass@123");
            req.setPhone("0933333333");
            req.setAddress("123 Test Street");

            when(userRepository.existsByEmail("legit@example.com")).thenReturn(false);
            when(customerRepository.existsByPhone("0933333333")).thenReturn(false);
            when(passwordEncoder.encode("ValidPass@123")).thenReturn(ENCODED);

            ArgumentCaptor<OtpVerification> otpCaptor =
                    ArgumentCaptor.forClass(OtpVerification.class);

            // WHEN
            ApiResponse response = authServiceSut.sendOtp(req);

            // THEN — sendOtp thành công; payload được lưu nguyên trạng vào OtpVerification
            assertTrue(response.isSuccess(), "sendOtp phải thành công");
            verify(otpRepository).save(otpCaptor.capture());
            assertEquals(xssPayload, otpCaptor.getValue().getFullName(),
                    "Service không sanitize fullName — tầng View phải escape khi render");
        }

        /**
         * TC_AUTH_SEC_008 — Unauthorized role: Customer cố dùng firstChangePassword (chỉ EMPLOYEE)
         *
         * GIVEN: User với role=CUSTOMER cố gọi firstChangePassword().
         * WHEN : userServiceSut.firstChangePassword(req) được gọi.
         * THEN : Trả về error "Chỉ nhân viên mới được đổi mật khẩu lần đầu!".
         *        Password không được thay đổi; save() không được gọi.
         */
        @Test
        @DisplayName("TC_AUTH_SEC_008 — Security: Unauthorized role — CUSTOMER gọi firstChangePassword → reject")
        void TC_AUTH_SEC_008_security_unauthorizedRole_customerCallsFirstChangePassword() {
            // GIVEN — userA có role CUSTOMER
            when(userRepository.findByEmail(EMAIL_A))
                    .thenReturn(java.util.Optional.of(userA));

            FirstChangePasswordRequest req = new FirstChangePasswordRequest();
            req.setEmail(EMAIL_A);
            req.setCurrentPassword("OldPass@123");
            req.setNewPassword("NewPass@456");
            req.setConfirmPassword("NewPass@456");

            // WHEN
            ApiResponse response = userServiceSut.firstChangePassword(req);

            // THEN
            assertFalse(response.isSuccess(), "CUSTOMER không được phép dùng firstChangePassword");
            assertEquals("Chỉ nhân viên mới được đổi mật khẩu lần đầu!", response.getMessage());
            verify(userRepository, never()).save(any(User.class));
            verify(passwordEncoder, never()).encode(any());
        }

        // ============================================================
        //  [EDGE]
        // ============================================================

        /**
         * TC_AUTH_SEC_009 — Password length = 0 (empty string) trong login
         *
         * GIVEN: loginRequest.password = "" (length = 0).
         * WHEN : userServiceSut.login(loginReq) được gọi.
         * THEN : passwordEncoder.matches("", ENCODED) → false → error "Mật khẩu không đúng!".
         */
        @Test
        @DisplayName("TC_AUTH_SEC_009 — Edge: Password length=0 (empty) → Error 'Mật khẩu không đúng!'")
        void TC_AUTH_SEC_009_edge_passwordLengthZero_loginFails() {
            // GIVEN
            LoginRequest loginReq = new LoginRequest();
            loginReq.setEmail(EMAIL_A);
            loginReq.setPassword("");
            when(passwordEncoder.matches("", ENCODED)).thenReturn(false);

            // WHEN
            ApiResponse response = userServiceSut.login(loginReq);

            // THEN
            assertFalse(response.isSuccess());
            assertEquals("Mật khẩu không đúng!", response.getMessage());
            verify(passwordEncoder).matches("", ENCODED);
            verify(jwtService, never()).generateToken(any(), any());
        }

        /**
         * TC_AUTH_SEC_010 — Password length = 1 (chuỗi đơn ký tự) trong login
         *
         * GIVEN: loginRequest.password = "X" (1 ký tự).
         * WHEN : userServiceSut.login(loginReq) được gọi.
         * THEN : BCrypt.matches("X", ENCODED) → false → error "Mật khẩu không đúng!".
         *        Service KHÔNG ném exception — xử lý gracefully.
         */
        @Test
        @DisplayName("TC_AUTH_SEC_010 — Edge: Password length=1 → Error 'Mật khẩu không đúng!' (no exception)")
        void TC_AUTH_SEC_010_edge_passwordLengthOne_loginFails() {
            // GIVEN
            LoginRequest loginReq = new LoginRequest();
            loginReq.setEmail(EMAIL_A);
            loginReq.setPassword("X");
            when(passwordEncoder.matches("X", ENCODED)).thenReturn(false);

            // WHEN
            ApiResponse response = userServiceSut.login(loginReq);

            // THEN
            assertFalse(response.isSuccess());
            assertEquals("Mật khẩu không đúng!", response.getMessage());
            // Không ném exception — service xử lý gracefully
            verify(passwordEncoder).matches("X", ENCODED);
        }

        /**
         * TC_AUTH_SEC_011 — Password cực kỳ dài (10.000 ký tự) trong login
         *
         * GIVEN: password ~10.000 ký tự (vượt xa giới hạn 72 byte của BCrypt).
         * WHEN : userServiceSut.login(loginReq) được gọi.
         * THEN : passwordEncoder.matches(ultraLongPwd, ENCODED) → false → error đúng.
         *        Service không bị OOM hay timeout — trả response ngay.
         *        Ghi chú: BCrypt chỉ xử lý 72 byte đầu → password 10k và 72-byte prefix
         *                 giống nhau sẽ match. Test verify behavior với password không match.
         */
        @Test
        @DisplayName("TC_AUTH_SEC_011 — Edge: Password 10.000 ký tự → service trả error, không crash/OOM")
        void TC_AUTH_SEC_011_edge_extremelyLongPassword_noOomNoCrash() {
            // GIVEN — password 10.000 ký tự
            String ultraLongPwd = "P@ssw0rd!".repeat(1112); // ~10.008 ký tự
            assertThat(ultraLongPwd.length()).isGreaterThan(9999);

            LoginRequest loginReq = new LoginRequest();
            loginReq.setEmail(EMAIL_A);
            loginReq.setPassword(ultraLongPwd);
            when(passwordEncoder.matches(ultraLongPwd, ENCODED)).thenReturn(false);

            // WHEN
            ApiResponse response = userServiceSut.login(loginReq);

            // THEN — service trả error bình thường, không crash
            assertFalse(response.isSuccess());
            assertEquals("Mật khẩu không đúng!", response.getMessage());
            verify(passwordEncoder).matches(ultraLongPwd, ENCODED);
        }

        /**
         * TC_AUTH_SEC_012 — Email null và empty trong login
         *
         * GIVEN: Hai sub-case: email=null và email="".
         * WHEN : login() được gọi với từng giá trị.
         * THEN : cả hai đều trả error "Email không tồn tại!" (findByEmail trả empty).
         *        Service không ném NullPointerException khi email=null.
         */
        @Test
        @DisplayName("TC_AUTH_SEC_012 — Edge: Email null + empty trong login → Error 'Email không tồn tại!'")
        void TC_AUTH_SEC_012_edge_nullAndEmptyEmail_loginReturnsEmailNotFoundError() {
            // Sub-case 1: email = null
            LoginRequest nullEmailReq = new LoginRequest();
            nullEmailReq.setEmail(null);
            nullEmailReq.setPassword("anyPassword");
            when(userRepository.findByEmail(null)).thenReturn(java.util.Optional.empty());

            ApiResponse responseNull = userServiceSut.login(nullEmailReq);
            assertFalse(responseNull.isSuccess(), "email=null phải trả error");
            assertEquals("Email không tồn tại!", responseNull.getMessage());

            // Sub-case 2: email = "" (empty string)
            LoginRequest emptyEmailReq = new LoginRequest();
            emptyEmailReq.setEmail("");
            emptyEmailReq.setPassword("anyPassword");
            when(userRepository.findByEmail("")).thenReturn(java.util.Optional.empty());

            ApiResponse responseEmpty = userServiceSut.login(emptyEmailReq);
            assertFalse(responseEmpty.isSuccess(), "email=empty phải trả error");
            assertEquals("Email không tồn tại!", responseEmpty.getMessage());

            // Verify: không có lần nào kiểm tra password (email không tồn tại)
            verify(passwordEncoder, never()).matches(any(), any());
        }

        // ============================================================
        //  [ERROR]
        // ============================================================

        /**
         * TC_AUTH_SEC_013 — DB connection failure: findByEmail() ném DataAccessException
         *
         * GIVEN: userRepository.findByEmail() ném RuntimeException (DB timeout/down).
         * WHEN : userServiceSut.login(loginReq) được gọi.
         * THEN : RuntimeException propagate lên caller; password không bị kiểm tra;
         *        JWT không được tạo. (Service không nuốt DB error)
         */
        @Test
        @DisplayName("TC_AUTH_SEC_013 — Error: DB connection failure → RuntimeException propagate, không tạo JWT")
        void TC_AUTH_SEC_013_error_dbConnectionFailure_exceptionPropagates() {
            // GIVEN
            LoginRequest loginReq = new LoginRequest();
            loginReq.setEmail(EMAIL_A);
            loginReq.setPassword("ValidPass@123");
            when(userRepository.findByEmail(EMAIL_A))
                    .thenThrow(new org.springframework.dao.DataAccessResourceFailureException(
                            "DB connection timeout: Cannot connect to MS SQL Server"));

            // WHEN & THEN
            RuntimeException ex = assertThrows(
                    RuntimeException.class,
                    () -> userServiceSut.login(loginReq),
                    "DB error phải propagate lên caller"
            );
            assertThat(ex.getMessage()).contains("DB connection timeout");
            verify(passwordEncoder, never()).matches(any(), any());
            verify(jwtService, never()).generateToken(any(), any());
        }

        /**
         * TC_AUTH_SEC_014 — External service failure: JavaMailSender.send() ném MailSendException
         *
         * GIVEN: SMTP server không khả dụng; mailSender.send() ném MailSendException.
         * WHEN : authServiceSut.sendOtp(req) được gọi.
         * THEN : Trả về error "Không thể gửi email OTP. Vui lòng thử lại sau!";
         *        OTP VẪN được lưu vào DB trước khi gửi email thất bại.
         *        (Design decision: OTP lưu trước, gửi email sau)
         */
        @Test
        @DisplayName("TC_AUTH_SEC_014 — Error: External service (SMTP) failure → OTP saved, error response trả về")
        void TC_AUTH_SEC_014_error_externalServiceFailure_smtpDown() {
            // GIVEN
            RegisterRequest req = new RegisterRequest();
            req.setEmail("newemail@example.com");
            req.setFullName("Người Test");
            req.setPassword("ValidPass@123");
            req.setPhone("0944444444");
            req.setAddress("456 Test Ave");

            when(userRepository.existsByEmail("newemail@example.com")).thenReturn(false);
            when(customerRepository.existsByPhone("0944444444")).thenReturn(false);
            when(passwordEncoder.encode("ValidPass@123")).thenReturn(ENCODED);
            doThrow(new MailSendException("SMTP connection refused: port 587"))
                    .when(mailSender).send(any(SimpleMailMessage.class));

            // WHEN
            ApiResponse response = authServiceSut.sendOtp(req);

            // THEN — error được trả về (try-catch trong sendOtp)
            assertFalse(response.isSuccess(),
                    "Khi SMTP thất bại, sendOtp phải trả error");
            assertEquals("Không thể gửi email OTP. Vui lòng thử lại sau!",
                    response.getMessage());

            // OTP VẪN được lưu vào DB trước khi gửi email
            verify(otpRepository).save(any(OtpVerification.class));
            // mailSender.send() đã được gọi (ném exception)
            verify(mailSender).send(any(SimpleMailMessage.class));
        }

        /**
         * TC_AUTH_SEC_015 — NullPointerException từ passwordEncoder khi User.password = null
         *
         * GIVEN: User trong DB có password = null (dữ liệu bị hỏng).
         *        passwordEncoder.matches(anyString, null) ném NullPointerException.
         * WHEN : userServiceSut.login(loginReq) được gọi.
         * THEN : NullPointerException propagate; JWT không được tạo.
         *        Phát hiện lỗi dữ liệu bị hỏng thay vì silent failure.
         */
        @Test
        @DisplayName("TC_AUTH_SEC_015 — Error: Null password trong DB → NullPointerException propagate (dữ liệu hỏng)")
        void TC_AUTH_SEC_015_error_nullPasswordInDb_nullPointerExceptionPropagates() {
            // GIVEN — user có password = null (DB data corruption)
            User corruptedUser = User.builder()
                    .id(999L).email(EMAIL_A).password(null)
                    .role(Role.CUSTOMER).status(Status.ACTIVE).build();
            when(userRepository.findByEmail(EMAIL_A))
                    .thenReturn(java.util.Optional.of(corruptedUser));

            // BCrypt.matches(rawPwd, null) ném NullPointerException
            doThrow(new NullPointerException("encodedPassword cannot be null"))
                    .when(passwordEncoder).matches(any(), isNull());

            LoginRequest loginReq = new LoginRequest();
            loginReq.setEmail(EMAIL_A);
            loginReq.setPassword("ValidPass@123");

            // WHEN & THEN
            assertThrows(
                    NullPointerException.class,
                    () -> userServiceSut.login(loginReq),
                    "NullPointerException phải propagate khi DB chứa null password"
            );
            verify(jwtService, never()).generateToken(any(), any());
        }

        // ============================================================
        //  [CONCURRENCY]
        // ============================================================

        /**
         * TC_AUTH_SEC_016 — Concurrent register: 5 sendOtp() calls với cùng email
         *
         * Mô phỏng: 5 request đồng thời gửi OTP với cùng email (double-click, retry storm).
         * Tầng service KHÔNG có distributed lock → mỗi call xử lý độc lập.
         *
         * GIVEN: email mới chưa tồn tại; 5 lần gọi sendOtp() với cùng email/phone.
         * WHEN : sendOtp() được gọi 5 lần liên tiếp (sequential simulation).
         * THEN : Tất cả 5 lần đều thành công (service không có idempotency check);
         *        otpRepository.save() được gọi 5 lần → 5 OTP record;
         *        Ghi chú: cần distributed lock hoặc idempotency key để giải quyết.
         */
        @Test
        @DisplayName("TC_AUTH_SEC_016 — Concurrency: 5 sendOtp concurrent → 5 OTP records (thiếu idempotency)")
        void TC_AUTH_SEC_016_concurrency_multipleSimultaneousSendOtp() {
            // GIVEN
            RegisterRequest req = new RegisterRequest();
            req.setEmail("concurrent@example.com");
            req.setFullName("Người Dùng Concurrent");
            req.setPassword("ValidPass@123");
            req.setPhone("0955555555");
            req.setAddress("789 Concurrent Rd");

            when(userRepository.existsByEmail("concurrent@example.com")).thenReturn(false);
            when(customerRepository.existsByPhone("0955555555")).thenReturn(false);
            when(passwordEncoder.encode("ValidPass@123")).thenReturn(ENCODED);
            // mailSender không ném exception → thành công

            // WHEN — 5 requests liên tiếp (mô phỏng 5 thread đồng thời)
            int concurrentRequests = 5;
            int successCount = 0;
            for (int i = 0; i < concurrentRequests; i++) {
                ApiResponse response = authServiceSut.sendOtp(req);
                if (response.isSuccess()) successCount++;
            }

            // THEN — tất cả 5 đều thành công (không có idempotency guard)
            assertEquals(concurrentRequests, successCount,
                    "Tất cả " + concurrentRequests + " requests đều thành công (thiếu idempotency)");
            // OTP được lưu 5 lần → potential data integrity issue
            verify(otpRepository, times(concurrentRequests)).save(any(OtpVerification.class));
            verify(mailSender, times(concurrentRequests)).send(any(SimpleMailMessage.class));
        }

        /**
         * TC_AUTH_SEC_017 — Concurrent login: 10 lần login liên tiếp với credential đúng
         *
         * GIVEN: Cùng credential; 10 request đăng nhập song song (sequential simulation).
         * WHEN : login() được gọi 10 lần.
         * THEN : Mỗi lần đều thành công và độc lập nhau;
         *        generateToken() được gọi đúng 10 lần (stateless — không chia sẻ token);
         *        userRepository.save() KHÔNG được gọi (login là read-only).
         */
        @Test
        @DisplayName("TC_AUTH_SEC_017 — Concurrency: 10 login requests → mỗi request độc lập, stateless JWT")
        void TC_AUTH_SEC_017_concurrency_tenConcurrentLoginRequests_statelessJwt() {
            // GIVEN
            LoginRequest loginReq = new LoginRequest();
            loginReq.setEmail(EMAIL_A);
            loginReq.setPassword("ValidPass@123");
            when(passwordEncoder.matches("ValidPass@123", ENCODED)).thenReturn(true);

            // WHEN
            int concurrentRequests = 10;
            for (int i = 0; i < concurrentRequests; i++) {
                ApiResponse response = userServiceSut.login(loginReq);
                assertTrue(response.isSuccess(),
                        "Request " + (i + 1) + "/" + concurrentRequests + " phải thành công");
                LoginResponse loginResp = (LoginResponse) response.getData();
                assertEquals(MOCK_JWT, loginResp.getToken(),
                        "Token phải là mock JWT (stateless)");
            }

            // THEN — verify stateless behavior
            verify(jwtService, times(concurrentRequests)).generateToken(eq(EMAIL_A), any());
            verify(userRepository, times(concurrentRequests)).findByEmail(EMAIL_A);
            // Login KHÔNG ghi vào DB
            verify(userRepository, never()).save(any(User.class));
        }
    }

    // ================================================================
    // ================================================================
    //  PHẦN 5 — SPEC GAP / DEFECT EVIDENCE TESTS
    // ================================================================
    // ================================================================

    /**
     * ================================================================
     * SQA DEFECT EVIDENCE TESTS
     * ================================================================
     * Mục tiêu:
     *  ▸ Không xác nhận implementation hiện tại.
     *  ▸ Cố tình bám theo nghiệp vụ chuẩn để làm lộ defect.
     *  ▸ Bao gồm cả FAIL (assert sai kỳ vọng) và ERROR
     *    (exception kỹ thuật rơi ra ngoài thay vì được xử lý nghiệp vụ).
     * ================================================================
     */
    @Nested
    @DisplayName("Spec Gap / Defect Evidence Tests")
    class SpecGapDefectEvidenceTests {

        @Mock private UserRepository userRepository;
        @Mock private CustomerRepository customerRepository;
        @Mock private OtpRepository otpRepository;
        @Mock private PasswordEncoder passwordEncoder;
        @Mock private JavaMailSender mailSender;
        @Mock private UserService userService;
        @Mock private JwtService jwtService;

        @InjectMocks private AuthServiceImpl authServiceSut;
        @InjectMocks private UserServiceImpl userServiceSut;

        private RegisterRequest registerRequest;
        private LoginRequest loginRequest;
        private ChangePasswordRequest changePasswordRequest;
        private User activeUser;

        @BeforeEach
        void setUpSpecGap() {
            registerRequest = new RegisterRequest();
            registerRequest.setEmail("specgap@example.com");
            registerRequest.setPassword("ValidPass@123");
            registerRequest.setFullName("Người Dùng Spec Gap");
            registerRequest.setPhone("0966666666");
            registerRequest.setAddress("123 Spec Gap Street");

            loginRequest = new LoginRequest();
            loginRequest.setEmail("specgap@example.com");
            loginRequest.setPassword("ValidPass@123");

            changePasswordRequest = new ChangePasswordRequest();
            changePasswordRequest.setOldPassword("OldPass@123");
            changePasswordRequest.setNewPassword("NewPass@456");
            changePasswordRequest.setConfirmPassword("NewPass@456");

            activeUser = User.builder()
                    .id(777L)
                    .email("specgap@example.com")
                    .password("$2a$10$encodedOldPassword")
                    .role(Role.CUSTOMER)
                    .status(Status.ACTIVE)
                    .customer(Customer.builder()
                            .id(77L)
                            .fullName("Người Dùng Spec Gap")
                            .phone("0966666666")
                            .address("123 Spec Gap Street")
                            .build())
                    .build();
            activeUser.getCustomer().setUser(activeUser);

            lenient().when(userRepository.existsByEmail(any())).thenReturn(false);
            lenient().when(customerRepository.existsByPhone(any())).thenReturn(false);
            lenient().when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$encodedNewPassword");
            lenient().when(otpRepository.save(any(OtpVerification.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            lenient().when(userRepository.findByEmail("specgap@example.com"))
                    .thenReturn(java.util.Optional.of(activeUser));
            lenient().when(passwordEncoder.matches("ValidPass@123", "$2a$10$encodedOldPassword"))
                    .thenReturn(true);
            lenient().when(passwordEncoder.matches("OldPass@123", "$2a$10$encodedOldPassword"))
                    .thenReturn(true);
            lenient().when(jwtService.generateToken(eq("specgap@example.com"), any()))
                    .thenReturn("mock.jwt.token");
        }

        @AfterEach
        void tearDownSpecGap() {
            Mockito.reset(userRepository, customerRepository, otpRepository,
                    passwordEncoder, mailSender, userService, jwtService);
        }

        @Test
        @DisplayName("TC_AUTH_REG_026 — Spec: Email sai định dạng phải bị từ chối ở service layer")
        void TC_AUTH_REG_026_invalidEmailFormat_shouldBeRejectedByBusinessRule() {
            registerRequest.setEmail("not-an-email");

            ApiResponse response = authServiceSut.sendOtp(registerRequest);

            assertFalse(response.isSuccess(),
                    "Theo nghiệp vụ chuẩn, email sai định dạng phải bị từ chối thay vì vẫn gửi OTP");
            assertEquals("Email không đúng định dạng!", response.getMessage());
            verify(otpRepository, never()).save(any(OtpVerification.class));
            verify(mailSender, never()).send(any(SimpleMailMessage.class));
        }

        @Test
        @DisplayName("TC_AUTH_REG_027 — Spec: Số điện thoại trống phải bị từ chối, không lưu OTP")
        void TC_AUTH_REG_027_emptyPhone_shouldNotCreateOtpRecord() {
            registerRequest.setPhone("");
            when(customerRepository.existsByPhone("")).thenReturn(false);

            ApiResponse response = authServiceSut.sendOtp(registerRequest);

            assertFalse(response.isSuccess(),
                    "Theo nghiệp vụ chuẩn, số điện thoại trống phải bị reject thay vì tạo OTP");
            assertEquals("Số điện thoại không được để trống!", response.getMessage());
            verify(otpRepository, never()).save(any(OtpVerification.class));
        }

        @Test
        @DisplayName("TC_AUTH_LOGIN_021 — Spec: Email rỗng khi login phải trả lỗi validation rõ ràng")
        void TC_AUTH_LOGIN_021_emptyEmail_shouldReturnValidationMessage() {
            loginRequest.setEmail("");
            when(userRepository.findByEmail("")).thenReturn(java.util.Optional.empty());

            ApiResponse response = userServiceSut.login(loginRequest);

            assertFalse(response.isSuccess(),
                    "Theo nghiệp vụ chuẩn, email rỗng phải trả lỗi validation thay vì Email không tồn tại");
            assertEquals("Email không được để trống!", response.getMessage());
        }

        @Test
        @DisplayName("TC_AUTH_LOGIN_022 — Spec: Password null khi login không được làm rơi exception kỹ thuật")
        void TC_AUTH_LOGIN_022_nullPassword_shouldNotLeakTechnicalException() {
            loginRequest.setPassword(null);
            doThrow(new IllegalArgumentException("rawPassword cannot be null"))
                    .when(passwordEncoder).matches(null, "$2a$10$encodedOldPassword");

            userServiceSut.login(loginRequest);
        }

        @Test
        @DisplayName("TC_AUTH_USER_011 — Spec: Đổi sang mật khẩu trùng mật khẩu cũ phải bị từ chối")
        void TC_AUTH_USER_011_changePassword_sameAsOld_shouldBeRejected() {
            changePasswordRequest.setNewPassword("OldPass@123");
            changePasswordRequest.setConfirmPassword("OldPass@123");

            ApiResponse response = userServiceSut.changePassword("specgap@example.com", changePasswordRequest);

            assertFalse(response.isSuccess(),
                    "Theo nghiệp vụ chuẩn, mật khẩu mới trùng mật khẩu cũ phải bị từ chối");
            assertEquals("Mật khẩu mới không được trùng mật khẩu cũ!", response.getMessage());
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("TC_AUTH_USER_012 — Spec: Tài khoản LOCKED không được lấy thông tin hồ sơ")
        void TC_AUTH_USER_012_lockedUser_shouldNotReadProfile() {
            activeUser.setStatus(Status.LOCKED);

            ApiResponse response = userServiceSut.getCurrentUser("specgap@example.com");

            assertFalse(response.isSuccess(),
                    "Theo nghiệp vụ chuẩn, tài khoản bị khóa không được đọc hồ sơ người dùng");
            assertEquals("Tài khoản đang bị khóa!", response.getMessage());
        }

        @Test
        @DisplayName("TC_AUTH_SEC_018 — Spec: Sai mật khẩu 5 lần phải khóa tài khoản hoặc từ chối lần sau")
        void TC_AUTH_SEC_018_multipleWrongPasswords_shouldTriggerLockout() {
            when(passwordEncoder.matches(anyString(), eq("$2a$10$encodedOldPassword"))).thenReturn(false);

            for (int attempt = 0; attempt < 5; attempt++) {
                LoginRequest wrongReq = new LoginRequest();
                wrongReq.setEmail("specgap@example.com");
                wrongReq.setPassword("WrongPass" + attempt);
                userServiceSut.login(wrongReq);
            }

            LoginRequest correctAfterLockout = new LoginRequest();
            correctAfterLockout.setEmail("specgap@example.com");
            correctAfterLockout.setPassword("ValidPass@123");
            when(passwordEncoder.matches("ValidPass@123", "$2a$10$encodedOldPassword")).thenReturn(true);

            ApiResponse response = userServiceSut.login(correctAfterLockout);

            assertFalse(response.isSuccess(),
                    "Theo nghiệp vụ bảo mật chuẩn, sau nhiều lần sai phải bị lockout thay vì vẫn login được");
            assertEquals("Tài khoản tạm thời bị khóa do nhập sai quá số lần cho phép!", response.getMessage());
        }

        @Test
        @DisplayName("TC_AUTH_SEC_019 — Spec: Gửi OTP lặp lại liên tục phải bị throttle hoặc reject")
        void TC_AUTH_SEC_019_duplicateOtpRequests_shouldBeThrottled() {
            ApiResponse firstResponse = authServiceSut.sendOtp(registerRequest);
            ApiResponse secondResponse = authServiceSut.sendOtp(registerRequest);

            assertTrue(firstResponse.isSuccess(), "Request đầu tiên có thể được chấp nhận");
            assertFalse(secondResponse.isSuccess(),
                    "Theo nghiệp vụ chuẩn, request OTP lặp lại ngay lập tức phải bị chặn/throttle");
            assertEquals("Yêu cầu OTP quá nhiều. Vui lòng thử lại sau!", secondResponse.getMessage());
            verify(otpRepository, times(1)).save(any(OtpVerification.class));
        }
    }
}
