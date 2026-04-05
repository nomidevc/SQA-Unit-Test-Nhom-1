# OUTLINE SLIDE BÁO CÁO ĐỒ ÁN
## Website Bán Đồ Công Nghệ (15-18 slides)

---

## **SLIDE 1: TRANG BÌA**
**Layout**: Trung tâm, đơn giản

**Nội dung**:
```
ĐỒ ÁN TỐT NGHIỆP
XÂY DỰNG WEBSITE BÁN ĐỒ CÔNG NGHỆ

Sinh viên thực hiện: [Tên bạn]
MSSV: [Mã số]
Giảng viên hướng dẫn: [Tên GV]

[Logo trường] - [Năm học]
```

**Hình ảnh**: 
- Logo trường (góc trên)
- Icon laptop/điện thoại (trang trí nhẹ)

**Màu sắc**: Xanh dương chủ đạo, chuyên nghiệp

---

## **SLIDE 2: MỤC LỤC**
**Layout**: 2 cột

**Nội dung**:
```
1. Giới thiệu đề tài
2. Mục tiêu & phạm vi
3. Công nghệ sử dụng
4. Kiến trúc hệ thống
5. Cơ sở dữ liệu
6. Chức năng chính
7. Demo giao diện
8. Kết quả đạt được
9. Kết luận & hướng phát triển
```

**Hình ảnh**: Icon nhỏ cho mỗi mục

---

## **SLIDE 3: GIỚI THIỆU ĐỀ TÀI**
**Layout**: Text bên trái, hình ảnh bên phải

**Nội dung**:
```
📌 BỐI CẢNH
• Thương mại điện tử phát triển mạnh
• Nhu cầu mua sắm trực tuyến tăng cao
• Cần hệ thống quản lý toàn diện

🎯 VẤN ĐỀ
• Quản lý kho hàng phức tạp
• Xử lý đơn hàng thủ công
• Thiếu tích hợp thanh toán/vận chuyển

💡 GIẢI PHÁP
Xây dựng website thương mại điện tử hoàn chỉnh
với quản lý tự động và tích hợp dịch vụ bên ngoài
```

**Hình ảnh**: 
- Biểu đồ tăng trưởng TMĐT
- Icon shopping cart

---

## **SLIDE 4: MỤC TIÊU & PHẠM VI**
**Layout**: 2 cột

**Cột trái - MỤC TIÊU**:
```
✅ Xây dựng website bán đồ công nghệ
✅ Quản lý sản phẩm, kho hàng
✅ Xử lý đơn hàng tự động
✅ Tích hợp thanh toán trực tuyến
✅ Tích hợp vận chuyển (GHN)
✅ Hỗ trợ nhiều vai trò người dùng
```

**Cột phải - PHẠM VI**:
```
👥 Đối tượng sử dụng:
• Khách hàng
• Admin
• Nhân viên (Kho, Bán hàng, Giao hàng)

📦 Sản phẩm:
• Laptop, Điện thoại, Phụ kiện
• Quản lý serial, bảo hành
```

---

## **SLIDE 5: CÔNG NGHỆ SỬ DỤNG**
**Layout**: 3 cột với icon

**Cột 1 - FRONTEND**:
```
💻 Frontend
• Next.js 14
• React 18
• TypeScript
• TailwindCSS
• Zustand
```
**Icon**: Logo Next.js, React

**Cột 2 - BACKEND**:
```
⚙️ Backend
• Spring Boot 3.x
• Java 17
• Spring Security
• JWT
• MySQL 8.0
```
**Icon**: Logo Spring Boot, Java

**Cột 3 - EXTERNAL**:
```
🔌 Dịch vụ bên ngoài
• SePay (Thanh toán)
• GHN (Vận chuyển)
• SMTP (Email)
```
**Icon**: Logo các dịch vụ

---

## **SLIDE 6: KIẾN TRÚC HỆ THỐNG**
**Layout**: Toàn màn hình

**Nội dung**: 
- Sơ đồ kiến trúc 3 tầng (vẽ từ draw.io)
- Client → Application → Database
- External Services

**Hình ảnh**: 
- Sơ đồ kiến trúc đã vẽ (chiếm 80% slide)
- Chú thích ngắn gọn

**Ghi chú nhỏ**:
```
Kiến trúc Monolithic - Module hóa rõ ràng
```

---

## **SLIDE 7: CƠ SỞ DỮ LIỆU**
**Layout**: Sơ đồ ERD hoặc bảng

**Nội dung**:
```
📊 CÁC BẢNG CHÍNH

Users & Auth          Products              Orders
• users               • products            • orders
• customers           • categories          • order_items
• employees           • product_images      • payments
                      • tech_specs          

Inventory             Reviews               Shipping
• stocks              • product_reviews     • shipper_assignments
• purchase_orders     • ratings             • ghn_orders
• export_orders       
• warranty_cards      
```

