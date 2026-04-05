package com.doan.WEB_TMDT.module.auth.service.impl;

import com.doan.WEB_TMDT.common.dto.ApiResponse;
import com.doan.WEB_TMDT.module.auth.dto.OtpVerifyRequest;
import com.doan.WEB_TMDT.module.auth.dto.RegisterRequest;
import com.doan.WEB_TMDT.module.auth.entity.*;
import com.doan.WEB_TMDT.module.auth.repository.*;
import com.doan.WEB_TMDT.module.auth.service.AuthService;
import com.doan.WEB_TMDT.module.auth.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final OtpRepository otpRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final UserService userService; // Gọi khi OTP hợp lệ để tạo user thực tế
    private final com.doan.WEB_TMDT.security.JwtService jwtService;
    // -----------------------------------------------------------
    // 1. Gửi mã OTP xác minh khi đăng ký
    // -----------------------------------------------------------
    @Override
    public ApiResponse sendOtp(RegisterRequest request) {
        System.out.println("=== SEND OTP ===");
        System.out.println("Email: " + request.getEmail());
        System.out.println("Phone: " + request.getPhone());
        System.out.println("Full Name: " + request.getFullName());
        
        // Kiểm tra trùng email hoặc SĐT
        if (userRepository.existsByEmail(request.getEmail())) {
            return ApiResponse.error("Email đã được sử dụng!");
        }
        if (customerRepository.existsByPhone(request.getPhone())) {
            return ApiResponse.error("Số điện thoại đã tồn tại!");
        }

        // Tạo mã OTP ngẫu nhiên 6 chữ số
        String otp = String.format("%06d", new Random().nextInt(999999));
        System.out.println("Generated OTP: " + otp);

        // Mã hóa mật khẩu tạm (sẽ dùng khi tạo tài khoản)
        String encodedPassword = passwordEncoder.encode(request.getPassword());

        // Lưu OTP kèm toàn bộ thông tin đăng ký
        OtpVerification otpVerification = OtpVerification.builder()
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .address(request.getAddress())
                .email(request.getEmail())
                .encodedPassword(encodedPassword)
                .otpCode(otp)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .verified(false)
                .build();
        
        System.out.println("Saving OTP to database...");
        otpRepository.save(otpVerification);
        System.out.println("OTP saved successfully");

        // Gửi OTP qua email
        try {
            System.out.println("Sending OTP email to: " + request.getEmail());
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(request.getEmail());
            message.setSubject("Mã xác minh đăng ký tài khoản");
            message.setText("Xin chào " + request.getFullName() +
                    ",\n\nMã OTP của bạn là: " + otp +
                    "\nMã có hiệu lực trong 5 phút.\n\nTrân trọng,\nĐội ngũ hỗ trợ WEB_TMDT.");
            mailSender.send(message);
            System.out.println("OTP email sent successfully");
        } catch (Exception e) {
            System.err.println("Failed to send OTP email: " + e.getMessage());
            e.printStackTrace();
            return ApiResponse.error("Không thể gửi email OTP. Vui lòng thử lại sau!");
        }

        System.out.println("=== OTP SENT  ===");
        return ApiResponse.success("Mã OTP đã được gửi đến email của bạn!");
    }

    // -----------------------------------------------------------
    // 2. Xác minh OTP và tạo tài khoản mới
    // -----------------------------------------------------------
    @Override
    public ApiResponse verifyOtpAndRegister(OtpVerifyRequest request) {
        var otpRecord = otpRepository.findByEmailAndOtpCode(request.getEmail(), request.getOtpCode())
                .orElse(null);

        if (otpRecord == null) {
            return ApiResponse.error("Mã OTP không hợp lệ!");
        }

        if (otpRecord.isVerified()) {
            return ApiResponse.error("Mã OTP này đã được xác minh!");
        }

        if (otpRecord.getExpiresAt().isBefore(LocalDateTime.now())) {
            return ApiResponse.error("Mã OTP đã hết hạn!");
        }

        // Đánh dấu OTP đã xác minh
        otpRecord.setVerified(true);
        otpRepository.save(otpRecord);

        // Tạo tài khoản mới từ thông tin OTP
        ApiResponse response = userService.registerCustomer(
                otpRecord.getEmail(),
                otpRecord.getEncodedPassword(),
                otpRecord.getFullName(),
                otpRecord.getPhone(),
                otpRecord.getAddress()
        );

        return ApiResponse.success("Xác minh OTP thành công, tài khoản đã được tạo!", response.getData());
    }
}
