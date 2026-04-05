package com.doan.WEB_TMDT.module.auth.service.impl;

import com.doan.WEB_TMDT.common.dto.ApiResponse;
import com.doan.WEB_TMDT.module.auth.entity.*;
import com.doan.WEB_TMDT.module.auth.repository.*;
import com.doan.WEB_TMDT.module.auth.service.EmployeeRegistrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class EmployeeRegistrationServiceImpl implements EmployeeRegistrationService {

    private final EmployeeRegistrationRepository registrationRepo;
    private final EmployeeRepository employeeRepo;
    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;

    @Override
    @Transactional
    public ApiResponse registerEmployee(String fullName, String email, String phone, String address, Position position, String note) {
        System.out.println("========== REGISTER EMPLOYEE START ==========");
        System.out.println("Full Name: " + fullName);
        System.out.println("Email: " + email);
        System.out.println("Phone: " + phone);
        System.out.println("Position: " + position);

        // Kiểm tra email đã tồn tại trong registration (chờ duyệt) hoặc users (đã duyệt)
        if (registrationRepo.existsByEmail(email)) {
            System.out.println("❌ ERROR: Email already exists in registration");
            return ApiResponse.error("Email đã được đăng ký và đang chờ duyệt!");
        }
        
        if (userRepo.existsByEmail(email)) {
            System.out.println("❌ ERROR: Email already exists in users");
            return ApiResponse.error("Email đã tồn tại trong hệ thống!");
        }

        // Kiểm tra phone đã tồn tại trong registration (chờ duyệt) hoặc employees (đã duyệt)
        if (registrationRepo.existsByPhone(phone)) {
            System.out.println("❌ ERROR: Phone already exists in registration");
            return ApiResponse.error("Số điện thoại đã được đăng ký và đang chờ duyệt!");
        }
        
        if (employeeRepo.existsByPhone(phone)) {
            System.out.println("❌ ERROR: Phone already exists in employees");
            return ApiResponse.error("Số điện thoại đã tồn tại trong hệ thống!");
        }

        System.out.println("✅ Validation passed, creating registration...");

        EmployeeRegistration reg = EmployeeRegistration.builder()
                .fullName(fullName)
                .email(email)
                .phone(phone)
                .address(address)
                .position(position)
                .note(note)
                .approved(false)
                .createdAt(LocalDateTime.now())
                .build();
        
        System.out.println("💾 Saving to employee_registration table...");
        EmployeeRegistration saved = registrationRepo.save(reg);
        System.out.println("✅ Saved with ID: " + saved.getId());

        // Force flush to database immediately
        registrationRepo.flush();
        System.out.println("✅ Flushed to database");
        
        // Kiểm tra xem có tạo employee không (không nên có!)
        boolean employeeCreated = employeeRepo.existsByPhone(phone);
        if (employeeCreated) {
            System.err.println("⚠️⚠️⚠️ WARNING: Employee was created automatically! This should NOT happen!");
        } else {
            System.out.println("✅ No employee created (correct behavior)");
        }
        
        System.out.println("========== REGISTER EMPLOYEE END ==========");
        return ApiResponse.success("Gửi yêu cầu đăng ký nhân viên thành công, chờ admin duyệt!", saved);
    }

    @Transactional
    @Override
    public ApiResponse approveEmployee(Long registrationId) {
        System.out.println("========== APPROVE EMPLOYEE START ==========");
        System.out.println("Registration ID: " + registrationId);
        
        EmployeeRegistration reg = registrationRepo.findById(registrationId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phiếu đăng ký!"));

        if (reg.isApproved()) {
            System.out.println("❌ Registration already approved");
            return ApiResponse.error("Phiếu đăng ký này đã được duyệt!");
        }

        System.out.println("📝 Creating user account for: " + reg.getEmail());
        
        // Tạo mật khẩu ngẫu nhiên
        String rawPassword = generateRandomPassword(10);
        String encodedPassword = passwordEncoder.encode(rawPassword);

        // Tạo tài khoản user
        User user = User.builder()
                .email(reg.getEmail())
                .password(encodedPassword)
                .role(Role.EMPLOYEE)
                .status(Status.ACTIVE)
                .build();

        // Tạo hồ sơ nhân viên chính thức
        Employee emp = Employee.builder()
                .user(user)
                .fullName(reg.getFullName())
                .phone(reg.getPhone())
                .address(reg.getAddress())
                .position(reg.getPosition())
                .firstLogin(true)
                .build();
        user.setEmployee(emp);

        System.out.println("💾 Saving user to database...");
        userRepo.save(user); // cascade lưu cả employee
        System.out.println("✅ User saved successfully");

        // Gửi mail thông báo tài khoản
        System.out.println("📧 Sending email to: " + reg.getEmail());
        sendEmailAccount(reg.getEmail(), rawPassword);
        System.out.println("✅ Email sent successfully");

        // Xóa phiếu đăng ký sau khi duyệt thành công
        System.out.println("🗑️ Deleting registration ID: " + registrationId);
        registrationRepo.deleteById(registrationId);
        System.out.println("✅ Registration deleted successfully");
        
        System.out.println("========== APPROVE EMPLOYEE END ==========");

        return ApiResponse.success("Đã duyệt và gửi thông tin tài khoản qua email!", emp);
    }

    private String generateRandomPassword(int len) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        Random rnd = new Random();
        for (int i = 0; i < len; i++) {
            sb.append(chars.charAt(rnd.nextInt(chars.length())));
        }
        return sb.toString();
    }

    @Override
    public ApiResponse getAllRegistrations() {
        return ApiResponse.success("Danh sách đăng ký nhân viên", registrationRepo.findAll());
    }

    @Override
    public ApiResponse getPendingRegistrations() {
        return ApiResponse.success("Danh sách đăng ký chờ duyệt", 
                registrationRepo.findAll().stream()
                        .filter(reg -> !reg.isApproved())
                        .toList());
    }

    @Override
    public long getRegistrationCount() {
        return registrationRepo.count();
    }

    private void sendEmailAccount(String email, String password) {
        try {
            System.out.println("📧 Đang gửi email tới: " + email);
            
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(email);
            msg.setSubject("Tài khoản nhân viên đã được duyệt");
            msg.setText("Xin chào,\n\nTài khoản của bạn đã được duyệt.\n" +
                    "Email: " + email + "\n" +
                    "Mật khẩu: " + password + "\n\n" +
                    "Trân trọng,\nAdmin");
            
            mailSender.send(msg);
            
            System.out.println("✅ Đã gửi email thành công tới: " + email);
        } catch (Exception e) {
            System.err.println("❌ KHÔNG THỂ GỬI EMAIL tới " + email);
            System.err.println("❌ Lỗi chi tiết: " + e.getClass().getName() + " - " + e.getMessage());
            e.printStackTrace();
            
            // Throw exception để rollback transaction nếu cần
            throw new RuntimeException("Không thể gửi email tới " + email + ": " + e.getMessage(), e);
        }
    }
    
}
