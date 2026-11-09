# Đặt điểm trên bản đồ robot PUDU — làm đúng ngay từ đầu

Robot chỉ đi tới được những chỗ **đã đặt tên sẵn trên bản đồ**. App gọi lệnh dẫn đường
bằng đúng cái tên đó. Sai tên là robot đứng im và báo *"Trên bản đồ chưa có điểm Quay 3"*.

Dành cho **BellaBot Pro** chạy app hành chính công (`com.pudutech.business.hcc`).
Máy OrionStar Nova làm khác — xem `11-app-nova/tra-cuu-tthc-mong-duong/huong-dan-dat-ten-diem-ban-do.md`.

---

## 1. Cần đặt những điểm nào

| Tên phải đặt | Là chỗ nào | Bắt buộc? |
|---|---|---|
| `Quay 1` | Phòng Văn hoá – Xã hội | ✔ |
| `Quay 2` | Kinh tế, Hạ tầng & Đô thị | ✔ |
| `Quay 3` | Văn phòng HĐND & UBND | ✔ |
| `Quay 4` | Hướng dẫn dịch vụ công trực tuyến | ✔ |
| `Quay 5` | Hướng dẫn dịch vụ công trực tuyến | ✔ |
| `Quay 6` | Thu phí, lệ phí & trả kết quả | ✔ |
| `Quay 7` | Hướng dẫn dịch vụ công trực tuyến | ✔ |
| `Le tan` | Giữa sảnh chờ — chỗ robot đứng gọi số thứ tự | nên có |
| `Tram sac` | Trạm sạc | nên có |

**Điểm đứng ở quầy** đặt cách mép bàn khoảng **80 cm**, quay mặt robot về phía người dân
đang đứng chờ, **không** quay vào bàn cán bộ. Robot rộng 55 cm — chừa đủ chỗ cho người
đứng cạnh nó mà không chắn lối đi.

---

## 2. Quy tắc đặt tên

**Ba điều bắt buộc:**

1. **Không dấu tiếng Việt.** Viết `Quay 1`, **không** viết `Quầy 1`.
2. **Không thêm gì phía sau.** `Quay 1` chứ không phải `Quay 1 - Van hoa Xa hoi`.
3. **Số phải đúng 1…7**, đúng thứ tự quầy ngoài thực địa.

**Hai điều bản PUDU đã nới, khác bản Nova** — app tự chuẩn hoá trước khi so
(`RobotHelper.chuanHoa`):

- **Hoa thường không quan trọng.** `Quay 1` · `quay 1` · `QUAY 1` đều nhận.
- **Khoảng trắng thừa không quan trọng.** `Quay  1` hay ` Quay 1 ` đều nhận.

> Vì sao vẫn phải bỏ dấu: app so tên bằng chuỗi thô. `Quầy 1` và `Quay 1` là hai chuỗi
> khác nhau, không có cách nào tự đoán được.

Muốn dùng tên khác hẳn (ví dụ `Ban 1`) thì được, nhưng phải sửa app cho khớp: mở
`app/src/main/java/vn/roboworld/hcc/Cau.kt`, hàm `danDuongToiQuay`, đổi chuỗi
`"Quay $soQuay"` rồi build lại APK.

---

## 3. Bản đồ nào

Điểm phải nằm trên **đúng bản đồ robot đang dùng** — không phải bản đồ nào cũng được.
Bản đồ dựng để thử ở văn phòng Roboworld tên là `vanphong`; bản đồ hiện trường là
`hcc-MongDuong-QN`.

⚠ Trên máy tên bản đồ hiện là `vanphong`, nhưng khi đọc bằng SDK nó ra `0#0#vanphong`.
**Tiền tố `0#0#` là của hệ thống** — tên ĐIỂM thì không có tiền tố đó. Đừng nhầm hai thứ.

Màn **Kiểm tra bản đồ** (mục 5) hiện luôn tên bản đồ đang dùng, nên không phải đoán.

---

## 4. Đường bấm trên máy

### Vào Cài đặt bản đồ

Từ trong app hành chính công: **bấm giữ nút ☰ ở góc trên bên trái** khoảng 1,2 giây →
chọn **"Cài đặt robot"**. (Bấm một cái không ra gì — cố ý, để người dân không mở nhầm.)

Rồi: **Cài đặt → Cài đặt bản đồ**

Màn này có hai phần:

| Mục | Làm gì |
|---|---|
| **Chỉnh sửa bản đồ** | Thêm / sửa / xoá điểm — **hỏi mật khẩu** |
| **Chọn bản đồ** | Danh sách bản đồ, bấm *Mở rộng* để xem hình xem trước |

> ⚠ **"Chỉnh sửa bản đồ" đòi mật khẩu.** Máy hiện hộp *"Vui lòng nhập mật khẩu"*.
> Đây là mật khẩu kỹ thuật của PUDU, không phải mật khẩu Wi-Fi hay tài khoản PUDU Cloud.
> **Chưa có thì hỏi hãng / hỏi người đã bàn giao máy trước khi ra hiện trường** —
> ra tới nơi mới phát hiện là mất công đi lại.

### Trình tự làm

1. **Quét bản đồ** (chỉ làm một lần cho mỗi mặt bằng). Đẩy robot đi hết khu vực phục vụ
   theo đúng lối người dân đi. Quét lúc **vắng người** — người đứng lố nhố lúc quét sẽ
   thành vật cản vĩnh viễn in vào bản đồ.
2. **Chọn bản đồ vừa quét** ở mục *Chọn bản đồ*.
3. **Định vị**: đưa robot về đúng điểm dừng quen thuộc rồi bấm **Định vị lại**.
   Chưa định vị xong thì không đặt điểm được.
