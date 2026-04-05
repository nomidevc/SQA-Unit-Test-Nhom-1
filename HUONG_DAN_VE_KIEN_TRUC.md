# HƯỚNG DẪN VẼ KIẾN TRÚC HỆ THỐNG TRÊN DRAW.IO

## BƯỚC 1: MỞ DRAW.IO
1. Truy cập: https://app.diagrams.net/
2. Chọn "Create New Diagram"
3. Chọn "Blank Diagram"

---

## BƯỚC 2: VẼ LAYER CLIENT (Tầng Giao Diện)

### Hình chữ nhật lớn (Container)
- **Shape**: Rectangle (bo góc)
- **Size**: 800px x 200px
- **Fill**: Màu xanh nhạt (#E3F2FD)
- **Border**: Màu xanh đậm (#1976D2), độ dày 2px
- **Text**: "CLIENT LAYER" (in đậm, size 16)

### Bên trong: Hình icon máy tính
- **Shape**: Rectangle nhỏ
- **Size**: 150px x 100px
- **Fill**: Trắng (#FFFFFF)
- **Border**: Màu xám (#757575)
- **Text**: 
  ```
  Web Browser
  (Next.js/React)
  ```
- **Icon**: Thêm icon máy tính từ thư viện (hoặc vẽ đơn giản)

**Giải thích**: 
- Đây là tầng người dùng tương tác
- Giao diện web chạy trên trình duyệt
- Sử dụng Next.js (React framework)

---

## BƯỚC 3: VẼ MŨI TÊN KẾT NỐI

### Mũi tên từ Client xuống Application
- **Shape**: Arrow (mũi tên 2 chiều)
- **Style**: Đường thẳng, mũi tên to
- **Color**: Màu đen (#000000)
- **Label**: "HTTPS / REST API" (màu đỏ, in đậm)

**Giải thích**:
- Client gọi API qua giao thức HTTPS
- Sử dụng REST API (GET, POST, PUT, DELETE)
- Giao tiếp 2 chiều (request/response)

---

## BƯỚC 4: VẼ LAYER APPLICATION (Tầng Ứng Dụng)

### Hình chữ nhật lớn
- **Shape**: Rectangle (bo góc)
- **Size**: 800px x 400px
- **Fill**: Màu cam nhạt (#FFF3E0)
- **Border**: Màu cam đậm (#F57C00), độ dày 2px
- **Text**: "APPLICATION LAYER" (in đậm, size 16)

### Bên trong: Hình Spring Boot
- **Shape**: Rectangle
- **Size**: 300px x 150px
- **Fill**: Màu xanh lá nhạt (#E8F5E9)
- **Border**: Màu xanh lá (#4CAF50)
- **Text**:
  ```
  Spring Boot API
  (Java 17)
  
  • Controllers
  • Services
  • Security (JWT)
  ```

### Bên cạnh: 8 Module nhỏ (2 hàng x 4 cột)
Mỗi module:
- **Shape**: Rectangle nhỏ
- **Size**: 150px x 80px
- **Fill**: Màu trắng (#FFFFFF)
- **Border**: Màu xanh dương (#2196F3)
- **Text**: Tên module (Auth, Product, Order, Inventory, Payment, Review, Cart, Shipping)

**Giải thích**:
- Tầng xử lý logic nghiệp vụ
- Spring Boot: Framework Java để xây dựng API
- Controllers: Nhận request từ client
- Services: Xử lý logic nghiệp vụ
- Security: Xác thực JWT (JSON Web Token)
- 8 module độc lập, dễ bảo trì

---

## BƯỚC 5: VẼ MŨI TÊN KẾT NỐI

### Mũi tên từ Application xuống Database
- **Shape**: Arrow (mũi tên 2 chiều)
- **Style**: Đường thẳng
- **Color**: Màu đen
- **Label**: "JPA / Hibernate" (màu xanh lá)

**Giải thích**:
- JPA: Java Persistence API (chuẩn ORM của Java)
- Hibernate: Thư viện ORM để mapping Object ↔ Database
- Tự động chuyển đổi giữa Java Object và SQL

---

## BƯỚC 6: VẼ LAYER DATA (Tầng Dữ Liệu)

### Hình chữ nhật lớn
- **Shape**: Rectangle (bo góc)
- **Size**: 800px x 200px
- **Fill**: Màu tím nhạt (#F3E5F5)
- **Border**: Màu tím đậm (#9C27B0), độ dày 2px
- **Text**: "DATA LAYER" (in đậm, size 16)

### Bên trong: Hình Database
- **Shape**: Cylinder (hình trụ - biểu tượng database)
- **Size**: 150px x 100px
- **Fill**: Màu xanh dương đậm (#1565C0)
- **Text Color**: Trắng
- **Text**:
  ```
  MySQL Database
  (Port 3306)
  ```

**Giải thích**:
- Tầng lưu trữ dữ liệu
- MySQL: Hệ quản trị cơ sở dữ liệu quan hệ
- Port 3306: Cổng mặc định của MySQL
- Lưu trữ: Users, Products, Orders, Payments, Reviews...

---

## BƯỚC 7: VẼ EXTERNAL SERVICES (Dịch Vụ Bên Ngoài)

### Hình chữ nhật lớn
- **Shape**: Rectangle (bo góc, viền đứt nét)
- **Size**: 800px x 150px
- **Fill**: Màu vàng nhạt (#FFF9C4)
- **Border**: Màu vàng đậm (#F57F17), viền đứt nét
- **Text**: "EXTERNAL SERVICES" (in đậm, size 16)

### Bên trong: 3 service nhỏ
Mỗi service:
- **Shape**: Rectangle
- **Size**: 200px x 80px
- **Fill**: Trắng
- **Border**: Màu cam (#FF9800)

**Service 1: SePay**
- **Text**: 
  ```
  SePay
  Payment Gateway
  ```
- **Icon**: Thêm icon tiền/thẻ

**Service 2: GHN**
- **Text**:
  ```
  Giao Hàng Nhanh
  Shipping Service
  ```
- **Icon**: Thêm icon xe tải

**Service 3: Email**
- **Text**:
  ```
  Email Service
  (SMTP)
  ```
- **Icon**: Thêm icon email

### Mũi tên kết nối
- Từ Application Layer → External Services
- **Label**: "API Calls" (màu đỏ)

**Giải thích**:
- SePay: Cổng thanh toán trực tuyến (chuyển khoản ngân hàng)
- GHN: Dịch vụ vận chuyển (tạo đơn, tracking)
- Email: Gửi thông báo cho khách hàng (SMTP: Simple Mail Transfer Protocol)

---

## BƯỚC 8: THÊM CHÚ THÍCH

### Box chú thích (góc phải)
- **Shape**: Rectangle
- **Size**: 250px x 200px
- **Fill**: Màu xám nhạt (#F5F5F5)
- **Border**: Màu xám (#9E9E9E), viền đứt nét
- **Text**:
  ```
  CÔNG NGHỆ
  
  Frontend:
  • Next.js 14
  • TypeScript
  • TailwindCSS
  
  Backend:
  • Spring Boot 3.x
  • Java 17
  • MySQL 8.0
  
  Security:
  • JWT Token
  • Spring Security
  ```

---

## BƯỚC 9: THÊM LUỒNG DỮ LIỆU (Tùy chọn)

### Vẽ sequence diagram đơn giản bên cạnh
1. **User** → **Client**: Truy cập website
2. **Client** → **API**: Gửi request (HTTPS)
3. **API** → **Database**: Truy vấn dữ liệu
4. **Database** → **API**: Trả về dữ liệu
5. **API** → **Client**: Response (JSON)
6. **Client** → **User**: Hiển thị kết quả

---

## MÀU SẮC CHUẨN

| Layer | Màu nền | Màu viền |
|-------|---------|----------|
| Client | #E3F2FD (xanh nhạt) | #1976D2 (xanh đậm) |
| Application | #FFF3E0 (cam nhạt) | #F57C00 (cam đậm) |
| Data | #F3E5F5 (tím nhạt) | #9C27B0 (tím đậm) |
| External | #FFF9C4 (vàng nhạt) | #F57F17 (vàng đậm) |

---

## GIẢI THÍCH CHI TIẾT TỪNG THÀNH PHẦN

### 1. CLIENT LAYER (Tầng Giao Diện)
**Chức năng**: 
- Giao diện người dùng tương tác
- Hiển thị dữ liệu, nhận input từ user
- Gửi request đến server

**Công nghệ**:
- **Next.js**: Framework React với SSR (Server-Side Rendering)
- **TypeScript**: JavaScript có kiểu dữ liệu, giảm lỗi
- **TailwindCSS**: Framework CSS tiện lợi

**Ví dụ**: Trang chủ, trang sản phẩm, giỏ hàng, thanh toán...

---

### 2. APPLICATION LAYER (Tầng Ứng Dụng)
**Chức năng**:
- Nhận request từ client
- Xử lý logic nghiệp vụ
- Truy vấn database
- Trả về response

**Cấu trúc**:
- **Controllers**: Nhận HTTP request, gọi service
- **Services**: Xử lý logic nghiệp vụ (tính toán, validation)
- **Repositories**: Truy vấn database
- **Security**: Xác thực JWT, phân quyền

**8 Module chính**:
1. **Auth**: Đăng nhập, đăng ký, JWT
2. **Product**: Quản lý sản phẩm, danh mục
3. **Order**: Quản lý đơn hàng
4. **Inventory**: Quản lý kho (nhập/xuất)
5. **Payment**: Thanh toán (COD, chuyển khoản)
6. **Review**: Đánh giá, bình luận
7. **Cart**: Giỏ hàng
8. **Shipping**: Giao hàng (nội bộ + GHN)

---

### 3. DATA LAYER (Tầng Dữ Liệu)
**Chức năng**:
- Lưu trữ dữ liệu lâu dài
- Đảm bảo tính toàn vẹn dữ liệu
- Hỗ trợ truy vấn nhanh

**Công nghệ**:
- **MySQL**: Database quan hệ (RDBMS)
- **JPA/Hibernate**: ORM (Object-Relational Mapping)

**Dữ liệu lưu trữ**:
- Users (khách hàng, nhân viên, admin)
- Products (sản phẩm, danh mục, hình ảnh)
- Orders (đơn hàng, chi tiết đơn)
- Inventory (tồn kho, phiếu nhập/xuất)
- Payments (thanh toán)
- Reviews (đánh giá)

---

### 4. EXTERNAL SERVICES (Dịch Vụ Bên Ngoài)
**Chức năng**: Tích hợp dịch vụ của bên thứ 3

**SePay**:
- Cổng thanh toán trực tuyến
- Hỗ trợ chuyển khoản ngân hàng
- Webhook để xác nhận thanh toán

**GHN (Giao Hàng Nhanh)**:
- Tạo đơn vận chuyển
- Tracking đơn hàng
- Tính phí ship

**Email Service**:
- Gửi email xác nhận đơn hàng
- Thông báo trạng thái đơn
- Gửi OTP, reset password

---

## TIPS VẼ ĐẸP

1. **Căn chỉnh**: Dùng Align tool để căn đều các phần tử
2. **Khoảng cách**: Giữ khoảng cách đều giữa các layer (50-80px)
3. **Font chữ**: Dùng Arial hoặc Helvetica, size 12-16
4. **Màu sắc**: Dùng màu pastel (nhạt) cho nền, màu đậm cho viền
5. **Mũi tên**: Dùng mũi tên to, rõ ràng, có label
6. **Icon**: Thêm icon để dễ nhận biết (máy tính, database, email...)
7. **Chú thích**: Thêm legend/chú thích ở góc để giải thích

---

## KẾT QUẢ MONG ĐỢI

Sau khi vẽ xong, bạn sẽ có:
- ✅ Sơ đồ kiến trúc 3 tầng rõ ràng
- ✅ Thể hiện được luồng dữ liệu
- ✅ Dễ hiểu, dễ trình bày
- ✅ Chuyên nghiệp, đẹp mắt

**Thời gian**: 15-20 phút
**Độ khó**: Dễ (chỉ cần vẽ hình chữ nhật và mũi tên)

---

## FILE MẪU

Bạn có thể tham khảo template có sẵn trên draw.io:
1. Vào draw.io
2. Chọn "File" → "Open Library"
3. Tìm "AWS Architecture" hoặc "Network Diagram"
4. Sử dụng các shape có sẵn

Hoặc import file XML này vào draw.io (nếu cần tôi có thể tạo).

---

## SƠ ĐỒ MẪU (ASCII ART)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                                                                               │
│                            CLIENT LAYER                                       │
│                         (Màu xanh nhạt #E3F2FD)                              │
│                                                                               │
│                    ┌────────────────────────────┐                            │
│                    │      💻 Web Browser        │                            │
│                    │                            │                            │
│                    │      Next.js / React       │                            │
│                    │      TypeScript            │                            │
│                    │      TailwindCSS           │                            │
│                    └────────────────────────────┘                            │
│                                                                               │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    │  ⬇ HTTPS / REST API
                                    │  (GET, POST, PUT, DELETE)
                                    ↓
┌─────────────────────────────────────────────────────────────────────────────┐
│                                                                               │
│                         APPLICATION LAYER                                     │
│                        (Màu cam nhạt #FFF3E0)                                │
│                                                                               │
│    ┌──────────────────────────────────────────────────────────────┐         │
│    │              🍃 Spring Boot API (Java 17)                     │         │
│    │                                                               │         │
│    │    • Controllers  (Nhận HTTP Request)                        │         │
│    │    • Services     (Xử lý logic nghiệp vụ)                    │         │
│    │    • Repositories (Truy vấn database)                        │         │
│    │    • Security     (JWT Authentication)                       │         │
│    └──────────────────────────────────────────────────────────────┘         │
│                                                                               │
│    ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐                 │
│    │   Auth   │  │ Product  │  │  Order   │  │Inventory │                 │
│    │  Module  │  │  Module  │  │  Module  │  │  Module  │                 │
│    └──────────┘  └──────────┘  └──────────┘  └──────────┘                 │
│                                                                               │
│    ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐                 │
│    │ Payment  │  │  Review  │  │   Cart   │  │ Shipping │                 │
│    │  Module  │  │  Module  │  │  Module  │  │  Module  │                 │
│    └──────────┘  └──────────┘  └──────────┘  └──────────┘                 │
│                                                                               │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    │  ⬇ JPA / Hibernate
                                    │  (ORM - Object Relational Mapping)
                                    ↓
┌─────────────────────────────────────────────────────────────────────────────┐
│                                                                               │
│                            DATA LAYER                                         │
│                         (Màu tím nhạt #F3E5F5)                               │
│                                                                               │
│                         ┌──────────────────┐                                 │
│                         │   🗄️ MySQL       │                                 │
│                         │   Database       │                                 │
│                         │   (Port 3306)    │                                 │
│                         │                  │                                 │
│                         │  • Users         │                                 │
│                         │  • Products      │                                 │
│                         │  • Orders        │                                 │
│                         │  • Inventory     │                                 │
│                         │  • Payments      │                                 │
│                         │  • Reviews       │                                 │
│                         └──────────────────┘                                 │
│                                                                               │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    │  ⬇ API Calls (HTTP/HTTPS)
                                    ↓
┌─────────────────────────────────────────────────────────────────────────────┐
│                                                                               │
│                         EXTERNAL SERVICES                                     │
│                      (Màu vàng nhạt #FFF9C4 - viền đứt nét)                 │
│                                                                               │
│    ┌──────────────┐      ┌──────────────┐      ┌──────────────┐            │
│    │   💳 SePay   │      │   🚚 GHN     │      │   📧 Email   │            │
│    │              │      │              │      │              │            │
│    │   Payment    │      │   Shipping   │      │   Service    │            │
│    │   Gateway    │      │   Service    │      │   (SMTP)     │            │
│    │              │      │              │      │              │            │
│    │ • Chuyển khoản│      │ • Tạo đơn    │      │ • Xác nhận   │            │
│    │ • Webhook    │      │ • Tracking   │      │ • Thông báo  │            │
│    └──────────────┘      └──────────────┘      └──────────────┘            │
│                                                                               │
└─────────────────────────────────────────────────────────────────────────────┘


┌─────────────────────────────────────────────────────────────────────────────┐
│                          CHÚ THÍCH / LEGEND                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                               │
│  ⬇  : Luồng dữ liệu một chiều                                               │
│  ⬍⬍ : Luồng dữ liệu hai chiều                                               │
│  💻  : Client / Browser                                                      │
│  🍃  : Spring Boot                                                           │
│  🗄️  : Database                                                              │
│  💳  : Payment Service                                                       │
│  🚚  : Shipping Service                                                      │
│  📧  : Email Service                                                         │
│                                                                               │
│  CÔNG NGHỆ:                                                                  │
│  • Frontend: Next.js 14, TypeScript, TailwindCSS                            │
│  • Backend: Spring Boot 3.x, Java 17, Spring Security                       │
│  • Database: MySQL 8.0, JPA/Hibernate                                       │
│  • Security: JWT Token, BCrypt                                               │
│  • External: SePay API, GHN API, SMTP                                       │
│                                                                               │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## SƠ ĐỒ CHI TIẾT MODULE (Bên trong Application Layer)

```
┌────────────────────────────────────────────────────────────────────┐
│                    SPRING BOOT APPLICATION                          │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────┐   │
│  │                    SECURITY LAYER                           │   │
│  │  • JWT Authentication Filter                                │   │
│  │  • Spring Security Configuration                            │   │
│  │  • Role-based Access Control (RBAC)                         │   │
│  └────────────────────────────────────────────────────────────┘   │
│                              ↓                                      │
│  ┌────────────────────────────────────────────────────────────┐   │
│  │                    CONTROLLER LAYER                         │   │
│  │  • AuthController                                           │   │
│  │  • ProductController                                        │   │
│  │  • OrderController                                          │   │
│  │  • InventoryController                                      │   │
│  │  • PaymentController                                        │   │
│  │  • ReviewController                                         │   │
│  │  • CartController                                           │   │
│  │  • ShippingController                                       │   │
│  └────────────────────────────────────────────────────────────┘   │
│                              ↓                                      │
│  ┌────────────────────────────────────────────────────────────┐   │
│  │                     SERVICE LAYER                           │   │
│  │  • Business Logic                                           │   │
│  │  • Data Validation                                          │   │
│  │  • Transaction Management                                   │   │
│  │  • External API Integration                                 │   │
│  └────────────────────────────────────────────────────────────┘   │
│                              ↓                                      │
│  ┌────────────────────────────────────────────────────────────┐   │
│  │                   REPOSITORY LAYER                          │   │
│  │  • Spring Data JPA                                          │   │
│  │  • Custom Queries                                           │   │
│  │  • Database Operations (CRUD)                               │   │
│  └────────────────────────────────────────────────────────────┘   │
│                              ↓                                      │
│                         MySQL Database                              │
└────────────────────────────────────────────────────────────────────┘
```

---

## LUỒNG XỬ LÝ REQUEST (Sequence Diagram)

```
User          Client         API           Service        Database      External
 │              │             │              │               │             │
 │─── Click ───>│             │              │               │             │
 │              │             │              │               │             │
 │              │─── HTTP ───>│              │               │             │
 │              │   Request   │              │               │             │
 │              │             │              │               │             │
 │              │             │─── Call ────>│               │             │
 │              │             │   Service    │               │             │
 │              │             │              │               │             │
 │              │             │              │─── Query ────>│             │
 │              │             │              │               │             │
 │              │             │              │<─── Data ─────│             │
 │              │             │              │               │             │
 │              │             │              │─── API Call ─────────────>│
 │              │             │              │               │             │
 │              │             │              │<─── Response ──────────────│
 │              │             │              │               │             │
 │              │             │<─── Return ──│               │             │
 │              │             │   Data       │               │             │
 │              │             │              │               │             │
 │              │<─── JSON ───│              │               │             │
 │              │   Response  │              │               │             │
 │              │             │              │               │             │
 │<─── Display ─│             │              │               │             │
 │     Result   │             │              │               │             │
```

**Giải thích luồng**:
1. User click vào nút trên giao diện
2. Client gửi HTTP request đến API
3. API gọi Service xử lý logic
4. Service truy vấn Database
5. Service gọi External API (nếu cần)
6. Service trả kết quả về API
7. API trả JSON response về Client
8. Client hiển thị kết quả cho User

---

## VÍ DỤ CỤ THỂ: LUỒNG ĐẶT HÀNG

```
┌──────────┐     ┌──────────┐     ┌──────────┐     ┌──────────┐     ┌──────────┐
│ Customer │     │  Client  │     │   API    │     │ Database │     │  SePay   │
└────┬─────┘     └────┬─────┘     └────┬─────┘     └────┬─────┘     └────┬─────┘
     │                │                 │                │                 │
     │ 1. Chọn SP     │                 │                │                 │
     │───────────────>│                 │                │                 │
     │                │                 │                │                 │
     │ 2. Thêm giỏ    │                 │                │                 │
     │───────────────>│ 3. POST /cart   │                │                 │
     │                │────────────────>│ 4. Save cart   │                 │
     │                │                 │───────────────>│                 │
     │                │                 │                │                 │
     │ 5. Thanh toán  │                 │                │                 │
     │───────────────>│ 6. POST /order  │                │                 │
     │                │────────────────>│ 7. Create order│                 │
     │                │                 │───────────────>│                 │
     │                │                 │                │                 │
     │                │                 │ 8. Create payment                │
     │                │                 │─────────────────────────────────>│
     │                │                 │                │                 │
     │                │                 │ 9. Payment URL │                 │
     │                │                 │<─────────────────────────────────│
     │                │                 │                │                 │
     │                │ 10. Redirect    │                │                 │
     │                │<────────────────│                │                 │
     │                │                 │                │                 │
     │ 11. Chuyển khoản                 │                │                 │
     │──────────────────────────────────────────────────────────────────>│
     │                │                 │                │                 │
     │                │                 │ 12. Webhook (confirm)            │
     │                │                 │<─────────────────────────────────│
     │                │                 │                │                 │
     │                │                 │ 13. Update status                │
     │                │                 │───────────────>│                 │
     │                │                 │                │                 │
     │ 14. Email xác nhận               │                │                 │
     │<─────────────────────────────────│                │                 │
     │                │                 │                │                 │
```

---

## BẢNG MÀU CHUẨN (Copy để dùng trong draw.io)

| Thành phần | Màu nền | Mã màu | Màu viền | Mã màu |
|------------|---------|--------|----------|--------|
| Client Layer | Xanh nhạt | `#E3F2FD` | Xanh đậm | `#1976D2` |
| Application Layer | Cam nhạt | `#FFF3E0` | Cam đậm | `#F57C00` |
| Data Layer | Tím nhạt | `#F3E5F5` | Tím đậm | `#9C27B0` |
| External Services | Vàng nhạt | `#FFF9C4` | Vàng đậm | `#F57F17` |
| Module boxes | Trắng | `#FFFFFF` | Xanh dương | `#2196F3` |
| Database | Xanh đậm | `#1565C0` | Xanh đen | `#0D47A1` |
| Text chính | Đen | `#000000` | - | - |
| Text phụ | Xám đậm | `#424242` | - | - |
| Mũi tên | Đen | `#000000` | - | - |
| Label mũi tên | Đỏ | `#D32F2F` | - | - |

---

**Lưu ý**: Sơ đồ ASCII art này chỉ để tham khảo. Khi vẽ trên draw.io, bạn sẽ có giao diện đẹp hơn nhiều với màu sắc, icon, và căn chỉnh chuyên nghiệp!
