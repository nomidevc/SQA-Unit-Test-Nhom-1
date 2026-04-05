# NỘI DUNG THUYẾT TRÌNH: QUY TRÌNH TỪ QUẢN LÝ SẢN PHẨM ĐẾN GIAO HÀNG
## Thời lượng: 2-3 phút

---

## 🎯 TỔNG QUAN QUY TRÌNH

Hệ thống của chúng em xây dựng một quy trình hoàn chỉnh từ khi sản phẩm nhập kho cho đến khi giao hàng thành công đến tay khách hàng. Quy trình này được tự động hóa cao, đảm bảo tính chính xác và minh bạch.

---

## 📦 BƯỚC 1: QUẢN LÝ SẢN PHẨM & KHO HÀNG

### 1.1. Nhập hàng vào kho
- **Nhân viên kho** tạo phiếu nhập hàng từ nhà cung cấp
- Hệ thống tự động:
  - Tạo mã SKU cho sản phẩm
  - Cập nhật số lượng tồn kho (InventoryStock)
  - Ghi nhận thông tin nhà cung cấp, giá nhập

### 1.2. Đăng bán sản phẩm
- **Admin/Nhân viên bán hàng** chọn sản phẩm từ kho để đăng bán
- Thiết lập thông tin:
  - Tên sản phẩm, giá bán
  - Danh mục (Laptop, Điện thoại, Phụ kiện)
  - Mô tả, thông số kỹ thuật
  - Upload ảnh sản phẩm (tối đa 9 ảnh)
- Sản phẩm xuất hiện trên website cho khách hàng

### 1.3. Quản lý tồn kho thông minh
- Hệ thống theo dõi 3 loại số lượng:
  - **On-hand**: Tổng số hàng trong kho
  - **Reserved**: Số lượng đã được giữ cho đơn hàng
  - **Sellable**: Số lượng có thể bán = On-hand - Reserved - Damaged
- Tự động cảnh báo khi hàng sắp hết

---

## 🛒 BƯỚC 2: KHÁCH HÀNG ĐẶT HÀNG

### 2.1. Chọn sản phẩm & Giỏ hàng
- Khách hàng duyệt sản phẩm, lọc theo thông số kỹ thuật
- Thêm vào giỏ hàng, chọn số lượng
- Có thể chọn một phần sản phẩm trong giỏ để thanh toán

### 2.2. Đặt hàng & Tính phí vận chuyển
- Nhập địa chỉ giao hàng (Tỉnh/Thành, Quận/Huyện, Phường/Xã)
- Hệ thống tự động:
  - Tính phí vận chuyển dựa trên địa chỉ
  - Kiểm tra tồn kho có đủ không
  - **Giữ hàng** (Reserved) để không bán cho người khác

### 2.3. Thanh toán
Hỗ trợ 2 phương thức:

**A. COD (Tiền mặt khi nhận hàng)**
- Đơn hàng tự động chuyển sang trạng thái **CONFIRMED** (Đã xác nhận)
- Chờ nhân viên kho chuẩn bị hàng

**B. Chuyển khoản (SePay)**
- Hệ thống tạo mã QR thanh toán
- Khách quét mã và chuyển khoản
- Webhook tự động xác nhận khi nhận được tiền
- Đơn hàng chuyển sang **CONFIRMED**
- Nếu không thanh toán trong 15 phút → Tự động hủy đơn, giải phóng hàng

---

## 📤 BƯỚC 3: CHUẨN BỊ & XUẤT KHO

### 3.1. Nhân viên kho xử lý đơn hàng
- Xem danh sách đơn hàng **CONFIRMED** (Đã xác nhận)
- Tạo phiếu xuất kho cho đơn hàng
- Chọn serial number cho từng sản phẩm (Laptop, Điện thoại)
- In phiếu bảo hành có mã QR

### 3.2. Xác nhận xuất kho
- Khi xác nhận xuất kho, hệ thống tự động:
  - Trừ số lượng tồn kho thực tế (On-hand)
  - Trừ số lượng đã giữ (Reserved)
  - Gán serial number cho từng sản phẩm trong đơn
  - Chuyển đơn hàng sang **READY_TO_SHIP** (Sẵn sàng giao)

---

## 🚚 BƯỚC 4: GIAO HÀNG

Hệ thống hỗ trợ 2 hình thức giao hàng:

### 4.1. Giao hàng nội bộ (Shipper nội bộ)
**Áp dụng cho**: Đơn hàng trong nội thành Hà Nội

**Quy trình**:
1. Shipper xem danh sách đơn hàng **READY_TO_SHIP** trong nội thành
2. Nhận đơn hàng (Claim)
3. Hệ thống tự động chuyển sang **SHIPPING** (Đang giao)
4. Shipper giao hàng và xác nhận trên app
5. Đơn hàng chuyển sang **DELIVERED** (Đã giao)

**Ưu điểm**:
- Giao hàng nhanh trong ngày
- Tiết kiệm chi phí
- Kiểm soát chất lượng dịch vụ

### 4.2. Giao hàng qua GHN (Giao Hàng Nhanh)
**Áp dụng cho**: Đơn hàng ngoại thành, tỉnh khác

