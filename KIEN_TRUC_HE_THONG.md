# KIẾN TRÚC HỆ THỐNG BÁN ĐỒ CÔNG NGHỆ

## KIẾN TRÚC TỔNG QUAN

```
┌─────────────────────────────────────────────────────────────────────┐
│                          CLIENT LAYER                                │
│                                                                       │
│                    ┌──────────────────────┐                          │
│                    │   Web Browser        │                          │
│                    │   (Next.js/React)    │                          │
│                    └──────────────────────┘                          │
│                                                                       │
└─────────────────────────────────────────────────────────────────────┘
                                  │
                                  │ HTTPS/REST API
                                  ↓
┌─────────────────────────────────────────────────────────────────────┐
│                      APPLICATION LAYER                               │
│                                                                       │
│                    ┌──────────────────────┐                          │
│                    │   Spring Boot API    │                          │
│                    │   (Java 17)          │                          │
│                    │                      │                          │
│                    │  • Controllers       │                          │
│                    │  • Services          │                          │
│                    │  • Security (JWT)    │                          │
│                    └──────────────────────┘                          │
│                                                                       │
└─────────────────────────────────────────────────────────────────────┘
                                  │
                                  │ JPA/Hibernate
                                  ↓
┌─────────────────────────────────────────────────────────────────────┐
│                         DATA LAYER                                   │
│                                                                       │
│                    ┌──────────────────────┐                          │
│                    │   MySQL Database     │                          │
│                    │   (Port 3306)        │                          │
│                    └──────────────────────┘                          │
│                                                                       │
└─────────────────────────────────────────────────────────────────────┘
                                  │
                                  │ API Calls
                                  ↓
┌─────────────────────────────────────────────────────────────────────┐
│                      EXTERNAL SERVICES                               │
│                                                                       │
│     ┌──────────────┐    ┌──────────────┐    ┌──────────────┐       │
│     │    SePay     │    │     GHN      │    │    Email     │       │
│     │   Payment    │    │   Shipping   │    │   Service    │       │
│     └──────────────┘    └──────────────┘    └──────────────┘       │
│                                                                       │
└─────────────────────────────────────────────────────────────────────┘
```

## CÁC MODULE CHÍNH (Tổng quan)

```
┌────────────────────────────────────────────────────────────────┐
│                    SPRING BOOT APPLICATION                      │
├────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐        │
│  │    Auth      │  │   Product    │  │   Order      │        │
│  │   Module     │  │   Module     │  │   Module     │        │
│  └──────────────┘  └──────────────┘  └──────────────┘        │
│                                                                 │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐        │
│  │  Inventory   │  │   Payment    │  │   Review     │        │
│  │   Module     │  │   Module     │  │   Module     │        │
│  └──────────────┘  └──────────────┘  └──────────────┘        │
│                                                                 │
│  ┌──────────────┐  ┌──────────────┐                           │
│  │    Cart      │  │   Shipping   │                           │
│  │   Module     │  │   Module     │                           │
│  └──────────────┘  └──────────────┘                           │
│                                                                 │
└────────────────────────────────────────────────────────────────┘
```

## KIẾN TRÚC CHI TIẾT CÁC MODULE