4. **Thêm từng điểm**: đẩy robot tới đúng chỗ, chỉnh hướng mặt robot, rồi lưu điểm và
   gõ tên theo bảng ở mục 1.
5. Làm đủ 7 quầy, rồi `Le tan`, rồi `Tram sac`.

---

## 5. Kiểm lại — đừng tin vào mắt

Sau khi đặt xong, **kiểm bằng máy** chứ đừng nhìn danh sách rồi tin là đúng. Lỗi hay gặp
nhất là thừa một dấu cách hoặc lỡ gõ dấu tiếng Việt — nhìn bằng mắt không thấy.

### Cách 1 — màn tự kiểm ngay trên robot (nhanh nhất, không cần máy tính)

Trong app: **bấm giữ nút ☰ góc trên trái** → **"Kiểm tra bản đồ"**.

Robot trả về ngay tại chỗ:

- tên bản đồ đang dùng
- đã định vị xong chưa
- **✔ / ✘ cho từng quầy `Quay 1`…`Quay 7`**, cộng `Le tan` và `Tram sac`
- toàn bộ danh sách điểm robot **thực sự** đọc được
- một dòng kết luận: còn thiếu điểm nào, hoặc đủ rồi thì mời chạy thử

Dấu ✔ ở đây dò bằng **đúng phép so tên** mà lệnh dẫn đường dùng, nên nó là lời hứa
thật: quầy nào ✔ thì bấm nút dẫn đường chắc chắn tìm thấy điểm.

Nút **CHI TIẾT** đổ đủ mọi trường của từng điểm — dùng khi tên nhìn thì đúng mà máy
vẫn báo thiếu.

### Cách 2 — đọc log

```powershell
$adb = "D:\RBW_Claude\tools\pudu\platform-tools-pudu\adb.exe"
& $adb logcat -c
# rồi bấm nút dẫn đường tới một quầy trên màn hình robot
& $adb logcat -d -s HCCRobot
```

Đặt sai tên thì log ghi rõ tên robot **thật sự** đang có:

```
W/HCCRobot: Không thấy điểm Quay 3. Bản đồ 0#0#hcc-MongDuong-QN đang có: Quay 1, Quay 2, Quầy 3, Le tan
```

→ Nhìn phát ra ngay: điểm thứ ba lỡ gõ dấu.

---

## 5b. ⚠ Tên điểm nằm ở trường `id`, KHÔNG phải `name`

Đo trên máy thật 18/08/2026, bản đồ `vanphong`, 7 điểm đặt bằng tay trên robot:

```
name='' id='Quay 1' key='Quay 1' group='' mode=Table
name='' id='Quay 2' key='Quay 2' group='' mode=Table
...
```

`PdDestination.getName()` trả về **chuỗi rỗng cho mọi điểm**. Cái tên cán bộ gõ vào
nằm ở `id` (và `key`).

App đã xử lý: `RobotHelper.tenCua()` lấy giá trị khác rỗng đầu tiên theo thứ tự
`name → id → key`. **Đừng sửa hàm đó về đọc mỗi `name`** — sửa là hỏng cả dẫn đường
lẫn màn tự kiểm, mà lỗi hiện ra dưới dạng "chưa có điểm này trên bản đồ" nên rất dễ
đổ oan cho người đặt điểm.

Bấm **CHI TIẾT** trong màn Kiểm tra bản đồ để xem đủ mọi trường của từng điểm.

---

## 6. Lỗi thường gặp

| Robot nói gì / hiện gì | Nguyên nhân | Chữa |
|---|---|---|
| *"Trên bản đồ chưa có điểm Quay 3"* | Sai tên, hoặc đang ở bản đồ khác | Đối chiếu danh sách bằng mục 5 |
| *"Tôi chưa định vị được trên bản đồ"* | Chưa bấm Định vị lại sau khi khởi động | Đưa robot về điểm dừng, bấm **Định vị lại** |
| *"Tôi không tìm được đường tới chỗ đó"* | Lối đi bị chắn, hoặc điểm đặt sát tường quá | Dọn lối; dời điểm ra xa vật cản ≥ 50 cm |
| *"Mình đang ở ngay tại vị trí đó rồi"* | Robot đang đứng đúng chỗ điểm đó | Không phải lỗi |
| Robot đi tới nhưng **quay lưng** vào người dân | Lúc lưu điểm robot đang quay sai hướng | Đặt lại điểm đó, chỉnh hướng trước khi lưu |

---

## 7. Ba điều đừng làm

- **Đừng bấm "Định vị lại" khi robot đang đứng chỗ khác.** Nó tự nhận mình đang ở điểm
  gốc bản đồ — sai vị trí thật, và có thể tự chạy đi tìm trạm sạc.
- **Đừng đổi tên điểm sau khi đã chạy thật.** App gọi bằng tên; đổi tên là mọi nút dẫn
  đường chết câm, mà không có lỗi nào hiện lên màn hình cho người dân thấy.
- **Đừng xoá bản đồ cũ để lấy chỗ.** Máy giữ bản đồ của nhiều nơi triển khai khác nhau.
  Xoá nhầm là mất công đi quét lại cả một mặt bằng.

---

## 8. Việc còn treo

Robot **chưa nói được** — tầng giọng nói của PUDU đang khoá với app bên thứ ba
(xem `README.md` mục *Tiếng Việt*). Nên lúc dẫn đường robot sẽ đi trong im lặng, mọi
thông báo chỉ hiện thành chữ trên màn hình. Việc đặt điểm không bị ảnh hưởng.
