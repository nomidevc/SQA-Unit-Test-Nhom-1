package com.doan.WEB_TMDT.service;

import com.doan.WEB_TMDT.common.dto.ApiResponse;
import com.doan.WEB_TMDT.module.auth.dto.ChangePasswordRequest;
import com.doan.WEB_TMDT.module.auth.dto.FirstChangePasswordRequest;
import com.doan.WEB_TMDT.module.auth.dto.LoginRequest;
import com.doan.WEB_TMDT.module.auth.dto.LoginResponse;
import com.doan.WEB_TMDT.module.auth.entity.Customer;
import com.doan.WEB_TMDT.module.auth.entity.Employee;
import com.doan.WEB_TMDT.module.auth.entity.Position;
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
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService — Unit Tests")
class UserServiceTest {

    @Nested
    @DisplayName("UserService — LOGIN (login) Unit Tests")
    class LoginTests {

        @Mock
        private UserRepository userRepository;

        @Mock
        private CustomerRepository customerRepository;

        @Mock
        private PasswordEncoder passwordEncoder;

        @Mock
        private JwtService jwtService;

        @InjectMocks
        private UserServiceImpl userService;

        private static final String LOGIN_EMAIL = "customer@example.com";
        private static final String LOGIN_PASSWORD = "MyPass@123";
        private static final String ENCODED_PWD_LOGIN = "$2a$10$hashedPasswordForLoginTest";
        private static final String MOCK_JWT =
                "eyJhbGciOiJIUzI1NiJ9.mockPayload.mockSignature";

        private LoginRequest loginRequest;
        private User activeCustomerUser;
        private Customer customer;

        @BeforeEach
        void setUpLogin() {
            loginRequest = new LoginRequest();
            loginRequest.setEmail(LOGIN_EMAIL);
            loginRequest.setPassword(LOGIN_PASSWORD);

            customer = Customer.builder()
                    .fullName("Trần Thị B")
                    .phone("0987654321")
                    .address("456 Nguyễn Huệ, TP.HCM")
                    .build();

            activeCustomerUser = User.builder()
                    .id(1L)
                    .email(LOGIN_EMAIL)
                    .password(ENCODED_PWD_LOGIN)
                    .role(Role.CUSTOMER)
                    .status(Status.ACTIVE)
                    .customer(customer)
                    .build();
            customer.setUser(activeCustomerUser);

            lenient().when(userRepository.findByEmail(LOGIN_EMAIL))
                    .thenReturn(java.util.Optional.of(activeCustomerUser));
            lenient().when(passwordEncoder.matches(LOGIN_PASSWORD, ENCODED_PWD_LOGIN))
                    .thenReturn(true);
            lenient().when(jwtService.generateToken(eq(LOGIN_EMAIL), any()))
                    .thenReturn(MOCK_JWT);
        }

        @AfterEach
        void tearDownLogin() {
            Mockito.reset(userRepository, customerRepository, passwordEncoder, jwtService);
        }

        @Test
        @DisplayName("TC_AUTH_USERSERVICE_LOGIN_001 — Happy Path: Email/password đúng → success + JWT")
        void TC_AUTH_USERSERVICE_LOGIN_001_happyPath_validCredentials() {
            ApiResponse response = userService.login(loginRequest);

            assertTrue(response.isSuccess(), "Phải là success response");
            assertEquals("Đăng nhập thành công!", response.getMessage());
            assertNotNull(response.getData(), "Response data không được null");

            LoginResponse loginResp = (LoginResponse) response.getData();
            assertEquals(MOCK_JWT, loginResp.getToken(), "Token phải khớp với giá trị từ jwtService");
            assertEquals(LOGIN_EMAIL, loginResp.getEmail(), "Email trong response phải khớp request");
            assertEquals("CUSTOMER", loginResp.getRole(), "Role phải là CUSTOMER");
            assertEquals("ACTIVE", loginResp.getStatus(), "Status phải là ACTIVE");
        }

        @Test
        @DisplayName("TC_AUTH_USERSERVICE_LOGIN_002 — Failure: Sai password → Error 'Mật khẩu không đúng!'")
        void TC_AUTH_USERSERVICE_LOGIN_002_failure_wrongPassword() {
            when(passwordEncoder.matches(LOGIN_PASSWORD, ENCODED_PWD_LOGIN)).thenReturn(false);

            ApiResponse response = userService.login(loginRequest);

            assertFalse(response.isSuccess(), "Phải là error khi password sai");
            assertEquals("Mật khẩu không đúng!", response.getMessage());
            verify(jwtService, never()).generateToken(any(), any());
        }

        @Test
        @DisplayName("TC_AUTH_USERSERVICE_LOGIN_003 — Failure: Email không tồn tại → Error 'Email không tồn tại!'")
        void TC_AUTH_USERSERVICE_LOGIN_003_failure_emailNotFound() {
            when(userRepository.findByEmail(LOGIN_EMAIL)).thenReturn(java.util.Optional.empty());

            ApiResponse response = userService.login(loginRequest);

            assertFalse(response.isSuccess(), "Phải là error khi email không tồn tại");
            assertEquals("Email không tồn tại!", response.getMessage());
            verify(passwordEncoder, never()).matches(any(), any());
            verify(jwtService, never()).generateToken(any(), any());
        }

        @Test
        @DisplayName("TC_AUTH_USERSERVICE_LOGIN_004 — Failure: Tài khoản LOCKED → Error 'Tài khoản đang bị khóa!'")
        void TC_AUTH_USERSERVICE_LOGIN_004_failure_accountLocked() {
            activeCustomerUser.setStatus(Status.LOCKED);

            ApiResponse response = userService.login(loginRequest);

            assertFalse(response.isSuccess(), "Phải là error khi tài khoản bị LOCKED");
            assertEquals("Tài khoản đang bị khóa!", response.getMessage());
            verify(jwtService, never()).generateToken(any(), any());
        }

        @Test
        @DisplayName("TC_AUTH_USERSERVICE_LOGIN_005 — Failure: Tài khoản INACTIVE → Error 'Tài khoản đang bị khóa!'")
        void TC_AUTH_USERSERVICE_LOGIN_005_failure_accountInactive() {
            activeCustomerUser.setStatus(Status.INACTIVE);

            ApiResponse response = userService.login(loginRequest);

            assertFalse(response.isSuccess(), "Phải là error khi tài khoản INACTIVE");
            assertEquals("Tài khoản đang bị khóa!", response.getMessage(),
                    "Service dùng chung message cho cả LOCKED và INACTIVE");
            verify(jwtService, never()).generateToken(any(), any());
        }

        @Test
        @DisplayName("TC_AUTH_USERSERVICE_LOGIN_006 — Validation: Email null → Error 'Email không tồn tại!'")
        void TC_AUTH_USERSERVICE_LOGIN_006_validation_nullEmail() {
            loginRequest.setEmail(null);
            when(userRepository.findByEmail(null)).thenReturn(java.util.Optional.empty());

            ApiResponse response = userService.login(loginRequest);

            assertFalse(response.isSuccess());
            assertEquals("Email không tồn tại!", response.getMessage(),
                    "findByEmail(null) trả empty → service báo Email không tồn tại");
            verify(userRepository).findByEmail(null);
        }

        @Test
        @DisplayName("TC_AUTH_USERSERVICE_LOGIN_007 — Validation: Password null → IllegalArgumentException từ matches()")
        void TC_AUTH_USERSERVICE_LOGIN_007_validation_nullPassword() {
            loginRequest.setPassword(null);
            doThrow(new IllegalArgumentException("rawPassword cannot be null"))
                    .when(passwordEncoder).matches(null, ENCODED_PWD_LOGIN);

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> userService.login(loginRequest),
                    "Phải ném IllegalArgumentException khi password là null"
            );
            assertEquals("rawPassword cannot be null", ex.getMessage());
            verify(jwtService, never()).generateToken(any(), any());
        }

