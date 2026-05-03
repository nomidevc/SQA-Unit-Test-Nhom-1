package com.doan.WEB_TMDT.service;

import com.doan.WEB_TMDT.common.dto.ApiResponse;
import com.doan.WEB_TMDT.module.auth.dto.OtpVerifyRequest;
import com.doan.WEB_TMDT.module.auth.dto.RegisterRequest;
import com.doan.WEB_TMDT.module.auth.entity.OtpVerification;
import com.doan.WEB_TMDT.module.auth.repository.CustomerRepository;
import com.doan.WEB_TMDT.module.auth.repository.OtpRepository;
import com.doan.WEB_TMDT.module.auth.repository.UserRepository;
import com.doan.WEB_TMDT.module.auth.service.UserService;
import com.doan.WEB_TMDT.module.auth.service.impl.AuthServiceImpl;
import com.doan.WEB_TMDT.security.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthServiceImpl — Unit Tests")
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private OtpRepository otpRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private UserService userService;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthServiceImpl authService;

    private static final String ENCODED_PASSWORD = "$2a$10$encodedPassword";

    private RegisterRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = new RegisterRequest();
        validRequest.setEmail("newuser@example.com");
        validRequest.setPassword("ValidPass@123");
        validRequest.setFullName("Nguyễn Văn A");
        validRequest.setPhone("0912345678");
        validRequest.setAddress("123 Lê Lợi, Hà Nội");

        lenient().when(userRepository.existsByEmail(anyString())).thenReturn(false);
        lenient().when(customerRepository.existsByPhone(anyString())).thenReturn(false);
        lenient().when(passwordEncoder.encode(anyString())).thenReturn(ENCODED_PASSWORD);
        lenient().when(otpRepository.save(any(OtpVerification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        Mockito.reset(userRepository, customerRepository, otpRepository,
                passwordEncoder, mailSender, userService, jwtService);
    }

    @Test
    @DisplayName("TC_AUTH_AUTHSERVICE_SENDOTP_001 — Happy path: thông tin hợp lệ → gửi OTP thành công")
    void TC_AUTH_AUTHSERVICE_SENDOTP_001_validRequest_shouldSendOtpSuccessfully() {
        ApiResponse response = authService.sendOtp(validRequest);

        assertTrue(response.isSuccess());
        assertEquals("Mã OTP đã được gửi đến email của bạn!", response.getMessage());
        verify(otpRepository).save(any(OtpVerification.class));
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("TC_AUTH_AUTHSERVICE_SENDOTP_002 — Duplicate email → reject ngay")
    void TC_AUTH_AUTHSERVICE_SENDOTP_002_duplicateEmail_shouldReturnError() {
        when(userRepository.existsByEmail(validRequest.getEmail())).thenReturn(true);

        ApiResponse response = authService.sendOtp(validRequest);

        assertFalse(response.isSuccess());
        assertEquals("Email đã được sử dụng!", response.getMessage());
        verify(otpRepository, never()).save(any(OtpVerification.class));
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("TC_AUTH_AUTHSERVICE_SENDOTP_003 — Duplicate phone → reject ngay")
    void TC_AUTH_AUTHSERVICE_SENDOTP_003_duplicatePhone_shouldReturnError() {
        when(customerRepository.existsByPhone(validRequest.getPhone())).thenReturn(true);

        ApiResponse response = authService.sendOtp(validRequest);

        assertFalse(response.isSuccess());
        assertEquals("Số điện thoại đã tồn tại!", response.getMessage());
        verify(otpRepository, never()).save(any(OtpVerification.class));
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("TC_AUTH_AUTHSERVICE_SENDOTP_004 — DB save lỗi → propagate, không gửi email")
    void TC_AUTH_AUTHSERVICE_SENDOTP_004_systemRepositorySaveThrowsException() {
        when(otpRepository.save(any(OtpVerification.class)))
                .thenThrow(new RuntimeException("Database connection timeout"));

        RuntimeException ex = assertThrows(
                RuntimeException.class,
                () -> authService.sendOtp(validRequest)
        );

        assertEquals("Database connection timeout", ex.getMessage());
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Nested
    @DisplayName("AuthService — Security / Error / Concurrency")
    class SecurityEdgeErrorTests {

        @Test
        @DisplayName("TC_AUTH_AUTHSERVICE_SECURITY_001 — XSS payload được giữ nguyên ở service layer")
        void TC_AUTH_AUTHSERVICE_SECURITY_001_xssPayloadInFullName_storedWithoutEscape() {
            String xssPayload = "<script>alert('XSS')</script>";
            validRequest.setFullName(xssPayload);

            ArgumentCaptor<OtpVerification> otpCaptor = ArgumentCaptor.forClass(OtpVerification.class);

            ApiResponse response = authService.sendOtp(validRequest);

            assertTrue(response.isSuccess());
            verify(otpRepository).save(otpCaptor.capture());
            assertEquals(xssPayload, otpCaptor.getValue().getFullName());
        }

        @Test
        @DisplayName("TC_AUTH_AUTHSERVICE_SECURITY_002 — SMTP lỗi → trả error sau khi đã lưu OTP")
        void TC_AUTH_AUTHSERVICE_SECURITY_002_externalServiceFailure_smtpDown() {
            doThrow(new MailSendException("SMTP connection refused"))
                    .when(mailSender).send(any(SimpleMailMessage.class));

            ApiResponse response = authService.sendOtp(validRequest);

            assertFalse(response.isSuccess());
            assertEquals("Không thể gửi email OTP. Vui lòng thử lại sau!", response.getMessage());
            verify(otpRepository).save(any(OtpVerification.class));
        }

        @Test
        @DisplayName("TC_AUTH_AUTHSERVICE_SECURITY_003 — 5 request sendOtp liên tiếp đều được xử lý")
        void TC_AUTH_AUTHSERVICE_SECURITY_003_multipleSimultaneousSendOtp() {
            int successCount = 0;

            for (int i = 0; i < 5; i++) {
                ApiResponse response = authService.sendOtp(validRequest);
                if (response.isSuccess()) {
                    successCount++;
                }
            }

            assertEquals(5, successCount);
            verify(otpRepository, times(5)).save(any(OtpVerification.class));
            verify(mailSender, times(5)).send(any(SimpleMailMessage.class));
        }
    }

    @Nested
    @DisplayName("AuthService — verifyOtpAndRegister")
    class VerifyOtpAndRegisterTests {

        private OtpVerifyRequest verifyRequest;
        private OtpVerification otpVerification;

        @BeforeEach
        void setUpVerifyOtp() {
            verifyRequest = new OtpVerifyRequest();
            verifyRequest.setEmail("newuser@example.com");
            verifyRequest.setOtpCode("123456");

            otpVerification = OtpVerification.builder()
                    .email("newuser@example.com")
                    .otpCode("123456")
                    .fullName("Nguyễn Văn A")
                    .phone("0912345678")
                    .address("123 Lê Lợi, Hà Nội")
                    .encodedPassword(ENCODED_PASSWORD)
                    .verified(false)
                    .createdAt(LocalDateTime.now())
                    .expiresAt(LocalDateTime.now().plusMinutes(5))
                    .build();

            lenient().when(otpRepository.findByEmailAndOtpCode("newuser@example.com", "123456"))
                    .thenReturn(java.util.Optional.of(otpVerification));
            lenient().when(userService.registerCustomer(
                    otpVerification.getEmail(),
                    otpVerification.getEncodedPassword(),
                    otpVerification.getFullName(),
                    otpVerification.getPhone(),
                    otpVerification.getAddress()))
                    .thenReturn(ApiResponse.success("Tạo tài khoản khách hàng thành công!", new Object()));
        }

        @Test
        @DisplayName("TC_AUTH_AUTHSERVICE_VERIFYOTP_001 — OTP hợp lệ → verify thành công và tạo tài khoản")
        void TC_AUTH_AUTHSERVICE_VERIFYOTP_001_validOtp_shouldVerifyAndRegister() {
            ApiResponse response = authService.verifyOtpAndRegister(verifyRequest);

            assertTrue(response.isSuccess());
            assertEquals("Xác minh OTP thành công, tài khoản đã được tạo!", response.getMessage());
            assertTrue(otpVerification.isVerified());
            verify(otpRepository).save(otpVerification);
            verify(userService).registerCustomer(
                    otpVerification.getEmail(),
                    otpVerification.getEncodedPassword(),
                    otpVerification.getFullName(),
                    otpVerification.getPhone(),
                    otpVerification.getAddress());
        }

        @Test
        @DisplayName("TC_AUTH_AUTHSERVICE_VERIFYOTP_002 — Không tìm thấy OTP → trả lỗi mã OTP không hợp lệ")
        void TC_AUTH_AUTHSERVICE_VERIFYOTP_002_missingOtp_shouldReturnInvalidOtpError() {
            when(otpRepository.findByEmailAndOtpCode("newuser@example.com", "123456"))
                    .thenReturn(java.util.Optional.empty());

            ApiResponse response = authService.verifyOtpAndRegister(verifyRequest);

            assertFalse(response.isSuccess());
            assertEquals("Mã OTP không hợp lệ!", response.getMessage());
            verify(otpRepository, never()).save(any(OtpVerification.class));
            verify(userService, never()).registerCustomer(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("TC_AUTH_AUTHSERVICE_VERIFYOTP_003 — OTP đã verify trước đó → trả lỗi")
        void TC_AUTH_AUTHSERVICE_VERIFYOTP_003_verifiedOtp_shouldReturnAlreadyVerifiedError() {
            otpVerification.setVerified(true);

            ApiResponse response = authService.verifyOtpAndRegister(verifyRequest);

            assertFalse(response.isSuccess());
            assertEquals("Mã OTP này đã được xác minh!", response.getMessage());
            verify(otpRepository, never()).save(any(OtpVerification.class));
            verify(userService, never()).registerCustomer(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("TC_AUTH_AUTHSERVICE_VERIFYOTP_004 — OTP hết hạn → trả lỗi")
        void TC_AUTH_AUTHSERVICE_VERIFYOTP_004_expiredOtp_shouldReturnExpiredError() {
            otpVerification.setExpiresAt(LocalDateTime.now().minusSeconds(1));

            ApiResponse response = authService.verifyOtpAndRegister(verifyRequest);

            assertFalse(response.isSuccess());
            assertEquals("Mã OTP đã hết hạn!", response.getMessage());
            verify(otpRepository, never()).save(any(OtpVerification.class));
            verify(userService, never()).registerCustomer(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("TC_AUTH_AUTHSERVICE_VERIFYOTP_005 — registerCustomer thất bại → propagate ApiResponse lỗi")
        void TC_AUTH_AUTHSERVICE_VERIFYOTP_005_registerCustomerFails_shouldReturnOriginalError() {
            ApiResponse failure = ApiResponse.error("Email đã tồn tại!");
            when(userService.registerCustomer(any(), any(), any(), any(), any())).thenReturn(failure);

            ApiResponse response = authService.verifyOtpAndRegister(verifyRequest);

            assertFalse(response.isSuccess());
            assertEquals("Email đã tồn tại!", response.getMessage());
            assertTrue(otpVerification.isVerified());
            verify(otpRepository).save(otpVerification);
        }
    }

    @Nested
    @DisplayName("AuthService — Spec Gap / Defect Evidence")
    class SpecGapDefectEvidenceTests {

        @Test
        @DisplayName("TC_AUTH_AUTHSERVICE_SENDOTP_SPEC_001 — Email sai định dạng phải bị từ chối theo nghiệp vụ")
        void TC_AUTH_AUTHSERVICE_SENDOTP_SPEC_001_invalidEmailFormat_shouldBeRejectedByBusinessRule() {
            validRequest.setEmail("not-an-email");

            ApiResponse response = authService.sendOtp(validRequest);

            assertFalse(response.isSuccess());
            assertEquals("Email không đúng định dạng!", response.getMessage());
            verify(otpRepository, never()).save(any(OtpVerification.class));
            verify(mailSender, never()).send(any(SimpleMailMessage.class));
        }

        @Test
        @DisplayName("TC_AUTH_AUTHSERVICE_SENDOTP_SPEC_002 — Số điện thoại trống phải bị từ chối theo nghiệp vụ")
        void TC_AUTH_AUTHSERVICE_SENDOTP_SPEC_002_emptyPhone_shouldNotCreateOtpRecord() {
            validRequest.setPhone("");
            when(customerRepository.existsByPhone("")).thenReturn(false);

            ApiResponse response = authService.sendOtp(validRequest);

            assertFalse(response.isSuccess());
            assertEquals("Số điện thoại không được để trống!", response.getMessage());
            verify(otpRepository, never()).save(any(OtpVerification.class));
        }

        @Test
        @DisplayName("TC_AUTH_AUTHSERVICE_SENDOTP_SPEC_003 — OTP lặp lại liên tục phải bị throttle theo nghiệp vụ")
        void TC_AUTH_AUTHSERVICE_SENDOTP_SPEC_003_duplicateOtpRequests_shouldBeThrottled() {
            ApiResponse firstResponse = authService.sendOtp(validRequest);
            ApiResponse secondResponse = authService.sendOtp(validRequest);

            assertTrue(firstResponse.isSuccess());
            assertFalse(secondResponse.isSuccess());
            assertEquals("Yêu cầu OTP quá nhiều. Vui lòng thử lại sau!", secondResponse.getMessage());
            verify(otpRepository, times(1)).save(any(OtpVerification.class));
        }
    }
}
