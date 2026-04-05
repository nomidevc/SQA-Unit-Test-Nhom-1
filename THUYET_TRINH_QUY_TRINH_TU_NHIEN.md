# NỘI DUNG THUYẾT TRÌNH - DẠNG ĐOẠN VĂN TỰ NHIÊN
## Quy trình từ Quản lý Sản phẩm đến Giao hàng (2-3 phút)

---

## 🎤 BẢN THUYẾT TRÌNH ĐẦY ĐỦ

Chúng em xin trình bày về quy trình hoàn chỉnh từ khi quản lý sản phẩm cho đến khi giao hàng đến tay khách hàng. Đây là một trong những phần quan trọng nhất của hệ thống, thể hiện khả năng tự động hóa và quản lý chặt chẽ của đồ án.

### **Phần 1: Quản lý kho và đăng bán sản phẩm**

Đầu tiên là khâu quản lý kho hàng. Khi có hàng mới từ nhà cung cấp, nhân viên kho sẽ tạo phiếu nhập hàng trên hệ thống. Lúc này, hệ thống tự động tạo mã SKU cho sản phẩm và cập nhật số lượng tồn kho vào cơ sở dữ liệu. Tất cả thông tin về nhà cung cấp, giá nhập, ngày nhập đều được ghi nhận đầy đủ.

Sau khi hàng đã có trong kho, Admin hoặc nhân viên bán hàng sẽ chọn những sản phẩm nào để đăng bán trên website. Họ sẽ thiết lập các thông tin như tên sản phẩm, giá bán, mô tả chi tiết, thông số kỹ thuật, và upload ảnh sản phẩm - tối đa 9 ảnh cho mỗi sản phẩm. Khi hoàn tất, sản phẩm sẽ xuất hiện trên trang web để khách hàng có thể xem và mua.

Điểm đặc biệt ở đây là hệ thống quản lý tồn kho rất thông minh. Chúng em theo dõi ba loại số lượng: On-hand là tổng số hàng đang có trong kho, Reserved là số lượng đã được giữ cho các đơn hàng đang chờ xử lý, và Sellable chính là số lượng thực sự có thể bán, được tính bằng On-hand trừ đi Reserved và số hàng bị hỏng. Nhờ vậy, hệ thống luôn biết chính xác có bao nhiêu sản phẩm có thể bán cho khách hàng.

### **Phần 2: Khách hàng đặt hàng và thanh toán**

Tiếp theo là quy trình khách hàng đặt hàng. Khách hàng vào website, duyệt sản phẩm, có thể lọc theo các thông số kỹ thuật như RAM, ổ cứng, màn hình, rồi thêm sản phẩm vào giỏ hàng. Một điểm hay là khách hàng có thể chọn một phần sản phẩm trong giỏ để thanh toán, không nhất thiết phải mua hết.

Khi tiến hành đặt hàng, khách hàng nhập địa chỉ giao hàng bao gồm tỉnh thành, quận huyện, phường xã. Hệ thống sẽ tự động tính phí vận chuyển dựa trên địa chỉ này. Đồng thời, hệ thống kiểm tra xem kho còn đủ hàng không, và nếu đủ thì sẽ tự động giữ hàng - tức là tăng số lượng Reserved lên - để đảm bảo không bán cho người khác.

Về thanh toán, chúng em hỗ trợ hai phương thức. Thứ nhất là COD - thanh toán tiền mặt khi nhận hàng. Với phương thức này, đơn hàng sẽ được tự động xác nhận ngay và chuyển sang trạng thái Confirmed, chờ nhân viên kho chuẩn bị hàng.

Thứ hai là thanh toán chuyển khoản qua SePay. Hệ thống sẽ tạo một mã QR code với nội dung chuyển khoản là mã đơn hàng. Khách hàng chỉ cần quét mã và chuyển khoản. Khi ngân hàng nhận được tiền, SePay sẽ gọi webhook về hệ thống của chúng em để tự động xác nhận thanh toán. Đơn hàng lúc này cũng chuyển sang trạng thái Confirmed. Nếu khách hàng không thanh toán trong vòng 15 phút, hệ thống sẽ tự động hủy đơn hàng và giải phóng số lượng hàng đã giữ để người khác có thể mua.

### **Phần 3: Xuất kho và chuẩn bị hàng**