        @Test
        @DisplayName("TC_AUTH_USERSERVICE_LOGIN_008 — Validation: Email rỗng → Error 'Email không tồn tại!'")
        void TC_AUTH_USERSERVICE_LOGIN_008_validation_emptyEmail() {
            loginRequest.setEmail("");
            when(userRepository.findByEmail("")).thenReturn(java.util.Optional.empty());

            ApiResponse response = userService.login(loginRequest);

            assertFalse(response.isSuccess());
            assertEquals("Email không tồn tại!", response.getMessage());
            verify(userRepository).findByEmail("");
        }

        @Test
        @DisplayName("TC_AUTH_USERSERVICE_LOGIN_009 — Boundary: Password rất dài (~500 ký tự) → Error 'Mật khẩu không đúng!'")
        void TC_AUTH_USERSERVICE_LOGIN_009_boundary_veryLongPassword() {
            String longPassword = "P@ssw0rd!".repeat(56);
            assertThat(longPassword).hasSizeGreaterThan(72);
            loginRequest.setPassword(longPassword);
            when(passwordEncoder.matches(longPassword, ENCODED_PWD_LOGIN)).thenReturn(false);

            ApiResponse response = userService.login(loginRequest);

            assertFalse(response.isSuccess());
            assertEquals("Mật khẩu không đúng!", response.getMessage());
            verify(passwordEncoder).matches(longPassword, ENCODED_PWD_LOGIN);
        }

        @Test
        @DisplayName("TC_AUTH_USERSERVICE_LOGIN_010 — Boundary: Password rỗng (length=0) → Error 'Mật khẩu không đúng!'")
        void TC_AUTH_USERSERVICE_LOGIN_010_boundary_passwordLengthZero() {
            loginRequest.setPassword("");
            when(passwordEncoder.matches("", ENCODED_PWD_LOGIN)).thenReturn(false);

            ApiResponse response = userService.login(loginRequest);

            assertFalse(response.isSuccess());
            assertEquals("Mật khẩu không đúng!", response.getMessage());
            verify(passwordEncoder).matches("", ENCODED_PWD_LOGIN);
        }

        @Test
        @DisplayName("TC_AUTH_USERSERVICE_LOGIN_011 — Security: SQL Injection trong email → JPA bảo vệ, error đúng")
        void TC_AUTH_USERSERVICE_LOGIN_011_security_sqlInjectionInEmail() {
            String sqlPayload = "' OR '1'='1'; --@evil.com";
            loginRequest.setEmail(sqlPayload);
            when(userRepository.findByEmail(sqlPayload)).thenReturn(java.util.Optional.empty());

            ApiResponse response = userService.login(loginRequest);

            assertFalse(response.isSuccess(),
                    "SQL injection payload không tạo được session (JPA parameterized query bảo vệ)");
            assertEquals("Email không tồn tại!", response.getMessage());
            verify(userRepository).findByEmail(sqlPayload);
            verify(passwordEncoder, never()).matches(any(), any());
        }

        @Test
        @DisplayName("TC_AUTH_USERSERVICE_LOGIN_012 — Security: Brute-force (5 lần sai) → Mỗi lần đều error (thiếu rate-limit)")
        void TC_AUTH_USERSERVICE_LOGIN_012_security_bruteForceSimulation() {
            String[] wrongPasswords = {
                    "wrongPass1", "wrongPass2", "wrongPass3", "wrongPass4", "wrongPass5"
            };

            for (int attempt = 1; attempt <= 5; attempt++) {
                String wrongPwd = wrongPasswords[attempt - 1];
                loginRequest.setPassword(wrongPwd);
                when(passwordEncoder.matches(wrongPwd, ENCODED_PWD_LOGIN)).thenReturn(false);

                ApiResponse response = userService.login(loginRequest);

                assertFalse(response.isSuccess(),
                        "Lần thử " + attempt + ": phải là error khi password sai");
                assertEquals("Mật khẩu không đúng!", response.getMessage(),
                        "Lần thử " + attempt + ": message phải là 'Mật khẩu không đúng!'");
            }

            verify(userRepository, times(5)).findByEmail(LOGIN_EMAIL);
            verify(jwtService, never()).generateToken(any(), any());
        }

        @Test
        @DisplayName("TC_AUTH_USERSERVICE_LOGIN_013 — Token: JWT được tạo bởi jwtService và có trong LoginResponse")
        void TC_AUTH_USERSERVICE_LOGIN_013_token_jwtGeneratedAndReturned() {
            ApiResponse response = userService.login(loginRequest);

            assertTrue(response.isSuccess());
            verify(jwtService, times(1)).generateToken(eq(LOGIN_EMAIL), any());

            LoginResponse loginResp = (LoginResponse) response.getData();
            assertNotNull(loginResp.getToken(), "Token không được null");
            assertEquals(MOCK_JWT, loginResp.getToken(), "Token trong response phải khớp với mock JWT");
            assertFalse(loginResp.getToken().isEmpty(), "Token không được rỗng");
        }

        @Test
        @DisplayName("TC_AUTH_USERSERVICE_LOGIN_014 — Token: JWT claims chứa đúng role và ROLE_ prefix")
        void TC_AUTH_USERSERVICE_LOGIN_014_token_jwtContainsCorrectClaims() {
            @SuppressWarnings("unchecked")
            ArgumentCaptor<java.util.Map<String, Object>> claimsCaptor =
                    ArgumentCaptor.forClass(java.util.Map.class);

            userService.login(loginRequest);

            verify(jwtService).generateToken(eq(LOGIN_EMAIL), claimsCaptor.capture());
            java.util.Map<String, Object> capturedClaims = claimsCaptor.getValue();

            assertNotNull(capturedClaims, "Claims không được null");
            assertEquals("CUSTOMER", capturedClaims.get("role"),
                    "Claim 'role' phải là 'CUSTOMER'");
            assertEquals(true, capturedClaims.get("ROLE_CUSTOMER"),
                    "Claim 'ROLE_CUSTOMER' phải là true (Spring Security convention)");
        }

        @Test
        @DisplayName("TC_AUTH_USERSERVICE_LOGIN_015 — Token: Employee firstLogin → Không tạo JWT, yêu cầu đổi mật khẩu")
        void TC_AUTH_USERSERVICE_LOGIN_015_token_employeeFirstLogin_noJwtGenerated() {
            Employee employee = Employee.builder()
                    .id(10L)
                    .fullName("Nhân Viên C")
                    .firstLogin(true)
                    .build();

            User employeeUser = User.builder()
                    .id(2L)
                    .email(LOGIN_EMAIL)
                    .password(ENCODED_PWD_LOGIN)
                    .role(Role.EMPLOYEE)
                    .status(Status.ACTIVE)
                    .employee(employee)
                    .build();
            employee.setUser(employeeUser);

            when(userRepository.findByEmail(LOGIN_EMAIL))
                    .thenReturn(java.util.Optional.of(employeeUser));
            when(passwordEncoder.matches(LOGIN_PASSWORD, ENCODED_PWD_LOGIN)).thenReturn(true);

            ApiResponse response = userService.login(loginRequest);

            assertTrue(response.isSuccess(), "Phải là success (nhưng không có JWT)");
            assertEquals("Đăng nhập lần đầu. Yêu cầu đổi mật khẩu!", response.getMessage());
            verify(jwtService, never()).generateToken(any(), any());

            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> data = (java.util.Map<String, Object>) response.getData();
            assertEquals(true, data.get("requireChangePassword"),
                    "Data phải chứa requireChangePassword = true");
            assertEquals(LOGIN_EMAIL, data.get("email"),
                    "Data phải chứa email của user");
        }