### 1. AUTH MODULE (Xác thực & Phân quyền)
```
┌─────────────────────────────────────────────────────────────────┐
│                         AUTH MODULE                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Controllers:                                                    │
│  ┌──────────────────┐  ┌──────────────────┐                    │
│  │ AuthController   │  │CustomerController│                    │
│  │ - login()        │  │ - register()     │                    │
│  │ - logout()       │  │ - getProfile()   │                    │
│  │ - refreshToken() │  │ - updateProfile()│                    │
│  └────────┬─────────┘  └────────┬─────────┘                    │
│           │                     │                               │
│           ↓                     ↓                               │
│  Services:                                                       │
│  ┌──────────────────┐  ┌──────────────────┐                    │
│  │ AuthService      │  │CustomerService   │                    │
│  │ - authenticate() │  │ - createCustomer()│                   │
│  │ - generateToken()│  │ - findByEmail()  │                    │
│  └────────┬─────────┘  └────────┬─────────┘                    │
│           │                     │                               │
│           ↓                     ↓                               │
│  Repositories:                                                   │
│  ┌──────────────────┐  ┌──────────────────┐                    │
│  │ UserRepository   │  │CustomerRepository│                    │
│  │ - findByEmail()  │  │ - findByUserId() │                    │
│  │ - save()         │  │ - save()         │                    │
│  └────────┬─────────┘  └────────┬─────────┘                    │
│           │                     │                               │
│           ↓                     ↓                               │
│  Entities:                                                       │
│  ┌──────────────────┐  ┌──────────────────┐                    │
│  │ User             │  │ Customer         │                    │
│  │ - id             │  │ - id             │                    │
│  │ - email          │  │ - fullName       │                    │
│  │ - password       │  │ - phone          │                    │
│  │ - role           │  │ - address        │                    │
│  └──────────────────┘  └──────────────────┘                    │
│                                                                  │
│  Security:                                                       │
│  ┌──────────────────┐  ┌──────────────────┐                    │
│  │ SecurityConfig   │  │ JwtTokenProvider │                    │
│  │ - filterChain()  │  │ - generateToken()│                    │
│  │ - passwordEnc()  │  │ - validateToken()│                    │
│  └──────────────────┘  └──────────────────┘                    │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### 2. PRODUCT MODULE (Quản lý sản phẩm)
```
┌─────────────────────────────────────────────────────────────────┐
│                       PRODUCT MODULE                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Controllers:                                                    │
│  ┌──────────────────┐  ┌──────────────────┐                    │
│  │ProductController │  │CategoryController│                    │
│  │ - getAll()       │  │ - getAll()       │                    │
│  │ - getById()      │  │ - create()       │                    │
│  │ - create()       │  │ - update()       │                    │
│  │ - update()       │  └──────────────────┘                    │
│  │ - delete()       │                                           │
│  │ - publish()      │  ┌──────────────────┐                    │
│  │ - addImage()     │  │ProductImageCtrl  │                    │
│  └────────┬─────────┘  │ - upload()       │                    │
│           │            │ - setPrimary()   │                    │
│           ↓            │ - delete()       │                    │
│  Services:             └──────────────────┘                    │
│  ┌──────────────────┐                                           │
│  │ProductService    │                                           │
│  │ - getAll()       │                                           │
│  │ - publishProduct()│                                          │
│  │ - addImage()     │                                           │
│  │ - syncStock()    │────────┐                                 │
│  └────────┬─────────┘        │                                 │
│           │                  │ Đồng bộ với Inventory            │
│           ↓                  ↓                                  │
│  Repositories:        ┌──────────────────┐                     │
│  ┌──────────────────┐ │InventoryStock    │                     │
│  │ProductRepository │ │Repository        │                     │
│  │ - findAll()      │ │ - findByProduct()│                     │
│  │ - findByIdLock() │ │ - save()         │                     │
│  │ - save()         │ └──────────────────┘                     │
│  └────────┬─────────┘                                           │
│           │                                                     │
│           ↓                                                     │
│  Entities:                                                       │
│  ┌──────────────────┐  ┌──────────────────┐                    │
│  │ Product          │  │ ProductImage     │                    │
│  │ - id             │  │ - id             │                    │
│  │ - name           │  │ - imageUrl       │                    │
│  │ - price          │  │ - isPrimary      │                    │
│  │ - stockQuantity  │  │ - displayOrder   │                    │
│  │ - reservedQty    │  └──────────────────┘                    │
│  │ - category       │                                           │
│  └──────────────────┘  ┌──────────────────┐                    │
│                        │ Category         │                    │
│                        │ - id             │                    │
│                        │ - name           │                    │
│                        └──────────────────┘                    │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### 3. ORDER MODULE (Quản lý đơn hàng)
```
┌─────────────────────────────────────────────────────────────────┐
│                        ORDER MODULE                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Controllers:                                                    │
│  ┌──────────────────┐  ┌──────────────────┐                    │
│  │ OrderController  │  │ShipperAssignment │                    │
│  │ - create()       │  │Controller        │                    │
│  │ - getById()      │  │ - claimOrder()   │                    │
│  │ - getMyOrders()  │  │ - confirmDeliv() │                    │
│  │ - cancel()       │  │ - reportFail()   │                    │
│  │ - confirmRecv()  │  └────────┬─────────┘                    │
│  └────────┬─────────┘           │                              │
│           │                     │                              │
│           ↓                     ↓                              │
│  Services:                                                       │
│  ┌──────────────────┐  ┌──────────────────┐                    │
│  │ OrderService     │  │ShipperAssignment │                    │
│  │ - createOrder()  │  │Service           │                    │
│  │ - cancelOrder()  │  │ - claimOrder()   │                    │
│  │ - updateStatus() │  │ - isHanoiInner() │                    │
│  └────────┬─────────┘  └────────┬─────────┘                    │
│           │                     │                              │
│           ↓                     ↓                              │
│  Repositories:                                                   │
│  ┌──────────────────┐  ┌──────────────────┐                    │
│  │ OrderRepository  │  │ShipperAssignment │                    │
│  │ - findById()     │  │Repository        │                    │
│  │ - findByCustomer()│ │ - existsByOrder()│                    │
│  │ - save()         │  │ - save()         │                    │
│  └────────┬─────────┘  └────────┬─────────┘                    │
│           │                     │                              │
│           ↓                     ↓                              │
│  Entities:                                                       │
│  ┌──────────────────┐  ┌──────────────────┐                    │
│  │ Order            │  │ OrderItem        │                    │
│  │ - id             │  │ - id             │                    │
│  │ - orderCode      │  │ - product        │                    │
│  │ - customer       │  │ - quantity       │                    │
│  │ - status         │  │ - price          │                    │
│  │ - total          │  │ - serialNumber   │                    │
│  │ - paymentMethod  │  │ - reserved       │                    │
│  │ - ghnOrderCode   │  │ - exported       │                    │
│  └──────────────────┘  └──────────────────┘                    │
│                                                                  │
│  ┌──────────────────┐                                           │
│  │ShipperAssignment │                                           │
│  │ - id             │                                           │
│  │ - order          │                                           │
│  │ - shipper        │                                           │
│  │ - status         │                                           │
│  │ - claimedAt      │                                           │
│  │ - deliveredAt    │                                           │
│  └──────────────────┘                                           │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

## CÔNG NGHỆ SỬ DỤNG

### Frontend
- **Framework**: Next.js 14 (React 18)
- **Language**: TypeScript
- **Styling**: TailwindCSS
- **State**: Zustand
- **HTTP**: Axios

### Backend
- **Framework**: Spring Boot 3.x
- **Language**: Java 17
- **Security**: Spring Security + JWT
- **Database**: MySQL 8.0
- **ORM**: Spring Data JPA

### External Services
- **Payment**: SePay API
- **Shipping**: GHN API
- **Email**: SMTP

## LUỒNG HOẠT ĐỘNG

### 1. Đặt Hàng
```
Customer → Chọn sản phẩm → Thêm giỏ hàng → Thanh toán
    ↓