Sau khi đơn hàng được xác nhận, nhân viên kho sẽ thấy đơn hàng này trong danh sách các đơn cần xử lý. Họ tạo phiếu xuất kho và bắt đầu chuẩn bị hàng. Đối với những sản phẩm như laptop hay điện thoại, nhân viên sẽ chọn serial number cụ thể cho từng sản phẩm trong đơn hàng. Hệ thống cũng tự động tạo và in phiếu bảo hành có mã QR code, khách hàng có thể quét mã này để xem thông tin bảo hành sau này.

Khi nhân viên xác nhận xuất kho, hệ thống sẽ tự động thực hiện nhiều thao tác: trừ số lượng tồn kho thực tế, trừ số lượng đã giữ, gán serial number cho từng sản phẩm trong đơn hàng, và chuyển đơn hàng sang trạng thái Ready to Ship - tức là sẵn sàng để giao hàng.

### **Phần 4: Giao hàng đến khách**

Cuối cùng là khâu giao hàng. Chúng em hỗ trợ hai hình thức giao hàng linh hoạt.

Hình thức thứ nhất là giao hàng bằng shipper nội bộ, áp dụng cho các đơn hàng trong nội thành Hà Nội. Shipper sẽ vào app, xem danh sách các đơn hàng sẵn sàng giao trong khu vực của mình, và nhận đơn. Ngay khi nhận đơn, hệ thống tự động chuyển trạng thái đơn hàng sang Shipping - đang giao hàng. Sau khi giao hàng thành công, shipper xác nhận trên app, đơn hàng chuyển sang Delivered - đã giao. Ưu điểm của hình thức này là giao hàng nhanh, có thể trong ngày, tiết kiệm chi phí, và chúng em kiểm soát được chất lượng dịch vụ.

Hình thức thứ hai là giao hàng qua Giao Hàng Nhanh, áp dụng cho các đơn hàng ở ngoại thành hoặc các tỉnh khác. Khi xuất kho, nhân viên sẽ tạo đơn GHN thông qua API. Hệ thống gọi API của GHN để tạo đơn vận chuyển, nhận về mã vận đơn, và tính phí vận chuyển chính xác. Khách hàng có thể dùng mã vận đơn này để tracking đơn hàng real-time, xem hàng đang ở đâu. Khi GHN giao hàng thành công, họ sẽ cập nhật trạng thái, và hệ thống của chúng em cũng tự động cập nhật theo. Ưu điểm là phủ sóng toàn quốc và có tracking chính xác.

### **Phần 5: Hoàn tất và đánh giá**

Sau khi nhận hàng, khách hàng có thể vào website xác nhận đã nhận hàng. Đơn hàng lúc này chuyển sang trạng thái Completed - hoàn thành, và hệ thống ghi nhận doanh thu. Khách hàng cũng có thể đánh giá sản phẩm từ 1 đến 5 sao, viết review để chia sẻ trải nghiệm, giúp những khách hàng khác tham khảo. Ngoài ra, khách hàng có thể quét mã QR trên phiếu bảo hành để xem thông tin bảo hành và liên hệ khi cần.

### **Kết luận**

Như vậy, quy trình của chúng em có ba điểm mạnh chính. Thứ nhất là tự động hóa cao - từ việc giữ hàng khi đặt, xác nhận thanh toán qua webhook, đến hủy đơn quá hạn, tất cả đều tự động. Thứ hai là xử lý đồng thời an toàn - chúng em sử dụng database lock để tránh tình trạng 100 người đặt cùng lúc vượt quá số lượng tồn kho. Thứ ba là minh bạch hoàn toàn - mỗi sản phẩm có serial number riêng, phiếu bảo hành có QR code, và khách hàng có thể tracking đơn hàng mọi lúc.

Kết quả là chúng em đạt được thời gian xử lý rất nhanh - từ khi đặt hàng đến xuất kho chỉ mất dưới 2 giờ, và giao hàng nội thành trong vòng 24 giờ. Độ chính xác quản lý tồn kho đạt 100%, không bao giờ bán quá hàng. Đây là một quy trình hoàn chỉnh, từ nhập kho đến giao hàng, đáp ứng được yêu cầu của một hệ thống thương mại điện tử chuyên nghiệp.

---

## 🎯 BẢN RÚT GỌN (2 PHÚT)

Nếu cần ngắn gọn hơn, đây là bản 2 phút:

Chúng em xin trình bày quy trình từ quản lý sản phẩm đến giao hàng. Quy trình bắt đầu từ khâu quản lý kho - nhân viên kho tạo phiếu nhập hàng, hệ thống tự động tạo mã SKU và cập nhật tồn kho. Admin sau đó chọn sản phẩm để đăng bán với đầy đủ thông tin và ảnh.