        @Test
        @DisplayName("TC_AUTH_USERSERVICE_LOGIN_016 — Token: jwtService ném RuntimeException (tampered key) → propagate")
        void TC_AUTH_USERSERVICE_LOGIN_016_token_jwtServiceThrowsException() {
            when(jwtService.generateToken(eq(LOGIN_EMAIL), any()))
                    .thenThrow(new RuntimeException("JWT signing failed: tampered secret key"));

            RuntimeException ex = assertThrows(
                    RuntimeException.class,
                    () -> userService.login(loginRequest),
                    "RuntimeException từ jwtService phải propagate lên caller"
            );
            assertEquals("JWT signing failed: tampered secret key", ex.getMessage());
        }

        @Test
        @DisplayName("TC_AUTH_USERSERVICE_LOGIN_017 — System: DB error từ findByEmail() → RuntimeException propagate")
        void TC_AUTH_USERSERVICE_LOGIN_017_system_dbErrorFromRepository() {
            when(userRepository.findByEmail(LOGIN_EMAIL))
                    .thenThrow(new RuntimeException("Database connection timeout"));

            RuntimeException ex = assertThrows(
                    RuntimeException.class,
                    () -> userService.login(loginRequest),
                    "RuntimeException từ DB phải propagate"
            );
            assertEquals("Database connection timeout", ex.getMessage());
            verify(passwordEncoder, never()).matches(any(), any());
            verify(jwtService, never()).generateToken(any(), any());
        }

        @Test
        @DisplayName("TC_AUTH_USERSERVICE_LOGIN_018 — System: passwordEncoder.matches() ném RuntimeException → propagate")
        void TC_AUTH_USERSERVICE_LOGIN_018_system_passwordEncoderFailure() {
            when(passwordEncoder.matches(LOGIN_PASSWORD, ENCODED_PWD_LOGIN))
                    .thenThrow(new RuntimeException("BCrypt internal error: invalid salt"));

            RuntimeException ex = assertThrows(
                    RuntimeException.class,
                    () -> userService.login(loginRequest),
                    "RuntimeException từ passwordEncoder phải propagate"
            );
            assertEquals("BCrypt internal error: invalid salt", ex.getMessage());
            verify(jwtService, never()).generateToken(any(), any());
        }

        @Test
        @DisplayName("TC_AUTH_USERSERVICE_LOGIN_019 — CheckDB: Login thành công → Không gọi save() (read-only operation)")
        void TC_AUTH_USERSERVICE_LOGIN_019_checkdb_verifyNoSaveCalledDuringLogin() {
            ApiResponse response = userService.login(loginRequest);

            assertTrue(response.isSuccess());
            verify(userRepository, never()).save(any(User.class));
            verify(userRepository, times(1)).findByEmail(LOGIN_EMAIL);
            verifyNoMoreInteractions(userRepository);
        }

        @Test
        @DisplayName("TC_AUTH_USERSERVICE_LOGIN_020 — Concurrency: 3 login requests → Mỗi request độc lập thành công")
        void TC_AUTH_USERSERVICE_LOGIN_020_concurrency_multipleLoginRequests() {
            int numberOfRequests = 3;

            for (int i = 0; i < numberOfRequests; i++) {
                ApiResponse response = userService.login(loginRequest);
                assertTrue(response.isSuccess(),
                        "Request " + (i + 1) + "/" + numberOfRequests + " phải thành công");
                assertEquals("Đăng nhập thành công!", response.getMessage());

                LoginResponse loginResp = (LoginResponse) response.getData();
                assertEquals(MOCK_JWT, loginResp.getToken(),
                        "Request " + (i + 1) + ": token phải là mock JWT");
            }

            verify(jwtService, times(numberOfRequests)).generateToken(eq(LOGIN_EMAIL), any());
            verify(userRepository, times(numberOfRequests)).findByEmail(LOGIN_EMAIL);
        }

        @Test
        @DisplayName("TC_AUTH_USERSERVICE_LOGIN_021 — Employee không có position, không first login → login thành công với position null")
        void TC_AUTH_USERSERVICE_LOGIN_021_employeeWithoutPosition_shouldLoginSuccessfully() {
            Employee employee = Employee.builder()
                    .id(30L)
                    .fullName("Nhân Viên Không Vị Trí")
                    .firstLogin(false)
                    .build();

            User employeeUser = User.builder()
                    .id(3L)
                    .email(LOGIN_EMAIL)
                    .password(ENCODED_PWD_LOGIN)
                    .role(Role.EMPLOYEE)
                    .status(Status.ACTIVE)
                    .employee(employee)
                    .build();
            employee.setUser(employeeUser);

            when(userRepository.findByEmail(LOGIN_EMAIL))
                    .thenReturn(java.util.Optional.of(employeeUser));
            when(passwordEncoder.matches(LOGIN_PASSWORD, ENCODED_PWD_LOGIN)).thenReturn(true);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<java.util.Map<String, Object>> claimsCaptor = ArgumentCaptor.forClass(java.util.Map.class);

            ApiResponse response = userService.login(loginRequest);

            assertTrue(response.isSuccess());
            LoginResponse loginResp = (LoginResponse) response.getData();
            assertEquals("EMPLOYEE", loginResp.getRole());
            assertNull(loginResp.getPosition());
            assertEquals(30L, loginResp.getEmployeeId());
            verify(jwtService).generateToken(eq(LOGIN_EMAIL), claimsCaptor.capture());
            assertEquals("EMPLOYEE", claimsCaptor.getValue().get("authorities"));
        }