Tạo đơn hàng → Xử lý thanh toán → Xuất kho → Giao hàng
```

### 2. Quản Lý Kho
```
Nhập hàng → Cập nhật tồn kho → Tạo serial → Sẵn sàng bán
    ↓
Đơn hàng → Xuất kho → Giảm tồn kho → Tạo phiếu bảo hành
```

### 3. Giao Hàng
```
Đơn hàng mới → [Nội bộ] → Shipper nhận → Giao hàng
               [GHN] → Tạo đơn GHN → Theo dõi → Giao hàng
```


### 4. PAYMENT MODULE (Thanh toán)
```
┌─────────────────────────────────────────────────────────────────┐
│                       PAYMENT MODULE                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Controllers:                                                    │
│  ┌──────────────────┐                                           │
│  │PaymentController │                                           │
│  │ - createPayment()│                                           │
│  │ - getByCode()    │                                           │
│  │ - checkStatus()  │                                           │
│  │ - sepayWebhook() │◄────── SePay Server                      │
│  └────────┬─────────┘                                           │
│           │                                                     │
│           ↓                                                     │
│  Services:                                                       │
│  ┌──────────────────┐                                           │
│  │ PaymentService   │                                           │
│  │ - createPayment()│                                           │
│  │ - handleWebhook()│                                           │
│  │ - generateQR()   │                                           │
│  │ - verifySign()   │                                           │
│  │ - extractCode()  │                                           │
│  └────────┬─────────┘                                           │
│           │                                                     │
│           ↓                                                     │
│  Repositories:                                                   │
│  ┌──────────────────┐  ┌──────────────────┐                    │
│  │PaymentRepository │  │BankAccountRepo   │                    │
│  │ - findByCode()   │  │ - findDefault()  │                    │
│  │ - findByOrder()  │  │ - save()         │                    │
│  │ - findExpired()  │  └──────────────────┘                    │
│  └────────┬─────────┘                                           │
│           │                                                     │
│           ↓                                                     │
│  Entities:                                                       │
│  ┌──────────────────┐  ┌──────────────────┐                    │
│  │ Payment          │  │ BankAccount      │                    │
│  │ - id             │  │ - id             │                    │
│  │ - paymentCode    │  │ - bankCode       │                    │
│  │ - order          │  │ - accountNumber  │                    │
│  │ - amount         │  │ - accountName    │                    │
│  │ - status         │  │ - sepayApiToken  │                    │
│  │ - method         │  │ - isDefault      │                    │
│  │ - sepayQrCode    │  └──────────────────┘                    │
│  │ - expiredAt      │                                           │
│  │ - paidAt         │                                           │
│  └──────────────────┘                                           │
│                                                                  │
│  Scheduler:                                                      │
│  ┌──────────────────┐                                           │
│  │PaymentScheduler  │                                           │
│  │ @Scheduled       │                                           │
│  │ - expireOld()    │ ◄─── Chạy mỗi 5 phút                     │
│  └──────────────────┘                                           │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### 5. INVENTORY MODULE (Quản lý kho)
```
┌─────────────────────────────────────────────────────────────────┐
│                      INVENTORY MODULE                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Controllers:                                                    │
│  ┌──────────────────┐  ┌──────────────────┐                    │
│  │InventoryCtrl     │  │SerialNumberCtrl  │                    │
│  │ - createPurchase()│ │ - generate()     │                    │
│  │ - createSaleExp()│  │ - getByProduct() │                    │
│  │ - getStock()     │  │ - updateStatus() │                    │
│  └────────┬─────────┘  └────────┬─────────┘                    │
│           │                     │                              │
│           ↓                     ↓                              │
│  Services:                                                       │
│  ┌──────────────────┐  ┌──────────────────┐                    │
│  │ InventoryService │  │SerialNumberSvc   │                    │
│  │ - createPurchase()│ │ - generateSerial()│                   │
│  │ - createSaleExp()│  │ - assignToOrder()│                    │
│  │ - syncReserved() │  │ - markAsSold()   │                    │
│  │ - updateStock()  │  └──────────────────┘                    │
│  └────────┬─────────┘                                           │
│           │                                                     │
│           ↓                                                     │
│  Repositories:                                                   │
│  ┌──────────────────┐  ┌──────────────────┐                    │
│  │InventoryStockRepo│  │SerialNumberRepo  │                    │
│  │ - findByProduct()│  │ - findBySerial() │                    │
│  │ - save()         │  │ - findAvailable()│                    │
│  └────────┬─────────┘  └────────┬─────────┘                    │
│           │                     │                              │
│           ↓                     ↓                              │
│  Entities:                                                       │
│  ┌──────────────────┐  ┌──────────────────┐                    │
│  │ InventoryStock   │  │ SerialNumber     │                    │
│  │ - id             │  │ - id             │                    │
│  │ - warehouseProduct│ │ - serialNumber   │                    │
│  │ - onHand         │  │ - product        │                    │
│  │ - reserved       │  │ - status         │                    │
│  │ - damaged        │  │ - createdAt      │                    │
│  │ - sellable       │  │ - soldAt         │                    │
│  └──────────────────┘  └──────────────────┘                    │
│                                                                  │
│  ┌──────────────────┐  ┌──────────────────┐                    │
│  │PurchaseOrder     │  │SaleExportOrder   │                    │
│  │ - id             │  │ - id             │                    │
│  │ - supplier       │  │ - order          │                    │
│  │ - items          │  │ - items          │                    │
│  │ - totalAmount    │  │ - exportDate     │                    │
│  │ - orderDate      │  │ - exportedBy     │                    │
│  └──────────────────┘  └──────────────────┘                    │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### 6. SHIPPING MODULE (Vận chuyển)
```
┌─────────────────────────────────────────────────────────────────┐
│                      SHIPPING MODULE                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Controllers:                                                    │
│  ┌──────────────────┐                                           │
│  │ShippingController│                                           │
│  │ - calculateFee() │                                           │
│  │ - createGHNOrder()│                                          │
│  │ - getGHNStatus() │                                           │
│  │ - cancelGHN()    │                                           │
│  └────────┬─────────┘                                           │
│           │                                                     │
│           ↓                                                     │
│  Services:                                                       │
│  ┌──────────────────┐                                           │
│  │ ShippingService  │                                           │
│  │ - calcShipFee()  │────────┐                                 │
│  │ - createGHNOrder()│        │                                 │
│  │ - getGHNDetail() │        │ HTTP Call                        │
│  │ - buildGHNReq()  │        ↓                                 │
│  │ - parseGHNResp() │  ┌──────────────────┐                    │
│  └──────────────────┘  │  GHN API         │                    │
│                        │  RestTemplate    │                    │
│  DTOs:                 │  - POST /create  │                    │
│  ┌──────────────────┐  │  - GET /detail   │                    │
│  │CreateGHNOrderReq │  │  - POST /cancel  │                    │
│  │ - orderId        │  └──────────────────┘                    │
│  │ - toName         │                                           │
│  │ - toPhone        │                                           │
│  │ - toAddress      │                                           │
│  │ - items          │                                           │
│  │ - serviceType    │                                           │
│  └──────────────────┘                                           │
│                                                                  │
│  ┌──────────────────┐                                           │
│  │GHNOrderResponse  │                                           │
│  │ - orderCode      │                                           │
│  │ - expectedTime   │                                           │
│  │ - totalFee       │                                           │
│  │ - status         │                                           │
│  └──────────────────┘                                           │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### 7. CART & REVIEW MODULE
```
┌─────────────────────────────────────────────────────────────────┐
│                    CART & REVIEW MODULE                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  CART:                                                           │
│  ┌──────────────────┐                                           │
│  │ CartController   │                                           │
│  │ - getCart()      │                                           │
│  │ - addItem()      │                                           │
│  │ - updateQty()    │                                           │
│  │ - removeItem()   │                                           │
│  └────────┬─────────┘                                           │
│           ↓                                                     │
│  ┌──────────────────┐                                           │
│  │ CartService      │                                           │
│  │ - getOrCreate()  │                                           │
│  │ - addItem()      │                                           │
│  │ - validateStock()│                                           │
│  └────────┬─────────┘                                           │
│           ↓                                                     │
│  ┌──────────────────┐  ┌──────────────────┐                    │
│  │ Cart             │  │ CartItem         │                    │
│  │ - id             │  │ - id             │                    │
│  │ - customer       │  │ - cart           │                    │
│  │ - items          │  │ - product        │                    │
│  │ - createdAt      │  │ - quantity       │                    │
│  └──────────────────┘  │ - price          │                    │
│                        └──────────────────┘                    │
│                                                                  │
│  REVIEW:                                                         │
│  ┌──────────────────┐                                           │
│  │ ReviewController │                                           │
│  │ - create()       │                                           │
│  │ - getByProduct() │                                           │
│  │ - update()       │                                           │
│  │ - delete()       │                                           │
│  └────────┬─────────┘                                           │
│           ↓                                                     │
│  ┌──────────────────┐                                           │
│  │ ReviewService    │                                           │
│  │ - createReview() │                                           │
│  │ - validateOrder()│                                           │
│  │ - calcAvgRating()│                                           │
│  └────────┬─────────┘                                           │
│           ↓                                                     │
│  ┌──────────────────┐                                           │
│  │ ProductReview    │                                           │
│  │ - id             │                                           │
│  │ - product        │                                           │
│  │ - customer       │                                           │
│  │ - order          │                                           │
│  │ - rating         │                                           │
│  │ - comment        │                                           │
│  │ - createdAt      │                                           │
│  └──────────────────┘                                           │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

## LUỒNG DỮ LIỆU CHI TIẾT

### Luồng Đặt Hàng (với các component)
```
┌──────────────┐
│   Frontend   │
│  (Next.js)   │
└──────┬───────┘
       │ POST /api/orders/create
       ↓