**Hình ảnh**: 
- Sơ đồ ERD đơn giản (nếu có)
- Hoặc bảng liệt kê

---

## **SLIDE 8: CHỨC NĂNG - KHÁCH HÀNG**
**Layout**: Text bên trái, screenshot bên phải

**Nội dung**:
```
🛒 CHỨC NĂNG CHO KHÁCH HÀNG

✅ Xem & tìm kiếm sản phẩm
   • Lọc theo thông số kỹ thuật
   • Tìm kiếm nâng cao

✅ Giỏ hàng thông minh
   • Chọn sản phẩm trước khi thanh toán
   • Cập nhật số lượng

✅ Đặt hàng & thanh toán
   • COD (Tiền mặt)
   • Chuyển khoản (SePay)

✅ Theo dõi đơn hàng
   • Real-time tracking
   • Thông tin shipper

✅ Đánh giá sản phẩm
   • Rating 1-5 sao
   • Comment & review
```

**Hình ảnh**: 
- Screenshot trang sản phẩm
- Screenshot giỏ hàng

---

## **SLIDE 9: CHỨC NĂNG - ADMIN**
**Layout**: Text bên trái, screenshot bên phải

**Nội dung**:
```
👨‍💼 CHỨC NĂNG CHO ADMIN

✅ Dashboard tổng quan
   • Thống kê doanh thu
   • Biểu đồ đơn hàng

✅ Quản lý người dùng
   • Khách hàng
   • Nhân viên
   • Phê duyệt đăng ký

✅ Quản lý toàn bộ hệ thống
   • Sản phẩm
   • Đơn hàng
   • Kho hàng
   • Thanh toán

✅ Báo cáo & thống kê
```

**Hình ảnh**: 
- Screenshot dashboard
- Screenshot quản lý

---

## **SLIDE 10: CHỨC NĂNG - NHÂN VIÊN**
**Layout**: 3 cột

**Cột 1 - KHO**:
```
📦 Nhân viên Kho
• Nhập hàng
• Xuất hàng
• Quản lý tồn kho
• Tạo serial
• Phiếu bảo hành
```

**Cột 2 - BÁN HÀNG**:
```
💼 Nhân viên Bán hàng
• Xem đơn hàng
• Cập nhật trạng thái
• Xem khách hàng
• Hỗ trợ khách hàng
```

**Cột 3 - GIAO HÀNG**:
```
🚚 Nhân viên Giao hàng
• Nhận đơn
• Giao hàng
• Cập nhật trạng thái
• Xác nhận giao thành công
```

**Hình ảnh**: Icon cho mỗi vai trò

---

## **SLIDE 11: TÍNH NĂNG NỔI BẬT (1)**
**Layout**: 2 cột với hình ảnh lớn

**Cột trái**:
```
🔐 BẢO MẬT & XÁC THỰC

• JWT Token Authentication
• Mã hóa mật khẩu (BCrypt)
• Phân quyền theo vai trò
• Session management
• HTTPS/SSL
```

**Cột phải**:
```
💳 THANH TOÁN ĐA DẠNG

• COD (Tiền mặt khi nhận hàng)
• Chuyển khoản ngân hàng (SePay)
• Webhook tự động xác nhận
• Tự động hủy đơn chưa thanh toán
```

**Hình ảnh**: 
- Icon khóa, shield
- Logo SePay

---

## **SLIDE 12: TÍNH NĂNG NỔI BẬT (2)**
**Layout**: 2 cột với hình ảnh

**Cột trái**:
```
🚚 VẬN CHUYỂN LINH HOẠT

• Giao hàng nội bộ (Shipper)
• Tích hợp GHN API
• Tracking real-time
• Tính phí tự động
• Hiển thị thông tin shipper
```

**Cột phải**:
```
📦 QUẢN LÝ KHO THÔNG MINH

• Nhập/xuất hàng tự động
• Quản lý serial number
• Phiếu bảo hành QR code
• Cảnh báo tồn kho
• Lịch sử nhập/xuất
```

**Hình ảnh**: 
- Logo GHN
- QR code mẫu

---

## **SLIDE 13: DEMO GIAO DIỆN (1) - KHÁCH HÀNG**
**Layout**: 4 ảnh nhỏ (2x2)

**Ảnh 1**: Trang chủ
**Ảnh 2**: Trang sản phẩm
**Ảnh 3**: Giỏ hàng
**Ảnh 4**: Thanh toán

**Chú thích ngắn** dưới mỗi ảnh

---

## **SLIDE 14: DEMO GIAO DIỆN (2) - ADMIN & NHÂN VIÊN**
**Layout**: 4 ảnh nhỏ (2x2)

**Ảnh 1**: Dashboard admin
**Ảnh 2**: Quản lý sản phẩm
**Ảnh 3**: Quản lý kho
**Ảnh 4**: Giao diện shipper

