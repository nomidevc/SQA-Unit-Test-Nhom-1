package com.doan.WEB_TMDT.module.payment.service.impl;

import com.doan.WEB_TMDT.common.dto.ApiResponse;
import com.doan.WEB_TMDT.module.auth.entity.User;
import com.doan.WEB_TMDT.module.auth.repository.UserRepository;
import com.doan.WEB_TMDT.module.order.entity.Order;
import com.doan.WEB_TMDT.module.order.repository.OrderRepository;
import com.doan.WEB_TMDT.module.order.service.OrderService;
import com.doan.WEB_TMDT.module.payment.dto.CreatePaymentRequest;
import com.doan.WEB_TMDT.module.payment.dto.PaymentResponse;
import com.doan.WEB_TMDT.module.payment.dto.SepayWebhookRequest;
import com.doan.WEB_TMDT.module.payment.entity.Payment;
import com.doan.WEB_TMDT.module.payment.entity.PaymentMethod;
import com.doan.WEB_TMDT.module.payment.entity.PaymentStatus;
import com.doan.WEB_TMDT.module.payment.repository.PaymentRepository;
import com.doan.WEB_TMDT.module.payment.service.PaymentService;
import com.doan.WEB_TMDT.module.accounting.listener.OrderStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final com.doan.WEB_TMDT.module.payment.repository.BankAccountRepository bankAccountRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final OrderService orderService;

    @Value("${sepay.bank.code:VCB}")
    private String sepayBankCode;

    @Value("${sepay.bank.account.number:1234567890}")
    private String sepayAccountNumber;

    @Value("${sepay.bank.account.name:CONG TY TNHH TECHMART}")
    private String sepayAccountName;

    @Value("${sepay.api.secret:demo_secret}")
    private String sepaySecretKey;

    @Value("${sepay.amount.multiplier:1}")
    private double amountMultiplier; // Nhân số tiền nếu cần (0.001 = chia 1000, 1 = giữ nguyên)

    @Override
    public Long getUserIdByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng với email: " + email));
        return user.getId();
    }

    @Override
    @Transactional
    public ApiResponse createPayment(CreatePaymentRequest request, Long userId) {
        // 1. Validate order
        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng"));

        // Verify ownership through customer
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));
        
        if (!order.getCustomer().getUser().getId().equals(userId)) {
            return ApiResponse.error("Bạn không có quyền thanh toán đơn hàng này");
        }

        // 2. Check if payment already exists
        if (paymentRepository.findByOrderId(order.getId()).isPresent()) {
            return ApiResponse.error("Đơn hàng này đã có thanh toán");
        }

        // 3. Validate amount
        if (!request.getAmount().equals(order.getTotal())) {
            return ApiResponse.error("Số tiền thanh toán không khớp với đơn hàng");
        }

        // 4. Generate payment code
        String paymentCode = generatePaymentCode();

        // 5. Get bank account from database (default account)
        com.doan.WEB_TMDT.module.payment.entity.BankAccount bankAccount = 
            bankAccountRepository.findByIsDefaultTrue()
                .orElse(null);
        
        // Fallback to config if no default account
        String bankCode = bankAccount != null ? bankAccount.getBankCode() : sepayBankCode;
        String accountNumber = bankAccount != null ? bankAccount.getAccountNumber() : sepayAccountNumber;
        String accountName = bankAccount != null ? bankAccount.getAccountName() : sepayAccountName;
        
        log.info("Using bank account: {} - {} - {}", bankCode, accountNumber, accountName);

        // 6. Generate QR Code URL (SePay format)
        String qrCodeUrl = generateSepayQrCode(paymentCode, request.getAmount(), bankCode, accountNumber, accountName);

        // 7. Create payment
        Payment payment = Payment.builder()
                .paymentCode(paymentCode)
                .order(order)
                .user(user)
                .amount(request.getAmount())
                .method(PaymentMethod.SEPAY)
                .status(PaymentStatus.PENDING)
                .sepayBankCode(bankCode)
                .sepayAccountNumber(accountNumber)
                .sepayAccountName(accountName)
                .sepayContent(paymentCode)
                .sepayQrCode(qrCodeUrl)
                .build();

        Payment savedPayment = paymentRepository.save(payment);

        // 7. Update order
        order.setPaymentId(savedPayment.getId());
        order.setPaymentStatus(com.doan.WEB_TMDT.module.order.entity.PaymentStatus.PENDING);
        orderRepository.save(order);

        // 8. Build response
        PaymentResponse response = PaymentResponse.builder()
                .paymentId(savedPayment.getId())
                .paymentCode(savedPayment.getPaymentCode())
                .amount(savedPayment.getAmount())
                .status(savedPayment.getStatus().name())
                .bankCode(savedPayment.getSepayBankCode())
                .accountNumber(savedPayment.getSepayAccountNumber())
                .accountName(savedPayment.getSepayAccountName())
                .content(savedPayment.getSepayContent())
                .qrCodeUrl(savedPayment.getSepayQrCode())
                .expiredAt(savedPayment.getExpiredAt().toString())
                .message("Vui lòng quét mã QR hoặc chuyển khoản với nội dung: " + paymentCode)
                .build();

        return ApiResponse.success("Tạo thanh toán thành công", response);
    }

    @Override
    public ApiResponse getPaymentByCode(String paymentCode) {
        Payment payment = paymentRepository.findByPaymentCode(paymentCode)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thanh toán"));

        PaymentResponse response = toPaymentResponse(payment);
        return ApiResponse.success("Thông tin thanh toán", response);
    }

    @Override
    public ApiResponse getPaymentByOrderId(Long orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thanh toán cho đơn hàng này"));

        PaymentResponse response = toPaymentResponse(payment);
        return ApiResponse.success("Thông tin thanh toán", response);
    }

    @Override
    public ApiResponse getPaymentsByUser(Long userId) {
        List<Payment> payments = paymentRepository.findByUserId(userId);
        List<PaymentResponse> responses = payments.stream()
                .map(this::toPaymentResponse)
                .collect(Collectors.toList());
        return ApiResponse.success("Danh sách thanh toán", responses);
    }

    @Override
    @Transactional
    public ApiResponse handleSepayWebhook(SepayWebhookRequest request) {
        log.info("Received SePay webhook: {}", request);

        try {
            // 1. Quick validation - reject if content doesn't contain "PAY"
            String content = request.getContent();
            if (content == null || !content.contains("PAY")) {
                log.warn("Webhook rejected - content doesn't contain payment code: {}", content);
                return ApiResponse.error("Nội dung không chứa mã thanh toán");
            }

            // 2. Extract payment code from content (may have extra text like "PAY202511277791 FT2533..")
            String paymentCode = extractPaymentCode(content);
            
            log.info("Extracted payment code: {} from content: {}", paymentCode, content);

            // 3. Find payment by payment code
            Payment payment = paymentRepository.findByPaymentCode(paymentCode)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy thanh toán với mã: " + paymentCode));

            // 4. Get bank account to verify signature
            com.doan.WEB_TMDT.module.payment.entity.BankAccount bankAccount = 
                bankAccountRepository.findByIsDefaultTrue().orElse(null);
            
            // 5. Verify signature (if bank account has API token)
            if (bankAccount != null && bankAccount.getSepayApiToken() != null && !bankAccount.getSepayApiToken().isEmpty()) {
                if (!verifySignature(request, bankAccount.getSepayApiToken())) {
                    log.error("Invalid signature from SePay webhook");
                    return ApiResponse.error("Chữ ký không hợp lệ");
                }
            } else {
                log.warn("No SePay API token configured for default bank account - skipping signature verification");
            }

            // 6. Check if already processed
            if (payment.getStatus() == PaymentStatus.SUCCESS) {
                log.warn("Payment already processed: {}", payment.getPaymentCode());
                return ApiResponse.success("Thanh toán đã được xử lý");
            }

            // 7. Check amount
            if (!payment.getAmount().equals(request.getAmount())) {
                log.error("Amount mismatch. Expected: {}, Received: {}", payment.getAmount(), request.getAmount());
                return ApiResponse.error("Số tiền không khớp");
            }

            // 8. Check if expired
            if (LocalDateTime.now().isAfter(payment.getExpiredAt())) {
                payment.setStatus(PaymentStatus.EXPIRED);
                payment.setFailureReason("Thanh toán đã hết hạn");
                paymentRepository.save(payment);
                return ApiResponse.error("Thanh toán đã hết hạn");
            }

            // 9. Update payment
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setSepayTransactionId(request.getTransactionId());
            payment.setPaidAt(LocalDateTime.now());
            payment.setSepayResponse(request.toString());
            paymentRepository.save(payment);

            // 10. Update order: PENDING_PAYMENT → CONFIRMED (tự động xác nhận, chờ chuẩn bị hàng)
            Order order = payment.getOrder();
            com.doan.WEB_TMDT.module.order.entity.OrderStatus oldStatus = order.getStatus();
            
            order.setPaymentStatus(com.doan.WEB_TMDT.module.order.entity.PaymentStatus.PAID);
            // Note: paidAt được lưu trong Payment entity, không cần lưu trong Order
            order.setStatus(com.doan.WEB_TMDT.module.order.entity.OrderStatus.CONFIRMED);
            order.setConfirmedAt(LocalDateTime.now());
            orderRepository.save(order);

            // 11. Publish event for accounting module
            try {
                OrderStatusChangedEvent event = new OrderStatusChangedEvent(
                    this, order, oldStatus, order.getStatus()
                );
                eventPublisher.publishEvent(event);
                log.info("Published OrderStatusChangedEvent for order: {} ({} -> {})", 
                    order.getOrderCode(), oldStatus, order.getStatus());
            } catch (Exception e) {
                log.error("Failed to publish OrderStatusChangedEvent for order: {}", order.getOrderCode(), e);
                // Don't fail the payment process if event publishing fails
            }

            log.info("Payment processed successfully: {}", payment.getPaymentCode());

            // TODO: Send email confirmation
            // TODO: Send notification

            return ApiResponse.success("Xử lý thanh toán thành công");

        } catch (Exception e) {
            log.error("Error processing SePay webhook", e);
            return ApiResponse.error("Lỗi xử lý webhook: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public ApiResponse checkPaymentStatus(String paymentCode) {
        Payment payment = paymentRepository.findByPaymentCode(paymentCode)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thanh toán"));

        // Check if expired - realtime check
        if (payment.getStatus() == PaymentStatus.PENDING && 
            LocalDateTime.now().isAfter(payment.getExpiredAt())) {
            
            log.info("Payment {} expired, processing cancellation...", paymentCode);
            
            // 1. Cập nhật Payment → EXPIRED
            payment.setStatus(PaymentStatus.EXPIRED);
            payment.setFailureReason("Không thanh toán trong thời gian quy định");
            paymentRepository.save(payment);
            
            // 2. Gọi hàm hủy đơn của khách hàng (sẽ tự động giải phóng stock)
            Order order = payment.getOrder();
            if (order != null && order.getStatus() == com.doan.WEB_TMDT.module.order.entity.OrderStatus.PENDING_PAYMENT) {
                try {
                    Long customerId = order.getCustomer().getId();
                    orderService.cancelOrderByCustomer(order.getId(), customerId, "Không thanh toán trong thời gian quy định");
                    log.info("Cancelled order {} due to payment expiration (realtime check)", order.getOrderCode());
                } catch (Exception e) {
                    log.error("Failed to cancel order {} due to payment expiration: {}", 
                        order.getOrderCode(), e.getMessage());
                }
            }
        }

        PaymentResponse response = toPaymentResponse(payment);
        return ApiResponse.success("Trạng thái thanh toán", response);
    }

    @Override
    @Transactional
    public void expireOldPayments() {
        LocalDateTime now = LocalDateTime.now();
        
        // Tìm các payment PENDING đã quá hạn
        List<Payment> expiredPayments = paymentRepository
                .findByStatusAndExpiredAtBefore(PaymentStatus.PENDING, now);

        log.info("Found {} expired payments to process", expiredPayments.size());

        for (Payment payment : expiredPayments) {
            // 1. Cập nhật Payment → EXPIRED
            payment.setStatus(PaymentStatus.EXPIRED);
            payment.setFailureReason("Không thanh toán trong thời gian quy định");
            paymentRepository.save(payment);

            // 2. Gọi hàm hủy đơn của khách hàng (sẽ tự động giải phóng stock)
            Order order = payment.getOrder();
            if (order != null && order.getStatus() == com.doan.WEB_TMDT.module.order.entity.OrderStatus.PENDING_PAYMENT) {
                try {
                    Long customerId = order.getCustomer().getId();
                    orderService.cancelOrderByCustomer(order.getId(), customerId, "Không thanh toán trong thời gian quy định");
                    log.info("Cancelled order {} due to payment expiration", order.getOrderCode());
                } catch (Exception e) {
                    log.error("Failed to cancel order {} due to payment expiration: {}", 
                        order.getOrderCode(), e.getMessage());
                }
            }
        }

        if (!expiredPayments.isEmpty()) {
            log.info("Expired {} old payments and cancelled their orders", expiredPayments.size());
        }
    }

    @Override
    @Transactional
    public void deletePaymentByOrderId(Long orderId) {
        paymentRepository.findByOrderId(orderId).ifPresent(payment -> {
            log.info("Deleting payment {} for order {}", payment.getPaymentCode(), orderId);
            paymentRepository.delete(payment);
        });
    }

    // Helper methods

    private String generatePaymentCode() {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        int random = new Random().nextInt(9999);
        String code = "PAY" + date + String.format("%04d", random);

        // Check if exists
        if (paymentRepository.existsByPaymentCode(code)) {
            return generatePaymentCode(); // Retry
        }

        return code;
    }

    private String generateSepayQrCode(String content, Double amount, String bankCode, String accountNumber, String accountName) {
        // VietQR format - chỉ hiện mã QR, không có text
        // Template: qr_only - sạch sẽ, chỉ có mã QR
        long amountInVnd = Math.round(amount * amountMultiplier);
        
        log.info("Generating QR code - Bank: {}, Account: {}, Amount: {} VND", 
                 bankCode, accountNumber, amountInVnd);
        
        // Sử dụng 'qr_only' - chỉ có mã QR, không có thông tin ngân hàng
        return String.format(
                "https://img.vietqr.io/image/%s-%s-qr_only.jpg?amount=%d&addInfo=%s&accountName=%s",
                bankCode,
                accountNumber,
                amountInVnd,
                content,
                accountName.replace(" ", "%20")
        );
    }

    private boolean verifySignature(SepayWebhookRequest request, String apiToken) {
        // TODO: Implement real signature verification based on SePay documentation
        // String data = request.getTransactionId() + request.getAmount() + request.getContent() + apiToken;
        // String calculatedSignature = DigestUtils.sha256Hex(data);
        // return calculatedSignature.equals(request.getSignature());

        // For demo, always return true
        log.info("Verifying signature with API token: {}...", apiToken.substring(0, Math.min(10, apiToken.length())));
        return true;
    }

    /**
     * Extract payment code from transaction content
     * Content may be: "PAY202511277791" or "PAY202511277791 FT2533.."
     */
    private String extractPaymentCode(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        
        // Tìm xâu con bắt đầu bằng "PAY"
        int index = content.indexOf("PAY");
        if (index != -1) {
            // Lấy từ vị trí PAY, tối đa 15 ký tự (PAY + 12 số)
            int endIndex = Math.min(index + 15, content.length());
            String extracted = content.substring(index, endIndex).split("\\s+")[0];
            return extracted;
        }
        
        return content.trim();
    }

    private PaymentResponse toPaymentResponse(Payment payment) {
        return PaymentResponse.builder()
                .paymentId(payment.getId())
                .paymentCode(payment.getPaymentCode())
                .amount(payment.getAmount())
                .status(payment.getStatus().name())
                .bankCode(payment.getSepayBankCode())
                .accountNumber(payment.getSepayAccountNumber())
                .accountName(payment.getSepayAccountName())
                .content(payment.getSepayContent())
                .qrCodeUrl(payment.getSepayQrCode())
                .expiredAt(payment.getExpiredAt().toString())
                .build();
    }
}