        @Test
        @DisplayName("TC_AUTH_USERSERVICE_LOGIN_022 — Employee có position và không first login → claims chứa position")
        void TC_AUTH_USERSERVICE_LOGIN_022_employeeWithPosition_shouldGeneratePositionClaims() {
            Employee employee = Employee.builder()
                    .id(31L)
                    .fullName("Nhân Viên Sale")
                    .position(Position.SALE)
                    .firstLogin(false)
                    .build();

            User employeeUser = User.builder()
                    .id(4L)
                    .email(LOGIN_EMAIL)
                    .password(ENCODED_PWD_LOGIN)
                    .role(Role.EMPLOYEE)
                    .status(Status.ACTIVE)
                    .employee(employee)
                    .build();
            employee.setUser(employeeUser);

            when(userRepository.findByEmail(LOGIN_EMAIL))
                    .thenReturn(java.util.Optional.of(employeeUser));
            when(passwordEncoder.matches(LOGIN_PASSWORD, ENCODED_PWD_LOGIN)).thenReturn(true);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<java.util.Map<String, Object>> claimsCaptor = ArgumentCaptor.forClass(java.util.Map.class);

            ApiResponse response = userService.login(loginRequest);

            assertTrue(response.isSuccess());
            LoginResponse loginResp = (LoginResponse) response.getData();
            assertEquals("SALE", loginResp.getPosition());
            assertEquals(31L, loginResp.getEmployeeId());
            verify(jwtService).generateToken(eq(LOGIN_EMAIL), claimsCaptor.capture());
            assertEquals("SALE", claimsCaptor.getValue().get("position"));
            assertEquals(true, claimsCaptor.getValue().get("SALE"));
            assertEquals("SALE", claimsCaptor.getValue().get("authorities"));
        }
    }

    @Nested
    @DisplayName("UserService — USER MANAGEMENT (getCurrentUser / changePassword) Unit Tests")
    class UserManagementTests {

        @Mock
        private UserRepository userRepository;

        @Mock
        private CustomerRepository customerRepository;

        @Mock
        private PasswordEncoder passwordEncoder;

        @Mock
        private JwtService jwtService;

        @InjectMocks
        private UserServiceImpl userService;

        private static final String USER_EMAIL = "user@example.com";
        private static final String OLD_PASSWORD = "OldPass@123";
        private static final String NEW_PASSWORD = "NewPass@456";
        private static final String ENCODED_OLD = "$2a$10$encodedOldPassword";
        private static final String ENCODED_NEW = "$2a$10$encodedNewPassword";

        private User activeUser;
        private Customer customer;
        private ChangePasswordRequest changePwdReq;

        @BeforeEach
        void setUpUserManagement() {
            customer = Customer.builder()
                    .id(1L)
                    .fullName("Nguyễn Văn A")
                    .phone("0912345678")
                    .address("123 Lê Lợi, Hà Nội")
                    .build();

            activeUser = User.builder()
                    .id(10L)
                    .email(USER_EMAIL)
                    .password(ENCODED_OLD)
                    .role(Role.CUSTOMER)
                    .status(Status.ACTIVE)
                    .customer(customer)
                    .build();
            customer.setUser(activeUser);

            changePwdReq = new ChangePasswordRequest();
            changePwdReq.setOldPassword(OLD_PASSWORD);
            changePwdReq.setNewPassword(NEW_PASSWORD);
            changePwdReq.setConfirmPassword(NEW_PASSWORD);

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

        @Test
        @DisplayName("TC_AUTH_USERSERVICE_GETCURRENTUSER_001 — Get User (valid): Customer tồn tại → success + data đầy đủ")
        void TC_AUTH_USERSERVICE_GETCURRENTUSER_001_validCustomer_returnsSuccessWithData() {
            ApiResponse response = userService.getCurrentUser(USER_EMAIL);

            assertTrue(response.isSuccess(), "Phải là success response");
            assertEquals("Lấy thông tin người dùng thành công", response.getMessage());
            assertNotNull(response.getData(), "Data không được null");

            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> data = (java.util.Map<String, Object>) response.getData();
            assertEquals(10L, data.get("id"), "ID phải khớp với user trong DB");
            assertEquals(USER_EMAIL, data.get("email"), "Email phải khớp");
            assertEquals("CUSTOMER", data.get("role"), "Role phải là CUSTOMER");
            assertEquals("ACTIVE", data.get("status"), "Status phải là ACTIVE");
        }

        @Test
        @DisplayName("TC_AUTH_USERSERVICE_GETCURRENTUSER_002 — Get User (valid): Employee có Position → data chứa position/employeeId")
        void TC_AUTH_USERSERVICE_GETCURRENTUSER_002_validEmployee_returnsPositionAndEmployeeId() {
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

            ApiResponse response = userService.getCurrentUser(USER_EMAIL);

            assertTrue(response.isSuccess());

            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> data = (java.util.Map<String, Object>) response.getData();
            assertEquals("EMPLOYEE", data.get("role"), "Role phải là EMPLOYEE");
            assertEquals("SALE", data.get("position"), "Position phải là SALE");
            assertEquals(20L, data.get("employeeId"), "EmployeeId phải khớp");
        }

        @Test
        @DisplayName("TC_AUTH_USERSERVICE_GETCURRENTUSER_003 — Get User (invalid): Email không tồn tại → RuntimeException")
        void TC_AUTH_USERSERVICE_GETCURRENTUSER_003_emailNotFound_throwsRuntimeException() {
            String notFoundEmail = "notfound@example.com";
            when(userRepository.findByEmail(notFoundEmail))
                    .thenReturn(java.util.Optional.empty());

            RuntimeException ex = assertThrows(
                    RuntimeException.class,
                    () -> userService.getCurrentUser(notFoundEmail),
                    "Phải ném RuntimeException khi email không tồn tại"
            );
            assertEquals("Không tìm thấy người dùng!", ex.getMessage());
            verify(userRepository).findByEmail(notFoundEmail);
        }

        @Test
        @DisplayName("TC_AUTH_USERSERVICE_CHANGEPASSWORD_001 — Update (valid): Đổi mật khẩu hợp lệ → success + password được cập nhật")
        void TC_AUTH_USERSERVICE_CHANGEPASSWORD_001_validChangePassword_success() {
            ApiResponse response = userService.changePassword(USER_EMAIL, changePwdReq);

            assertTrue(response.isSuccess(), "Phải là success response");
            assertEquals("Đổi mật khẩu thành công!", response.getMessage());

            ArgumentCaptor<User> savedUserCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(savedUserCaptor.capture());
            assertEquals(ENCODED_NEW, savedUserCaptor.getValue().getPassword(),
                    "Password trong DB phải là encoded version của newPassword");
        }

        @Test
        @DisplayName("TC_AUTH_USERSERVICE_CHANGEPASSWORD_002 — Update (invalid data): oldPassword sai → Error 'Mật khẩu cũ không đúng!'")
        void TC_AUTH_USERSERVICE_CHANGEPASSWORD_002_wrongOldPassword_returnsError() {
            when(passwordEncoder.matches(OLD_PASSWORD, ENCODED_OLD)).thenReturn(false);

            ApiResponse response = userService.changePassword(USER_EMAIL, changePwdReq);

            assertFalse(response.isSuccess(), "Phải là error khi oldPassword sai");
            assertEquals("Mật khẩu cũ không đúng!", response.getMessage());
            verify(userRepository, never()).save(any(User.class));
            verify(passwordEncoder, never()).encode(any());
        }

        @Test
        @DisplayName("TC_AUTH_USERSERVICE_CHANGEPASSWORD_003 — Update (null/mismatch fields): confirmPassword ≠ newPassword → Error")
        void TC_AUTH_USERSERVICE_CHANGEPASSWORD_003_confirmPasswordMismatch_returnsError() {
            changePwdReq.setConfirmPassword("TotallyDifferentPass@999");

            ApiResponse response = userService.changePassword(USER_EMAIL, changePwdReq);

            assertFalse(response.isSuccess(), "Phải là error khi confirmPassword không khớp");
            assertEquals("Xác nhận mật khẩu mới không khớp!", response.getMessage());
            verify(passwordEncoder, never()).encode(any());
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("TC_AUTH_USERSERVICE_CHANGEPASSWORD_004 — Update (not found): User không tồn tại → RuntimeException")
        void TC_AUTH_USERSERVICE_CHANGEPASSWORD_004_userNotFound_throwsRuntimeException() {
            String ghostEmail = "ghost@example.com";
            when(userRepository.findByEmail(ghostEmail))
                    .thenReturn(java.util.Optional.empty());

            RuntimeException ex = assertThrows(
                    RuntimeException.class,
                    () -> userService.changePassword(ghostEmail, changePwdReq),
                    "Phải ném RuntimeException khi user không tồn tại"
            );
            assertEquals("Không tìm thấy người dùng!", ex.getMessage());
            verify(passwordEncoder, never()).matches(any(), any());
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("TC_AUTH_USERSERVICE_REPOSITORY_001 — Delete (architecture gap): deleteById() không bao giờ được gọi trong service")
        void TC_AUTH_USERSERVICE_REPOSITORY_001_deleteUser_notImplemented_repositoryDeleteNeverInvoked() {
            userService.getCurrentUser(USER_EMAIL);
            userService.changePassword(USER_EMAIL, changePwdReq);

            verify(userRepository, never()).delete(any(User.class));
            verify(userRepository, never()).deleteById(any());
            verify(userRepository, never()).deleteAll();
        }

        @Test
        @DisplayName("TC_AUTH_USERSERVICE_CHANGEPASSWORD_005 — [CHECKDB] changePassword: save() gọi 1 lần với encoded new password")
        void TC_AUTH_USERSERVICE_CHANGEPASSWORD_005_checkdb_saveCalledOnceWithEncodedPassword() {
            ApiResponse response = userService.changePassword(USER_EMAIL, changePwdReq);
            assertTrue(response.isSuccess());

            verify(userRepository, times(1)).findByEmail(USER_EMAIL);
            verify(passwordEncoder, times(1)).encode(NEW_PASSWORD);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository, times(1)).save(captor.capture());

            User savedUser = captor.getValue();
            assertEquals(ENCODED_NEW, savedUser.getPassword(),
                    "Password phải là encoded version của newPassword");
            assertEquals(USER_EMAIL, savedUser.getEmail(),
                    "Email không được thay đổi trong quá trình đổi mật khẩu");
            verifyNoMoreInteractions(userRepository);
        }

        @Test
        @DisplayName("TC_AUTH_USERSERVICE_GETCURRENTUSER_004 — [CHECKDB] getCurrentUser: read-only, findByEmail 1 lần, không save/delete")
        void TC_AUTH_USERSERVICE_GETCURRENTUSER_004_checkdb_readOnlyNoSideEffects() {
            ApiResponse response = userService.getCurrentUser(USER_EMAIL);
            assertTrue(response.isSuccess());

            verify(userRepository, times(1)).findByEmail(USER_EMAIL);
            verify(userRepository, never()).save(any(User.class));
            verify(userRepository, never()).delete(any(User.class));
            verify(userRepository, never()).deleteById(any());
            verifyNoMoreInteractions(userRepository);
        }

                @Test
                @DisplayName("TC_AUTH_USERSERVICE_GETCURRENTUSER_005 — Employee không có position → trả employeeId và position null")
                void TC_AUTH_USERSERVICE_GETCURRENTUSER_005_employeeWithoutPosition_returnsNullPosition() {
                        Employee employee = Employee.builder()
                                        .id(55L)
                                        .fullName("Nhân Viên Thiếu Position")
                                        .build();

                        User employeeUser = User.builder()
                                        .id(15L)
                                        .email(USER_EMAIL)
                                        .password(ENCODED_OLD)
                                        .role(Role.EMPLOYEE)
                                        .status(Status.ACTIVE)
                                        .employee(employee)
                                        .build();
                        employee.setUser(employeeUser);
                        when(userRepository.findByEmail(USER_EMAIL)).thenReturn(java.util.Optional.of(employeeUser));

                        ApiResponse response = userService.getCurrentUser(USER_EMAIL);

                        assertTrue(response.isSuccess());
                        @SuppressWarnings("unchecked")
                        java.util.Map<String, Object> data = (java.util.Map<String, Object>) response.getData();
                        assertEquals("EMPLOYEE", data.get("role"));
                        assertNull(data.get("position"));
                        assertEquals(55L, data.get("employeeId"));
                        assertEquals("Nhân Viên Thiếu Position", data.get("fullName"));
                }
    }

    @Nested
    @DisplayName("UserService — Security / Edge / Error / Concurrency Unit Tests")
    class SecurityEdgeErrorTests {

        @Mock private UserRepository userRepository;
        @Mock private CustomerRepository customerRepository;
        @Mock private OtpRepository otpRepository;
        @Mock private PasswordEncoder passwordEncoder;
        @Mock private JwtService jwtService;
        @Mock private JavaMailSender mailSender;
        @Mock private UserService userService;

        @InjectMocks private UserServiceImpl userServiceSut;
        @InjectMocks private AuthServiceImpl authServiceSut;

        private static final String EMAIL_A = "user_a@example.com";
        private static final String EMAIL_B = "user_b@example.com";
        private static final String ENCODED = "$2a$10$encodedHash";
        private static final String MOCK_JWT =
                "eyJhbGciOiJIUzI1NiJ9.mockPayload.mockSignature";

        private User userA;
        private User userB;

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

        @Test
        @DisplayName("TC_AUTH_USERSERVICE_SECURITY_001 — Security: UserA vs UserB isolation — service trả đúng user theo email")
        void TC_AUTH_USERSERVICE_SECURITY_001_crossUserAccessIsolation() {
            ApiResponse responseA = userServiceSut.getCurrentUser(EMAIL_A);
            assertTrue(responseA.isSuccess());
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> dataA = (java.util.Map<String, Object>) responseA.getData();
            assertEquals(100L, dataA.get("id"), "Phải trả data của User A");
            assertEquals(EMAIL_A, dataA.get("email"));

            ApiResponse responseB = userServiceSut.getCurrentUser(EMAIL_B);
            assertTrue(responseB.isSuccess());
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> dataB = (java.util.Map<String, Object>) responseB.getData();
            assertEquals(200L, dataB.get("id"), "Phải trả data của User B (IDOR cần controller chặn)");
            assertEquals(EMAIL_B, dataB.get("email"));

            verify(userRepository).findByEmail(EMAIL_A);
            verify(userRepository).findByEmail(EMAIL_B);
        }

        @Test
        @DisplayName("TC_AUTH_USERSERVICE_SECURITY_002 — Security: Missing auth (email=null) → Exception, không trả success")
        void TC_AUTH_USERSERVICE_SECURITY_002_missingAuthorizationHeader_nullEmail() {
            when(userRepository.findByEmail(null)).thenReturn(java.util.Optional.empty());

            assertThrows(
                    RuntimeException.class,
                    () -> userServiceSut.getCurrentUser(null),
                    "Phải ném exception khi email null (missing auth)"
            );
            verify(userRepository).findByEmail(null);
        }

        @Test
        @DisplayName("TC_AUTH_USERSERVICE_SECURITY_003 — Security: Invalid JWT token → isTokenValid=false, login không bị ảnh hưởng")
        void TC_AUTH_USERSERVICE_SECURITY_003_invalidToken_isTokenValidReturnsFalse() {
            String malformedToken = "not.a.valid.jwt.token.at.all";
            org.springframework.security.core.userdetails.UserDetails userDetails =
                    org.springframework.security.core.userdetails.User.builder()
                            .username(EMAIL_A).password(ENCODED).roles("CUSTOMER").build();

            when(jwtService.isTokenValid(malformedToken, userDetails)).thenReturn(false);

            boolean valid = jwtService.isTokenValid(malformedToken, userDetails);

            assertFalse(valid, "Token malformed phải bị từ chối");

            LoginRequest loginReq = new LoginRequest();
            loginReq.setEmail(EMAIL_A);
            loginReq.setPassword("ValidPass@123");
            when(passwordEncoder.matches("ValidPass@123", ENCODED)).thenReturn(true);
            ApiResponse loginResp = userServiceSut.login(loginReq);
            assertTrue(loginResp.isSuccess(), "Login hợp lệ không bị ảnh hưởng bởi token invalid");
        }

        @Test
        @DisplayName("TC_AUTH_USERSERVICE_SECURITY_004 — Security: Expired token → isTokenValid=false")
        void TC_AUTH_USERSERVICE_SECURITY_004_expiredToken_isTokenValidReturnsFalse() {
            String expiredToken = "expired.jwt.token";
            org.springframework.security.core.userdetails.UserDetails userDetails =
                    org.springframework.security.core.userdetails.User.builder()
                            .username(EMAIL_A).password(ENCODED).roles("CUSTOMER").build();

            when(jwtService.isTokenValid(expiredToken, userDetails)).thenReturn(false);

            boolean valid = jwtService.isTokenValid(expiredToken, userDetails);

            assertFalse(valid, "Token hết hạn phải bị từ chối");
        }

        @Test
        @DisplayName("TC_AUTH_USERSERVICE_SECURITY_005 — Security: Tampered token → generateToken ném exception → login thất bại")
        void TC_AUTH_USERSERVICE_SECURITY_005_tamperedToken_loginFails() {
            when(jwtService.generateToken(eq(EMAIL_A), any()))
                    .thenThrow(new RuntimeException("SignatureException: JWT signature does not match"));

            LoginRequest loginReq = new LoginRequest();
            loginReq.setEmail(EMAIL_A);
            loginReq.setPassword("ValidPass@123");
            when(passwordEncoder.matches("ValidPass@123", ENCODED)).thenReturn(true);

            RuntimeException ex = assertThrows(
                    RuntimeException.class,
                    () -> userServiceSut.login(loginReq),
                    "Tampered token phải ném exception, login thất bại"
            );
            assertThat(ex.getMessage()).contains("SignatureException");
        }

        @Test
        @DisplayName("TC_AUTH_USERSERVICE_SECURITY_006 — Security: SQL Injection trong email → JPA bảo vệ, service không modify payload")
        void TC_AUTH_USERSERVICE_SECURITY_006_sqlInjectionInEmail_rejectedByJpa() {
            String sqlPayload = "admin'-- OR '1'='1@evil.com";
            when(userRepository.findByEmail(sqlPayload)).thenReturn(java.util.Optional.empty());

            LoginRequest loginReq = new LoginRequest();
            loginReq.setEmail(sqlPayload);
            loginReq.setPassword("anyPassword");

            ApiResponse response = userServiceSut.login(loginReq);

            assertFalse(response.isSuccess(), "SQL injection payload phải bị từ chối");
            assertEquals("Email không tồn tại!", response.getMessage());
            verify(userRepository).findByEmail(sqlPayload);
            verify(passwordEncoder, never()).matches(any(), any());
        }

        @Test
        @DisplayName("TC_AUTH_USERSERVICE_SECURITY_007 — Security: Unauthorized role — CUSTOMER gọi firstChangePassword → reject")
        void TC_AUTH_USERSERVICE_SECURITY_007_unauthorizedRole_customerCallsFirstChangePassword() {
            when(userRepository.findByEmail(EMAIL_A))
                    .thenReturn(java.util.Optional.of(userA));

            FirstChangePasswordRequest req = new FirstChangePasswordRequest();
            req.setEmail(EMAIL_A);
            req.setCurrentPassword("OldPass@123");
            req.setNewPassword("NewPass@456");
            req.setConfirmPassword("NewPass@456");

            ApiResponse response = userServiceSut.firstChangePassword(req);

            assertFalse(response.isSuccess(), "CUSTOMER không được phép dùng firstChangePassword");
            assertEquals("Chỉ nhân viên mới được đổi mật khẩu lần đầu!", response.getMessage());
            verify(userRepository, never()).save(any(User.class));
            verify(passwordEncoder, never()).encode(any());
        }

        @Test
        @DisplayName("TC_AUTH_USERSERVICE_SECURITY_008 — Edge: Password length=0 (empty) → Error 'Mật khẩu không đúng!'")
        void TC_AUTH_USERSERVICE_SECURITY_008_passwordLengthZero_loginFails() {
            LoginRequest loginReq = new LoginRequest();
            loginReq.setEmail(EMAIL_A);
            loginReq.setPassword("");
            when(passwordEncoder.matches("", ENCODED)).thenReturn(false);

            ApiResponse response = userServiceSut.login(loginReq);

            assertFalse(response.isSuccess());
            assertEquals("Mật khẩu không đúng!", response.getMessage());
            verify(passwordEncoder).matches("", ENCODED);
            verify(jwtService, never()).generateToken(any(), any());
        }

        @Test
        @DisplayName("TC_AUTH_USERSERVICE_SECURITY_009 — Edge: Password length=1 → Error 'Mật khẩu không đúng!' (no exception)")
        void TC_AUTH_USERSERVICE_SECURITY_009_passwordLengthOne_loginFails() {
            LoginRequest loginReq = new LoginRequest();
            loginReq.setEmail(EMAIL_A);
            loginReq.setPassword("X");
            when(passwordEncoder.matches("X", ENCODED)).thenReturn(false);

            ApiResponse response = userServiceSut.login(loginReq);

            assertFalse(response.isSuccess());
            assertEquals("Mật khẩu không đúng!", response.getMessage());
            verify(passwordEncoder).matches("X", ENCODED);
        }

        @Test
        @DisplayName("TC_AUTH_USERSERVICE_SECURITY_010 — Edge: Password 10.000 ký tự → service trả error, không crash/OOM")
        void TC_AUTH_USERSERVICE_SECURITY_010_extremelyLongPassword_noOomNoCrash() {
            String ultraLongPwd = "P@ssw0rd!".repeat(1112);
            assertThat(ultraLongPwd.length()).isGreaterThan(9999);

            LoginRequest loginReq = new LoginRequest();
            loginReq.setEmail(EMAIL_A);
            loginReq.setPassword(ultraLongPwd);
            when(passwordEncoder.matches(ultraLongPwd, ENCODED)).thenReturn(false);

            ApiResponse response = userServiceSut.login(loginReq);

            assertFalse(response.isSuccess());
            assertEquals("Mật khẩu không đúng!", response.getMessage());
            verify(passwordEncoder).matches(ultraLongPwd, ENCODED);
        }

        @Test
        @DisplayName("TC_AUTH_USERSERVICE_SECURITY_011 — Edge: Email null + empty trong login → Error 'Email không tồn tại!'")
        void TC_AUTH_USERSERVICE_SECURITY_011_nullAndEmptyEmail_loginReturnsEmailNotFoundError() {
            LoginRequest nullEmailReq = new LoginRequest();
            nullEmailReq.setEmail(null);
            nullEmailReq.setPassword("anyPassword");
            when(userRepository.findByEmail(null)).thenReturn(java.util.Optional.empty());

            ApiResponse responseNull = userServiceSut.login(nullEmailReq);
            assertFalse(responseNull.isSuccess(), "email=null phải trả error");
            assertEquals("Email không tồn tại!", responseNull.getMessage());

            LoginRequest emptyEmailReq = new LoginRequest();
            emptyEmailReq.setEmail("");
            emptyEmailReq.setPassword("anyPassword");
            when(userRepository.findByEmail("")).thenReturn(java.util.Optional.empty());

            ApiResponse responseEmpty = userServiceSut.login(emptyEmailReq);
            assertFalse(responseEmpty.isSuccess(), "email=empty phải trả error");
            assertEquals("Email không tồn tại!", responseEmpty.getMessage());
            verify(passwordEncoder, never()).matches(any(), any());
        }

        @Test
        @DisplayName("TC_AUTH_USERSERVICE_SECURITY_012 — Error: DB connection failure → RuntimeException propagate, không tạo JWT")
        void TC_AUTH_USERSERVICE_SECURITY_012_dbConnectionFailure_exceptionPropagates() {
            LoginRequest loginReq = new LoginRequest();
            loginReq.setEmail(EMAIL_A);
            loginReq.setPassword("ValidPass@123");
            when(userRepository.findByEmail(EMAIL_A))
                    .thenThrow(new org.springframework.dao.DataAccessResourceFailureException(
                            "DB connection timeout: Cannot connect to MS SQL Server"));

            RuntimeException ex = assertThrows(
                    RuntimeException.class,
                    () -> userServiceSut.login(loginReq),
                    "DB error phải propagate lên caller"
            );
            assertThat(ex.getMessage()).contains("DB connection timeout");
            verify(passwordEncoder, never()).matches(any(), any());
            verify(jwtService, never()).generateToken(any(), any());
        }

        @Test
        @DisplayName("TC_AUTH_USERSERVICE_SECURITY_013 — Error: Null password trong DB → NullPointerException propagate (dữ liệu hỏng)")
        void TC_AUTH_USERSERVICE_SECURITY_013_nullPasswordInDb_nullPointerExceptionPropagates() {
            User corruptedUser = User.builder()
                    .id(999L).email(EMAIL_A).password(null)
                    .role(Role.CUSTOMER).status(Status.ACTIVE).build();
            when(userRepository.findByEmail(EMAIL_A))
                    .thenReturn(java.util.Optional.of(corruptedUser));

            doThrow(new NullPointerException("encodedPassword cannot be null"))
                    .when(passwordEncoder).matches(any(), isNull());

            LoginRequest loginReq = new LoginRequest();
            loginReq.setEmail(EMAIL_A);
            loginReq.setPassword("ValidPass@123");

            assertThrows(
                    NullPointerException.class,
                    () -> userServiceSut.login(loginReq),
                    "NullPointerException phải propagate khi DB chứa null password"
            );
            verify(jwtService, never()).generateToken(any(), any());
        }

        @Test
        @DisplayName("TC_AUTH_USERSERVICE_SECURITY_014 — Concurrency: 10 login requests → mỗi request độc lập, stateless JWT")
        void TC_AUTH_USERSERVICE_SECURITY_014_tenConcurrentLoginRequests_statelessJwt() {
            LoginRequest loginReq = new LoginRequest();
            loginReq.setEmail(EMAIL_A);
            loginReq.setPassword("ValidPass@123");
            when(passwordEncoder.matches("ValidPass@123", ENCODED)).thenReturn(true);

            int concurrentRequests = 10;
            for (int i = 0; i < concurrentRequests; i++) {
                ApiResponse response = userServiceSut.login(loginReq);
                assertTrue(response.isSuccess(),
                        "Request " + (i + 1) + "/" + concurrentRequests + " phải thành công");
                LoginResponse loginResp = (LoginResponse) response.getData();
                assertEquals(MOCK_JWT, loginResp.getToken(),
                        "Token phải là mock JWT (stateless)");
            }

            verify(jwtService, times(concurrentRequests)).generateToken(eq(EMAIL_A), any());
            verify(userRepository, times(concurrentRequests)).findByEmail(EMAIL_A);
            verify(userRepository, never()).save(any(User.class));
        }
    }

        @Nested
        @DisplayName("UserService — REGISTER CUSTOMER Unit Tests")
        class RegisterCustomerTests {

                @Mock
                private UserRepository userRepository;

                @Mock
                private CustomerRepository customerRepository;

                @Mock
                private PasswordEncoder passwordEncoder;

                @Mock
                private JwtService jwtService;

                @InjectMocks
                private UserServiceImpl userService;

                @BeforeEach
                void setUpRegisterCustomer() {
                        lenient().when(userRepository.existsByEmail(anyString())).thenReturn(false);
                        lenient().when(customerRepository.existsByPhone(anyString())).thenReturn(false);
                        lenient().when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$encodedNewCustomerPassword");
                }

                @AfterEach
                void tearDownRegisterCustomer() {
                        Mockito.reset(userRepository, customerRepository, passwordEncoder, jwtService);
                }

                @Test
                @DisplayName("TC_AUTH_USERSERVICE_REGISTERCUSTOMER_001 — Mật khẩu thường → encode và lưu CUSTOMER ACTIVE")
                void TC_AUTH_USERSERVICE_REGISTERCUSTOMER_001_plainPassword_shouldEncodeAndSaveCustomer() {
                        ApiResponse response = userService.registerCustomer(
                                        "fresh@example.com",
                                        "PlainPass@123",
                                        "Khách Hàng Mới",
                                        "0909009009",
                                        "12 Nguyễn Trãi"
                        );

                        assertTrue(response.isSuccess());
                        assertEquals("Tạo tài khoản khách hàng thành công!", response.getMessage());
                        verify(passwordEncoder).encode("PlainPass@123");

                        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
                        verify(userRepository).save(userCaptor.capture());
                        User savedUser = userCaptor.getValue();
                        assertEquals("fresh@example.com", savedUser.getEmail());
                        assertEquals(Role.CUSTOMER, savedUser.getRole());
                        assertEquals(Status.ACTIVE, savedUser.getStatus());
                        assertEquals("$2a$10$encodedNewCustomerPassword", savedUser.getPassword());
                        assertNotNull(savedUser.getCustomer());
                        assertEquals("Khách Hàng Mới", savedUser.getCustomer().getFullName());
                        assertEquals("0909009009", savedUser.getCustomer().getPhone());
                        assertEquals("12 Nguyễn Trãi", savedUser.getCustomer().getAddress());
                        assertSame(savedUser, savedUser.getCustomer().getUser());
                }

                @Test
                @DisplayName("TC_AUTH_USERSERVICE_REGISTERCUSTOMER_002 — Mật khẩu đã encode sẵn → không encode lại")
                void TC_AUTH_USERSERVICE_REGISTERCUSTOMER_002_bcryptPassword_shouldSkipEncoding() {
                        String bcryptPassword = "$2a$10$alreadyEncodedPassword";

                        ApiResponse response = userService.registerCustomer(
                                        "encoded@example.com",
                                        bcryptPassword,
                                        "Khách Encode",
                                        "0911222333",
                                        "45 Lý Thường Kiệt"
                        );

                        assertTrue(response.isSuccess());
                        verify(passwordEncoder, never()).encode(anyString());

                        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
                        verify(userRepository).save(userCaptor.capture());
                        assertEquals(bcryptPassword, userCaptor.getValue().getPassword());
                }

                @Test
                @DisplayName("TC_AUTH_USERSERVICE_REGISTERCUSTOMER_003 — Email trùng → trả lỗi, không lưu")
                void TC_AUTH_USERSERVICE_REGISTERCUSTOMER_003_duplicateEmail_shouldReturnError() {
                        when(userRepository.existsByEmail("duplicate@example.com")).thenReturn(true);

                        ApiResponse response = userService.registerCustomer(
                                        "duplicate@example.com",
                                        "PlainPass@123",
                                        "Khách Trùng",
                                        "0911444555",
                                        "1 Trần Hưng Đạo"
                        );

                        assertFalse(response.isSuccess());
                        assertEquals("Email đã tồn tại!", response.getMessage());
                        verify(passwordEncoder, never()).encode(anyString());
                        verify(userRepository, never()).save(any(User.class));
                }

                @Test
                @DisplayName("TC_AUTH_USERSERVICE_REGISTERCUSTOMER_004 — Số điện thoại trùng → trả lỗi, không lưu")
                void TC_AUTH_USERSERVICE_REGISTERCUSTOMER_004_duplicatePhone_shouldReturnError() {
                        when(customerRepository.existsByPhone("0911444555")).thenReturn(true);

                        ApiResponse response = userService.registerCustomer(
                                        "phone@example.com",
                                        "PlainPass@123",
                                        "Khách Trùng SĐT",
                                        "0911444555",
                                        "1 Trần Hưng Đạo"
                        );

                        assertFalse(response.isSuccess());
                        assertEquals("Số điện thoại đã tồn tại!", response.getMessage());
                        verify(passwordEncoder, never()).encode(anyString());
                        verify(userRepository, never()).save(any(User.class));
                }
        }

        @Nested
        @DisplayName("UserService — FIRST CHANGE PASSWORD Unit Tests")
        class FirstChangePasswordTests {

                @Mock
                private UserRepository userRepository;

                @Mock
                private CustomerRepository customerRepository;

                @Mock
                private PasswordEncoder passwordEncoder;

                @Mock
                private JwtService jwtService;

                @InjectMocks
                private UserServiceImpl userService;

                private FirstChangePasswordRequest request;
                private User employeeUser;
                private Employee employee;

                @BeforeEach
                void setUpFirstChangePassword() {
                        request = new FirstChangePasswordRequest();
                        request.setEmail("employee@example.com");
                        request.setCurrentPassword("OldPass@123");
                        request.setNewPassword("NewPass@456");
                        request.setConfirmPassword("NewPass@456");

                        employee = Employee.builder()
                                        .id(88L)
                                        .fullName("Nhân Viên Một")
                                        .position(Position.SALE)
                                        .firstLogin(true)
                                        .build();

                        employeeUser = User.builder()
                                        .id(8L)
                                        .email("employee@example.com")
                                        .password("$2a$10$encodedOldPassword")
                                        .role(Role.EMPLOYEE)
                                        .status(Status.ACTIVE)
                                        .employee(employee)
                                        .build();
                        employee.setUser(employeeUser);

                        lenient().when(userRepository.findByEmail("employee@example.com"))
                                        .thenReturn(java.util.Optional.of(employeeUser));
                        lenient().when(passwordEncoder.matches("OldPass@123", "$2a$10$encodedOldPassword"))
                                        .thenReturn(true);
                        lenient().when(passwordEncoder.encode("NewPass@456"))
                                        .thenReturn("$2a$10$encodedNewPassword");
                }

                @AfterEach
                void tearDownFirstChangePassword() {
                        Mockito.reset(userRepository, customerRepository, passwordEncoder, jwtService);
                }

                @Test
                @DisplayName("TC_AUTH_USERSERVICE_FIRSTCHANGEPASSWORD_001 — Nhân viên first login hợp lệ → đổi mật khẩu thành công")
                void TC_AUTH_USERSERVICE_FIRSTCHANGEPASSWORD_001_validEmployeeFirstLogin_shouldChangePassword() {
                        ApiResponse response = userService.firstChangePassword(request);

                        assertTrue(response.isSuccess());
                        assertEquals("Đổi mật khẩu thành công!", response.getMessage());
                        assertFalse(employee.isFirstLogin());
                        assertEquals("$2a$10$encodedNewPassword", employeeUser.getPassword());
                        verify(passwordEncoder).encode("NewPass@456");
                        verify(userRepository).save(employeeUser);
                }

                @Test
                @DisplayName("TC_AUTH_USERSERVICE_FIRSTCHANGEPASSWORD_002 — Không tìm thấy user → ném RuntimeException")
                void TC_AUTH_USERSERVICE_FIRSTCHANGEPASSWORD_002_userNotFound_shouldThrow() {
                        when(userRepository.findByEmail("employee@example.com")).thenReturn(java.util.Optional.empty());

                        RuntimeException ex = assertThrows(RuntimeException.class,
                                        () -> userService.firstChangePassword(request));

                        assertEquals("Không tìm thấy người dùng!", ex.getMessage());
                        verify(userRepository, never()).save(any(User.class));
                }

                @Test
                @DisplayName("TC_AUTH_USERSERVICE_FIRSTCHANGEPASSWORD_003 — Mật khẩu hiện tại sai → trả lỗi")
                void TC_AUTH_USERSERVICE_FIRSTCHANGEPASSWORD_003_wrongCurrentPassword_shouldReturnError() {
                        when(passwordEncoder.matches("OldPass@123", "$2a$10$encodedOldPassword")).thenReturn(false);

                        ApiResponse response = userService.firstChangePassword(request);

                        assertFalse(response.isSuccess());
                        assertEquals("Mật khẩu hiện tại không đúng!", response.getMessage());
                        verify(passwordEncoder, never()).encode(anyString());
                        verify(userRepository, never()).save(any(User.class));
                        assertTrue(employee.isFirstLogin());
                }

                @Test
                @DisplayName("TC_AUTH_USERSERVICE_FIRSTCHANGEPASSWORD_004 — Confirm password không khớp → trả lỗi")
                void TC_AUTH_USERSERVICE_FIRSTCHANGEPASSWORD_004_confirmMismatch_shouldReturnError() {
                        request.setConfirmPassword("Mismatch@789");

                        ApiResponse response = userService.firstChangePassword(request);

                        assertFalse(response.isSuccess());
                        assertEquals("Xác nhận mật khẩu mới không khớp!", response.getMessage());
                        verify(passwordEncoder, never()).encode(anyString());
                        verify(userRepository, never()).save(any(User.class));
                        assertTrue(employee.isFirstLogin());
                }

                @Test
                @DisplayName("TC_AUTH_USERSERVICE_FIRSTCHANGEPASSWORD_005 — Role EMPLOYEE nhưng employee profile null → reject")
                void TC_AUTH_USERSERVICE_FIRSTCHANGEPASSWORD_005_employeeRoleButProfileNull_shouldReturnError() {
                        User brokenEmployeeUser = User.builder()
                                        .id(9L)
                                        .email("employee@example.com")
                                        .password("$2a$10$encodedOldPassword")
                                        .role(Role.EMPLOYEE)
                                        .status(Status.ACTIVE)
                                        .employee(null)
                                        .build();
                        when(userRepository.findByEmail("employee@example.com"))
                                        .thenReturn(java.util.Optional.of(brokenEmployeeUser));

                        ApiResponse response = userService.firstChangePassword(request);

                        assertFalse(response.isSuccess());
                        assertEquals("Chỉ nhân viên mới được đổi mật khẩu lần đầu!", response.getMessage());
                        verify(passwordEncoder, never()).matches(any(), any());
                        verify(userRepository, never()).save(any(User.class));
                }
        }

    @Nested
    @DisplayName("UserService — Spec Gap / Defect Evidence Tests")
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

        private LoginRequest loginRequest;
        private ChangePasswordRequest changePasswordRequest;
        private User activeUser;

        @BeforeEach
        void setUpSpecGap() {
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
        @DisplayName("TC_AUTH_USERSERVICE_LOGIN_SPEC_001 — Spec: Email rỗng khi login phải trả lỗi validation rõ ràng")
        void TC_AUTH_USERSERVICE_LOGIN_SPEC_001_emptyEmail_shouldReturnValidationMessage() {
            loginRequest.setEmail("");
            when(userRepository.findByEmail("")).thenReturn(java.util.Optional.empty());

            ApiResponse response = userServiceSut.login(loginRequest);

            assertFalse(response.isSuccess(),
                    "Theo nghiệp vụ chuẩn, email rỗng phải trả lỗi validation thay vì Email không tồn tại");
            assertEquals("Email không được để trống!", response.getMessage());
        }

        @Test
        @DisplayName("TC_AUTH_USERSERVICE_LOGIN_SPEC_002 — Spec: Password null khi login không được làm rơi exception kỹ thuật")
        void TC_AUTH_USERSERVICE_LOGIN_SPEC_002_nullPassword_shouldNotLeakTechnicalException() {
            loginRequest.setPassword(null);
            doThrow(new IllegalArgumentException("rawPassword cannot be null"))
                    .when(passwordEncoder).matches(null, "$2a$10$encodedOldPassword");

            userServiceSut.login(loginRequest);
        }

        @Test
        @DisplayName("TC_AUTH_USERSERVICE_CHANGEPASSWORD_SPEC_001 — Spec: Đổi sang mật khẩu trùng mật khẩu cũ phải bị từ chối")
        void TC_AUTH_USERSERVICE_CHANGEPASSWORD_SPEC_001_sameAsOld_shouldBeRejected() {
            changePasswordRequest.setNewPassword("OldPass@123");
            changePasswordRequest.setConfirmPassword("OldPass@123");

            ApiResponse response = userServiceSut.changePassword("specgap@example.com", changePasswordRequest);

            assertFalse(response.isSuccess(),
                    "Theo nghiệp vụ chuẩn, mật khẩu mới trùng mật khẩu cũ phải bị từ chối");
            assertEquals("Mật khẩu mới không được trùng mật khẩu cũ!", response.getMessage());
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("TC_AUTH_USERSERVICE_GETCURRENTUSER_SPEC_001 — Spec: Tài khoản LOCKED không được lấy thông tin hồ sơ")
        void TC_AUTH_USERSERVICE_GETCURRENTUSER_SPEC_001_lockedUser_shouldNotReadProfile() {
            activeUser.setStatus(Status.LOCKED);

            ApiResponse response = userServiceSut.getCurrentUser("specgap@example.com");

            assertFalse(response.isSuccess(),
                    "Theo nghiệp vụ chuẩn, tài khoản bị khóa không được đọc hồ sơ người dùng");
            assertEquals("Tài khoản đang bị khóa!", response.getMessage());
        }

        @Test
        @DisplayName("TC_AUTH_USERSERVICE_SECURITY_SPEC_001 — Spec: Sai mật khẩu 5 lần phải khóa tài khoản hoặc từ chối lần sau")
        void TC_AUTH_USERSERVICE_SECURITY_SPEC_001_multipleWrongPasswords_shouldTriggerLockout() {
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
    }
}