Hệ thống quản lý tồn kho rất thông minh với ba loại số lượng: On-hand là tổng số trong kho, Reserved là số đã giữ cho đơn hàng, và Sellable là số có thể bán. Khi khách hàng đặt hàng, hệ thống tự động giữ hàng và hỗ trợ hai phương thức thanh toán: COD tự động xác nhận đơn, còn SePay tạo mã QR và webhook tự động xác nhận khi nhận tiền. Nếu không thanh toán trong 15 phút, đơn sẽ tự động bị hủy.

Sau khi thanh toán, nhân viên kho tạo phiếu xuất, chọn serial number và in phiếu bảo hành QR. Khi xác nhận xuất kho, hệ thống tự động trừ tồn kho và chuyển đơn sang sẵn sàng giao.

Về giao hàng, chúng em hỗ trợ hai hình thức: Shipper nội bộ cho đơn trong nội thành Hà Nội, giao trong ngày; và Giao Hàng Nhanh cho đơn toàn quốc với tracking real-time qua API.

Quy trình có ba điểm mạnh: tự động hóa cao, xử lý đồng thời an toàn tránh bán quá hàng, và minh bạch với serial number và tracking real-time. Kết quả là thời gian xử lý nhanh, độ chính xác 100%, đáp ứng yêu cầu của một hệ thống thương mại điện tử chuyên nghiệp.

---

## 💡 TIPS KHI THUYẾT TRÌNH

### Giọng điệu & Tốc độ
- Nói với tốc độ vừa phải, khoảng 120-140 từ/phút
- Nhấn mạnh vào các con số: "15 phút", "100%", "24 giờ"
- Dừng ngắn sau mỗi phần để thính giả tiếp thu

### Ngôn ngữ cơ thể
- Dùng tay chỉ vào slide khi nói đến các bước
- Gật đầu nhẹ khi nói về ưu điểm
- Giữ ánh mắt với hội đồng

### Từ nối tự nhiên
- "Đầu tiên là...", "Tiếp theo...", "Cuối cùng..."
- "Điểm đặc biệt ở đây là...", "Điểm hay là..."
- "Như vậy...", "Kết quả là..."

### Nhấn mạnh điểm quan trọng
- "Đây là một trong những phần quan trọng nhất..."
- "Điểm mạnh chính là..."
- "Đặc biệt là..."

### Kết thúc mạnh mẽ
- "Kết quả là chúng em đạt được..."
- "Đây là một quy trình hoàn chỉnh..."
- "Đáp ứng được yêu cầu của..."

---

## 📝 CÂU HỎI DỰ KIẾN & TRẢ LỜI

**Q: Nếu 100 người đặt cùng lúc thì xử lý thế nào?**
A: Chúng em sử dụng database lock khi đặt hàng. Khi một người đang đặt, các request khác phải đợi. Hệ thống kiểm tra tồn kho và giữ hàng tuần tự, đảm bảo không bán quá số lượng.

**Q: Nếu khách không thanh toán thì sao?**
A: Hệ thống có scheduler chạy mỗi 5 phút để kiểm tra các đơn hàng quá hạn 15 phút chưa thanh toán. Những đơn này sẽ tự động bị hủy và giải phóng số lượng hàng đã giữ.

**Q: Làm sao đảm bảo serial number không bị trùng?**
A: Mỗi serial number là duy nhất trong database với unique constraint. Khi nhập kho, nhân viên tạo serial theo format chuẩn, và hệ thống kiểm tra không cho phép trùng lặp.

**Q: Nếu shipper giao hàng thất bại thì xử lý ra sao?**
A: Shipper có thể báo giao hàng thất bại kèm lý do trên app. Nhân viên sẽ liên hệ khách hàng để sắp xếp giao lại hoặc xử lý hoàn hàng.

**Q: Tại sao không dùng GHN cho tất cả đơn hàng?**
A: Với đơn nội thành Hà Nội, shipper nội bộ giúp tiết kiệm chi phí, giao hàng nhanh hơn trong ngày, và chúng em kiểm soát được chất lượng dịch vụ tốt hơn.

---

**Thời gian**: 2-3 phút (bản đầy đủ) hoặc 2 phút (bản rút gọn)
**Phong cách**: Tự nhiên, dễ hiểu, chuyên nghiệp
**Phù hợp**: Thuyết trình trước hội đồng, demo cho khách hàng