**Chú thích ngắn** dưới mỗi ảnh

---

## **SLIDE 15: KẾT QUẢ ĐẠT ĐƯỢC**
**Layout**: 2 cột với icon tick

**Cột trái - CHỨC NĂNG**:
```
✅ Hoàn thành đầy đủ chức năng
   • 8 module chính
   • 3 vai trò người dùng
   • 50+ API endpoints

✅ Giao diện thân thiện
   • Responsive design
   • UX/UI tối ưu
   • Đa ngôn ngữ (sẵn sàng)

✅ Tích hợp dịch vụ
   • SePay payment
   • GHN shipping
   • Email notification
```

**Cột phải - KỸ THUẬT**:
```
✅ Hiệu năng tốt
   • Load time < 2s
   • Tối ưu query database
   • Caching

✅ Bảo mật cao
   • JWT authentication
   • SQL injection prevention
   • XSS protection

✅ Dễ bảo trì
   • Code structure rõ ràng
   • Module hóa
   • Documentation đầy đủ
```

---

## **SLIDE 16: HẠNG CHẾ & HƯỚNG PHÁT TRIỂN**
**Layout**: 2 cột

**Cột trái - HẠN CHẾ**:
```
⚠️ HẠN CHẾ HIỆN TẠI

• Chưa có app mobile
• Chưa có AI recommendation
• Chưa có live chat
• Chưa có đa ngôn ngữ
• Chưa có phân tích dữ liệu nâng cao
```

**Cột phải - HƯỚNG PHÁT TRIỂN**:
```
🚀 HƯỚNG PHÁT TRIỂN

• Xây dựng mobile app (React Native)
• Tích hợp AI gợi ý sản phẩm
• Thêm live chat support
• Đa ngôn ngữ (Tiếng Anh, ...)
• Dashboard analytics nâng cao
• Tích hợp thêm payment gateway
• Microservices architecture
```

---

## **SLIDE 17: KẾT LUẬN**
**Layout**: Trung tâm, text lớn

**Nội dung**:
```
🎯 KẾT LUẬN

✅ Đã xây dựng thành công website thương mại điện tử
   hoàn chỉnh với đầy đủ chức năng quản lý

✅ Áp dụng công nghệ hiện đại (Next.js, Spring Boot)

✅ Tích hợp thành công các dịch vụ bên ngoài

✅ Giao diện thân thiện, dễ sử dụng

✅ Hệ thống ổn định, bảo mật cao

📌 Đề tài có tính ứng dụng thực tế cao,
   có thể triển khai cho doanh nghiệp nhỏ và vừa
```

---

## **SLIDE 18: CẢM ƠN & HỎI ĐÁP**
**Layout**: Trung tâm

**Nội dung**:
```
CẢM ƠN QUÝ THẦY CÔ
VÀ CÁC BẠN ĐÃ LẮNG NGHE!

❓ HỎI ĐÁP

---

Thông tin liên hệ:
📧 Email: [email của bạn]
📱 Phone: [số điện thoại]
🔗 GitHub: [link repository]
🌐 Demo: [link demo nếu có]
```

**Hình ảnh**: 
- Logo trường
- Icon Q&A

---

## TIPS THIẾT KẾ SLIDE

### Màu sắc
- **Màu chủ đạo**: Xanh dương (#1976D2)
- **Màu phụ**: Cam (#F57C00), Xanh lá (#4CAF50)
- **Background**: Trắng hoặc xám nhạt (#F5F5F5)
- **Text**: Đen (#000000) hoặc xám đậm (#424242)

### Font chữ
- **Tiêu đề**: Arial Bold, 32-40pt
- **Nội dung**: Arial Regular, 18-24pt
- **Chú thích**: Arial Regular, 14-16pt

### Layout
- **Margin**: 50px mỗi bên
- **Line spacing**: 1.5
- **Bullet points**: Tối đa 5-6 dòng/slide
- **Hình ảnh**: Chất lượng cao, không bị vỡ

### Animation (tùy chọn)
- **Entrance**: Fade in (nhẹ nhàng)
- **Emphasis**: Không dùng quá nhiều
- **Exit**: Không cần thiết

### Quy tắc 6-6-6
- Tối đa 6 bullet points
- Tối đa 6 từ mỗi bullet
- Tối đa 6 dòng text

---

## CHECKLIST TRƯỚC KHI TRÌNH BÀY

✅ Kiểm tra chính tả
✅ Kiểm tra hình ảnh rõ nét
✅ Test animation (nếu có)
✅ Chuẩn bị demo (video hoặc live)
✅ In handout (nếu cần)
✅ Backup file (USB + Cloud)
✅ Luyện tập trình bày (10-15 phút)

---

**Thời gian trình bày**: 10-15 phút
**Thời gian Q&A**: 5 phút
**Tổng**: 15-20 phút