**Quy trình**:
1. Nhân viên kho tạo đơn GHN khi xuất kho
2. Hệ thống gọi API GHN:
   - Tạo đơn vận chuyển
   - Nhận mã vận đơn (GHN Order Code)
   - Tính phí vận chuyển chính xác
3. Khách hàng theo dõi đơn hàng real-time qua mã vận đơn
4. GHN giao hàng và cập nhật trạng thái
5. Đơn hàng chuyển sang **DELIVERED**

**Ưu điểm**:
- Phủ sóng toàn quốc
- Tracking chính xác
- Tích hợp API tự động

---

## ✅ BƯỚC 5: HOÀN TẤT & ĐÁNH GIÁ

### 5.1. Khách hàng nhận hàng
- Khách hàng xác nhận đã nhận hàng trên website
- Đơn hàng chuyển sang **COMPLETED** (Hoàn thành)
- Hệ thống ghi nhận doanh thu

### 5.2. Đánh giá sản phẩm
- Khách hàng có thể đánh giá sản phẩm (1-5 sao)
- Viết review, chia sẻ trải nghiệm
- Giúp khách hàng khác tham khảo

### 5.3. Bảo hành
- Khách hàng quét mã QR trên phiếu bảo hành
- Xem thông tin bảo hành, serial number
- Liên hệ bảo hành khi cần

---

## 🔄 XỬ LÝ HỦY ĐỠN & HOÀN HÀNG

### Khách hàng hủy đơn
- **Trước khi thanh toán**: Xóa đơn, giải phóng hàng ngay lập tức
- **Sau khi thanh toán, chưa xuất kho**: Giải phóng hàng đã giữ (Reserved)
- **Sau khi xuất kho**: Hàng cần nhập lại kho thủ công

### Giao hàng thất bại
- Shipper báo thất bại + lý do
- Nhân viên liên hệ khách hàng
- Sắp xếp giao lại hoặc hoàn hàng

---

## 📊 TÍNH NĂNG NỔI BẬT CỦA QUY TRÌNH

### 1. Tự động hóa cao
- Tự động giữ hàng khi đặt
- Tự động xác nhận thanh toán (Webhook)
- Tự động hủy đơn quá hạn
- Tự động cập nhật tồn kho

### 2. Đồng bộ dữ liệu
- Tồn kho đồng bộ giữa Product và InventoryStock
- Reserved quantity cập nhật real-time
- Tránh bán quá số lượng tồn kho

### 3. Xử lý đồng thời (Concurrency)
- Sử dụng database lock khi đặt hàng
- Tránh 100 người đặt cùng lúc vượt quá tồn kho
- Unique constraint cho shipper claim order

### 4. Minh bạch & Truy vết
- Mỗi sản phẩm có serial number riêng
- Phiếu bảo hành có mã QR
- Lịch sử nhập/xuất kho đầy đủ
- Tracking đơn hàng real-time

### 5. Linh hoạt
- Hỗ trợ nhiều phương thức thanh toán
- Hỗ trợ nhiều hình thức giao hàng
- Khách hàng chọn sản phẩm trong giỏ để thanh toán

---

## 🎯 KẾT QUẢ ĐẠT ĐƯỢC

✅ **Quy trình hoàn chỉnh**: Từ nhập kho → Đăng bán → Đặt hàng → Thanh toán → Xuất kho → Giao hàng → Hoàn thành

✅ **Tự động hóa**: Giảm thiểu thao tác thủ công, tăng hiệu quả

✅ **Chính xác**: Quản lý tồn kho chặt chẽ, tránh bán quá hàng

✅ **Minh bạch**: Khách hàng theo dõi đơn hàng mọi lúc

✅ **Linh hoạt**: Hỗ trợ nhiều phương thức thanh toán & giao hàng

---

## 💡 DEMO FLOW (Nếu có thời gian)

**Tình huống**: Khách hàng mua 1 Laptop

1. **[Website]** Khách chọn Laptop, thêm vào giỏ
2. **[Checkout]** Nhập địa chỉ, chọn thanh toán SePay
3. **[Payment]** Quét QR, chuyển khoản → Webhook xác nhận
4. **[Warehouse]** Nhân viên kho tạo phiếu xuất, chọn serial, in bảo hành
5. **[Shipping]** Shipper nhận đơn, giao hàng
6. **[Complete]** Khách nhận hàng, xác nhận, đánh giá 5 sao

**Thời gian**: Từ đặt hàng đến nhận hàng < 24h (nội thành HN)

---

## 📌 KẾT LUẬN

Quy trình quản lý từ sản phẩm đến giao hàng của hệ thống được thiết kế:
- **Hoàn chỉnh**: Bao phủ toàn bộ vòng đời đơn hàng
- **Tự động**: Giảm thiểu can thiệp thủ công
- **Chính xác**: Quản lý tồn kho chặt chẽ
- **Linh hoạt**: Hỗ trợ nhiều phương thức
- **Minh bạch**: Khách hàng luôn nắm rõ trạng thái

Đây là một trong những điểm mạnh của đồ án, thể hiện khả năng phân tích nghiệp vụ và thiết kế hệ thống hoàn chỉnh.

---

**Thời gian trình bày**: 2-3 phút
**Số slide đề xuất**: 3-4 slides
**Có thể kết hợp**: Demo video hoặc live demo