┌──────────────────────────────────────────────────────────────┐
│                    Backend (Spring Boot)                      │
│                                                               │
│  ┌────────────────┐                                          │
│  │OrderController │                                          │
│  │ @PostMapping   │                                          │
│  └───────┬────────┘                                          │
│          │                                                   │
│          ↓                                                   │
│  ┌────────────────┐                                          │
│  │ OrderService   │                                          │
│  │ @Transactional │                                          │
│  └───────┬────────┘                                          │
│          │                                                   │
│          ├──────► ProductRepository.findByIdWithLock()       │
│          │        (Pessimistic Lock)                         │
│          │                                                   │
│          ├──────► Check stock & reserve                      │
│          │                                                   │
│          ├──────► OrderRepository.save()                     │
│          │                                                   │
│          ├──────► InventoryService.syncReserved()            │
│          │                                                   │
│          └──────► CartRepository.delete()                    │
│                                                               │
└───────────────────────────────┬───────────────────────────────┘
                                │
                                ↓
                        ┌───────────────┐
                        │    MySQL      │
                        │   Database    │
                        └───────────────┘
```

### Luồng Webhook SePay (với các component)
```
┌──────────────┐
│ SePay Server │
└──────┬───────┘
       │ POST /api/payments/sepay/webhook
       ↓
┌──────────────────────────────────────────────────────────────┐
│                    Backend (Spring Boot)                      │
│                                                               │
│  ┌────────────────┐                                          │
│  │PaymentController│                                         │
│  │ (No Auth!)     │                                          │
│  └───────┬────────┘                                          │
│          │                                                   │
│          ↓                                                   │
│  ┌────────────────┐                                          │
│  │PaymentService  │                                          │
│  │ @Transactional │                                          │
│  └───────┬────────┘                                          │
│          │                                                   │
│          ├──────► extractPaymentCode()                       │
│          │                                                   │
│          ├──────► PaymentRepository.findByCode()             │
│          │                                                   │
│          ├──────► verifySignature()                          │
│          │                                                   │
│          ├──────► Update Payment → SUCCESS                   │
│          │                                                   │
│          ├──────► Update Order → CONFIRMED                   │
│          │                                                   │
│          └──────► ApplicationEventPublisher.publish()        │
│                   (OrderStatusChangedEvent)                  │
│                                                               │
└───────────────────────────────┬───────────────────────────────┘
                                │
                                ↓
                        ┌───────────────┐
                        │ Event Listener│
                        │  (Accounting) │
                        └───────────────┘
```

