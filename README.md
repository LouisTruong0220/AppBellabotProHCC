# Trợ lý hành chính công — chạy trên BellaBot Pro (PUDU)

App tra cứu thủ tục hành chính cho Trung tâm Phục vụ Hành chính công phường Mông Dương,
**bản chạy trên robot PUDU BellaBot Pro**. Bản gốc chạy trên OrionStar GreetingBot Nova
nằm ở `11-app-nova/tra-cuu-tthc-mong-duong/`.

Đã cài và chạy thật trên máy **SN 8BR075801070003** ngày 18/08/2026.

---

## Cái gì dùng lại, cái gì viết mới

Toàn bộ phần *nội dung và giao diện* dùng lại nguyên vẹn. Chỉ tầng chạm vào phần cứng
là viết lại — đó là chủ ý ngay từ đầu khi làm bản Nova.

| File | Nguồn | Ghi chú |
|---|---|---|
| `assets/index.html` (1,6 MB) | **Chép nguyên** từ bản Nova | 165 thủ tục · menu 3 tầng · lấy số · tìm kiếm |
| `assets/bieu-cam/` | **Chép nguyên** | 10 clip biểu cảm màn chờ |
| `assets/quay.html`, `bang-so.html` | **Chép nguyên** | Trang cho cán bộ 7 quầy |
| `HangCho.kt`, `MayChu.kt` | **Chép nguyên**, chỉ đổi tên gói | Hàng chờ số + máy chủ web cổng 8080 |
| `Cau.kt` | Sửa | Giữ y nguyên tên hàm; cụm micro/AI trả về "chưa có" |
| `MainActivity.kt` | Sửa | Khác chỗ xử lý màn hình — xem bên dưới |
| `MainApplication.kt` | **Viết lại**, 604 dòng → ~100 | Bỏ hẳn tầng AI của OrionStar |
| `RobotHelper.kt` | **Viết mới hoàn toàn** | PdCoreSDK thay cho RobotApi |
| `GiongNoi.kt` | **Viết mới** | Tầng đọc thành tiếng hai đường |

**Muốn sửa nội dung thủ tục hay giao diện:** sửa `khung-app.html` bên thư mục Nova,
chạy `dung-app.py`, rồi chép `index.html` sang `app/src/main/assets/`. Đừng sửa hai nơi.

---

## Thoát ra ngoài khi đang ở trong app

App chạy toàn màn hình và là mô-đun tự khởi động, nên không có thanh điều hướng Android.
Lối ra là **nút ☰ mờ ở góc trên bên trái** — xem `NutThoat.kt`.

**BẤM GIỮ 1,2 giây**, không phải bấm một cái. Bấm một cái chỉ hiện lời nhắc.
Cố ý làm vậy: robot đặt ở quầy công, một nút "Thoát" bấm phát ra ngay là kiểu gì cũng có
người tò mò bấm rồi robot nằm ở màn hình cài đặt Android cho tới khi có cán bộ đi qua.

Bảng chọn có bốn đường: **Cài đặt robot** · **Màn hình chính của robot** ·
**Định vị lại trên bản đồ** · **Danh sách ứng dụng Android**.

⚠ **ĐỪNG gọi thẳng ComponentName của màn hình hãng.** Đã thử 18/08/2026:
`com.pudutech.function.setting.ui.SettingActivity` **không exported** — app bên thứ ba gọi
vào là `SecurityException "not exported from uid 10084"`. Phải đi qua bộ định tuyến,
action `com.pudutech.action.router.MAIN` + category `com.pudutech.category.router.<LOẠI>`,
với LOẠI ∈ `SETTINGS` · `GOHOME` · `CHECKLOCATION` · `ONECHARGE`
(đọc bằng `adb shell dumpsys package com.pudutech.business.function`).
Nhớ `setPackage` — app này cũng khai `router.MAIN` nên không ghim là có ngày Android bật
hộp chọn ứng dụng giữa mặt người dân.

⚠ Khi robot **chưa định vị**, ba đường đầu đều bị hãng chặn lại ở màn *"Định vị lại"* trước.
Đó là cổng của hãng, không phải lỗi app. Đường **"Danh sách ứng dụng Android"** thì luôn
vào được — dùng làm lối thoát cuối.

### Đặt điểm trên bản đồ

→ đọc **[huong-dan-dat-diem-ban-do.md](huong-dan-dat-diem-ban-do.md)**.
Tóm tắt: `Quay 1`…`Quay 7` + `Le tan` + `Tram sac`, **không dấu tiếng Việt**; hoa thường và
khoảng trắng thừa thì app tự chuẩn hoá. Vào bằng **Cài đặt → Cài đặt bản đồ → Chỉnh sửa
bản đồ**, màn này **hỏi mật khẩu kỹ thuật của PUDU** — chưa có thì hỏi hãng trước khi ra
hiện trường. Kiểm lại bằng `CAU.kiemTraBanDo()` trong DevTools, đừng nhìn bằng mắt.

---

## Dẫn đường — đã kiểm trên bản đồ thật

Kiểm 18/08/2026 trên bản đồ `vanphong` (dựng ở văn phòng Roboworld): **7/7 quầy khớp**,
robot đã định vị xong.

### ⚠ Tên điểm nằm ở `id`, KHÔNG phải `name`

Đây là cái bẫy tốn thời gian nhất của cả buổi. Bản đồ có 7 điểm, mà:

```
name='' id='Quay 1' key='Quay 1' group='' mode=Table
```

`PdDestination.getName()` trả **chuỗi rỗng cho mọi điểm**. Đọc mỗi `name` thì màn tự kiểm
báo "chưa đặt quầy nào" trong khi bản đồ đủ cả bảy — rất dễ đổ oan cho người đặt điểm.

`RobotHelper.tenCua()` lấy giá trị khác rỗng đầu tiên theo `name → id → key`.
Cả dẫn đường lẫn màn tự kiểm đều đi qua hàm đó, **đừng sửa nó về đọc mỗi `name`**.

### Màn tự kiểm bản đồ

Bấm giữ ☰ → **"Kiểm tra bản đồ"**. Hỏi thẳng robot, trả về tên bản đồ · đã định vị chưa ·
✔/✘ từng quầy `Quay 1`…`Quay 7` + `Le tan` + `Tram sac` · toàn bộ điểm đọc được · một dòng
kết luận. Nút **CHI TIẾT** đổ đủ mọi trường của từng điểm.

Dấu ✔ dò bằng **đúng phép so tên** mà lệnh dẫn đường dùng (`RobotHelper.quayCoSan`),
nên nó là lời hứa thật chứ không phải bản sao cấu hình của app.

### Câu robot nói khi thiếu điểm

Chi tiết kỹ thuật (tên bản đồ, danh sách điểm, quầy đã đặt) **chỉ ghi vào logcat**.
Câu robot nói ra loa là *"Chỗ đó tôi chưa được chỉ đường tới. Nhờ anh chị hỏi cán bộ ở
quầy giúp tôi ạ."* — người dân đứng đó không cần nghe tên điểm trên bản đồ, và sự cố nội
bộ thì không nên biến thành chuyện của khách.

## ⚠ Tiếng Việt — máy NÓI ĐƯỢC, nhưng app bên thứ ba thì không

Đo lại kỹ ngày 18/08/2026, firmware `SD2.13.21`. Kết luận đổi so với lần đo đầu:
**không phải máy thiếu tiếng Việt — máy có sẵn giọng tiếng Việt rất tốt.**

### Máy có gì

Vào **Cài đặt → Chức năng robot → Cài đặt tương tác → Giao tiếp bằng giọng nói** thì thấy
một danh sách **giọng tổng hợp tiếng Việt `vi-VN-Chirp3-HD-*`** (Achernar, Achird, Algenib,
Algieba, Alnilam, Aoede, Autonoe, Callirrhoe, Charon, Despina…), đang chọn sẵn
**`vi-VN-Chirp3-HD-Achernar`**. Bấm ▶ là robot đọc thử, nghe rõ và tự nhiên.

`Chirp3-HD` là tên giọng của Google Cloud TTS.

### Hãng cho robot nói bằng cách nào

Bắt log đúng lúc bấm ▶:

```
I/OtherPlayer(4012): OtherPlayer setPlayPath
    /sdcard/pudu/tts/live/vi-VN/vi-VN-Chirp3-HD-Achernar/523f455ae6e26d4f89eb2148ad4a7da2.mp3
```

`4012` là `com.pudutech.business.function` — **app nghiệp vụ của hãng, KHÔNG phải dịch vụ
`com.pudutech.speech`**. Nghĩa là:

- TTS **không tổng hợp tại chỗ**. Hãng lấy file MP3 đã tổng hợp sẵn (qua đám mây) rồi
  **phát bằng player thường**, cache ở `/sdcard/pudu/tts/live/<ngôn ngữ>/<giọng>/<mã băm>.mp3`.
- Máy đang giữ **591 file tiếng Việt** cache từ 2025 tới nay.
- Cả năng lực TTS nằm ở **tầng app nghiệp vụ**, không nằm ở PdCoreSDK.

Đó là lý do vì sao gọi qua SDK không ra gì: nó không đi qua đường đó.

### Đã thử những gì — tất cả đều không ra tiếng

| Thử | Kết quả |
|---|---|
| `reqTTS("vi", …)` · `("en", …)` · `("zh", …)` | `ByteString size=0`, không callback |
| `reqTTS("vi-VN", …, "vi-VN-Chirp3-HD-Achernar", …)` | `size=0` |
| `reqTTS("vi-VN", …, "", …)` — không tên giọng | `size=0` |
| `reqTTS("vi_VN", …, "vi-VN-Chirp3-HD-Achernar", …)` | `size=0` |
| `speechUIManager().playTTS(...)` | im lặng, không lỗi, không log |
| Đủ chuỗi khởi tạo của demo hãng | `isEnable()` vẫn **false** |
| Đổi `applicationId` sang `com.pudutech.business.*` | không mở được |
| Bắt log riêng tiến trình `com.pudutech.speech` lúc gọi | **không một dòng nào về TTS** |

Dịch vụ giọng nói **có** nhận lệnh của app ta — gọi `initConfig2("","")` thì nó ném lỗi
thật từ `PuduDialogueClient.initConfig2`. Nhưng `reqTTS` thì không chạm tới đoạn mã nào
có log. Không phải lỗi tham số, không phải lỗi ngôn ngữ, không phải âm lượng
(`STREAM_MUSIC` 15/15, không mute).

Về micro, dịch vụ tự báo `onError errorType:-5 "CAE startRecord fail"`, và log hệ thống
có dòng SELinux `avc: denied { ioctl } for path="/dev/snd/pcmC1D0c"` — thiết bị thu âm.

**→ Việc phải hỏi PUDU:** mở `reqTTS`/`playTTS` của PdCoreSDK cho app bên thứ ba, hoặc
cho một đường gọi TTS khác. Đây là câu hỏi có trọng lượng, vì máy đã có sẵn giọng tiếng
Việt chất lượng cao mà SDK không với tới được.

### Đường đi hiện tại: app tự phát MP3 — ĐÃ CHẠY THẬT

`GiongNoi.kt` phát file MP3 bằng `MediaPlayer` của Android, hoàn toàn không phụ thuộc
tầng thoại của hãng. **Đã kiểm chứng end-to-end 18/08/2026**: đẩy một file MP3 tiếng Việt
lên máy, vào màn tư vấn, robot đọc ra loa. Bằng chứng khách quan:

```
D/HCCGiongNoi: Đang đọc: Mời quý khách lựa chọn thủ tục cần tư vấn.…
D/AudioTrack: flags: 0x8
dumpsys media.audio_flinger →  28973  10093  com.pudutech.business.hcc   (có track đang phát)
```

Nên **robot nói được ngay hôm nay**, chỉ cần sinh trước bộ giọng.

Nơi đặt file:

```
/sdcard/Android/data/com.pudutech.business.hcc/files/tieng-noi/<sha1 của câu>.mp3
```

Tên file là SHA-1 của câu **đã chuẩn hoá** (bỏ khoảng trắng thừa hai đầu, gộp khoảng
trắng liên tiếp thành một). Script sinh giọng và app tự khớp nhau, không cần bảng tra.

Câu nào chưa có file thì app ghi vào `…/files/thieu-tieng.txt` dạng `<sha1>	<câu>`.
**Đó chính là danh sách việc cho lượt sinh giọng tiếp theo** — không phải đoán.

```powershell
$adb = "D:\RBW_Claude\tools\pudu\platform-tools-pudu\adb.exe"

# lấy danh sách câu còn thiếu về máy
& $adb pull /sdcard/Android/data/com.pudutech.business.hcc/files/thieu-tieng.txt

# đẩy giọng đã sinh lên robot
& $adb push giong\*.mp3 /sdcard/Android/data/com.pudutech.business.hcc/files/tieng-noi/
```

⚠ **ĐỪNG** quay lại dùng `/sdcard/roboworld-hcc/`. Thư mục tạo bằng `adb shell mkdir`
thuộc uid khác, app ghi vào là `EPERM`; từ Android 11 app thường cũng không tự tạo được
thư mục ở gốc thẻ nhớ.

### Chọn giọng nào để sinh

| Lựa chọn | Được | Mất |
|---|---|---|
| **Google Chirp3-HD `vi-VN`** | **Trùng đúng giọng robot đang dùng** — khách nghe không thấy vênh khi app khác nói | Cần khoá Google Cloud TTS |
| **ElevenLabs "Bella BellaBot VI"** (`50SWaRvGlWK9PFx8N9Al`) | Đã nhân bản sẵn, dây chuyền đã chạy (`tools/elevenlabs/`) | Khác giọng máy tự nói |

⚠ Giọng Bella là giọng diễn viên hãng PUDU thuê, **không phải tài sản Roboworld** — đưa ra
tài liệu công khai phải hỏi anh Trường.

### Bộ giọng — đã sinh, đã chạy trên máy

Giọng chốt: **ElevenLabs "Bella BellaBot VI"** (`50SWaRvGlWK9PFx8N9Al`), model `eleven_v3`,
stability **1.0** (đọc hàng loạt 165 câu thì cần đều giọng; thả thấp là mỗi lần sinh một
kiểu, ghép lại nghe rõ là chắp vá).

Dây chuyền: `tools/pudu/sinh-giong-hcc.py`. Mặc định **chỉ in ra xem hết bao nhiêu ký tự**,
phải thêm `--that-su-lam` mới thật sự gọi API — tài khoản ElevenLabs là của chung công ty.

```powershell
python tools\pudu\sinh-giong-hcc.py                              # xem trước, không tiêu gì
python tools\pudu\sinh-giong-hcc.py --nhom co-dinh --that-su-lam
python tools\pudu\sinh-giong-hcc.py --nhom so      --that-su-lam
python tools\pudu\sinh-giong-hcc.py --nhom thu-tuc --that-su-lam
```

Chạy lại là **làm tiếp từ chỗ dang dở**, không sinh lại file đã có.

| Nhóm | Số câu | Ký tự | Trạng thái |
|---|---:|---:|---|
| `co-dinh` — lời mời, câu dẫn đường, câu báo lỗi | 26 | 1.210 | ✅ đã sinh |
| `so` — số đọc thành chữ 0…99 | 100 | 1.036 | ✅ đã sinh |
| `thu-tuc --linh-vuc dat-dai` — Đất đai · Nhà ở · Xây dựng | 20 | 15.146 | ✅ đã sinh |
| `thu-tuc --linh-vuc van-hoa` — Văn hoá · Thể thao · Tôn giáo | 11 | 4.552 | ✅ đã sinh 18/08 |
| `thu-tuc` — 134 thủ tục còn lại | 134 | **69.981** | ⏳ chờ duyệt |

Đã sinh **155 file · 27 MB**. Kiểm trên máy 18/08/2026: mở thủ tục *"Đính chính Giấy chứng
nhận đã cấp lần đầu có sai sót"* → robot đọc liền **53,7 giây** (2.367.288 khung ở 44,1 kHz),
xong thì nút tự đổi lại thành *"Nghe robot đọc lại"*.

Sinh theo từng lĩnh vực, xem danh sách bằng `--ke-nhom`:

```powershell
python tools\pudu\sinh-giong-hcc.py --ke-nhom          # liệt kê 11 lĩnh vực + số ký tự
python tools\pudu\sinh-giong-hcc.py --nhom thu-tuc --linh-vuc dat-dai --that-su-lam
```

| Lĩnh vực còn lại | Câu | Ký tự |
|---|---:|---:|
| `hoc-hanh` Học hành · Trường lớp | 33 | 17.778 |
| `giao-thong` Giao thông · Tàu thuyền | 27 | 15.220 |
| `moi-truong` Môi trường · Nước · Thiên tai | 18 | 9.167 |
| `nong-nghiep` Nông nghiệp · Chăn nuôi · Rừng · Thuỷ sản | 13 | 6.878 |
| `kinh-doanh` Kinh doanh · Buôn bán | 14 | 6.623 |
| `tro-cap` Trợ cấp · Hộ nghèo · Bảo trợ xã hội | 13 | 6.139 |
| `tre-em` Trẻ em · Gia đình · Nhận con nuôi | 11 | 6.057 |
| `viec-lam` Việc làm · Lao động | 3 | 1.507 |
| `khieu-nai` Khiếu nại · Tố cáo | 2 | 612 |

### Hạn mức ElevenLabs — đọc được gì, không đọc được gì

`tools/elevenlabs/xem-su-dung.py` — báo cáo đã tiêu bao nhiêu.

⚠ **API không cho biết hạn mức của gói.** Key của công ty thiếu quyền `user_read`:
`/v1/user` và `/v1/user/subscription` đều trả 401. Nhưng `/v1/usage/character-stats`
thì mở — đọc được lượng **đã dùng** theo ngày, chính xác. Muốn biết **còn lại** thì
phải xem hạn mức ở `elevenlabs.io → Subscription` rồi chạy
`xem-su-dung.py --han-muc <số>`.

⚠ **`eleven_v3` trên tài khoản này tính NỬA credit cho mỗi ký tự.** Đo 18/08/2026:
gửi 17.382 ký tự, ElevenLabs trừ 8.680 credit — đúng 0,5. Đây là tỉ lệ của **gói đang
dùng**, không phải hằng số của ElevenLabs; model khác hoặc gói khác có thể tính 1:1.
Đo lại bằng cách ghi số ký tự vừa gửi rồi lấy chênh lệch credit chia cho nó.

⚠⚠ **Và `character-stats` ĐẾM THIẾU.** Đối chiếu 18/08/2026 với trang Subscription:

| Nguồn | Đã dùng trong kỳ |
|---|---:|
| API `/usage/character-stats` | 23.369 |
| **Trang web (số thật)** | **34.973 / 38.093** |

Lệch ~11.600. `character-stats` hình như chỉ đếm Text-to-Speech, không đếm Studio /
Music / Sound Effects / nhân bản giọng — mà tài khoản này **dùng chung cả phòng**.
→ **Trước khi sinh lô lớn, mở web xem số thật.**

**Tình trạng 18/08/2026:** gói **Starter**, kỳ gia hạn **ngày 22 hàng tháng**,
hạn mức kỳ này 38.093 credit (30.000 của gói + phần cộng dồn). Đã dùng 34.973,
còn 3.120 → sinh nhóm `van-hoa` hết 2.276 → **còn khoảng 844 credit**.

Còn 134 thủ tục = 69.981 ký tự = **34.991 credit**.

| Mốc | Sinh được |
|---|---|
| Còn lại kỳ này (~844) | không đủ một lĩnh vực nào, kể cả `khieu-nai` (306) thì cũng chỉ được 2 câu |
| Sau gia hạn 22/08, Starter 30.000 | **~114 / 134** thủ tục |
| Nếu nâng Creator 121.000 (22 $/tháng) | **đủ cả 134**, dư 86.009 credit |

⚠ Tài khoản dùng chung cả phòng — 844 credit còn lại có thể bị đồng nghiệp tiêu
trước ngày 22.


### ⚠ Lỗi nội dung: 34/165 thủ tục bị lặp chữ "Thủ tục"

Trường `doc` trong `app-data.json` được ghép theo khuôn `"Thủ tục " + tên`, mà **34 thủ tục
đã có sẵn chữ "Thủ tục" ở đầu tên** → robot đọc thành *"Thủ tục Thủ tục đề nghị tổ chức
đại hội…"*. Nhóm `van-hoa` dính **9/11**, nhóm `dat-dai` dính **6/20**.

Chưa sửa, vì sửa là đổi chuỗi → đổi mã SHA-1 → phải **sinh lại** 15 file đã trả tiền.

→ **Sửa trước khi sinh 134 thủ tục còn lại**, để chỉ trả tiền một lần. Chỗ sửa nằm ở
`11-app-nova/tra-cuu-tthc-mong-duong/tools/dung-du-lieu.py` (nơi ghép trường `doc`):
bỏ tiền tố nếu tên đã bắt đầu bằng "Thủ tục". Sinh lại 15 file kia tốn thêm ~4.400 credit —
hoặc chấp nhận nghe hơi lặp, dân vẫn hiểu.

### Câu có con số — ghép mảnh, không sinh sẵn mọi tổ hợp

Câu báo số đổi theo từng lượt: *"Số thứ tự của anh chị là 12. Mời anh chị tới quầy số 3."*
Sinh sẵn mọi tổ hợp số × quầy là hàng nghìn file, vô lý.

`GiongNoi.doc()` đi ba nước:

1. có file cho **nguyên câu** → phát (165 thủ tục đi lối này)
2. câu **có chữ số** → `catManh()` cắt tại mỗi cụm số, đổi số sang chữ, phát nối tiếp
3. thiếu mảnh → ghi **riêng mảnh đó** vào `thieu-tieng.txt`, không ghi cả câu

⚠ Bảng đổi số sang chữ nằm ở **hai nơi** — `GiongNoi.docSo()` (Kotlin) và `doc_so()` trong
`sinh-giong-hcc.py`. **Phải khớp từng chữ.** Lệch một chữ ("hai mươi lăm" ↔ "hai mươi năm")
là câu báo số câm giữa chừng, mà log chỉ báo thiếu một mảnh.

Vì vậy `HangCho.goiMotLuot` **để nguyên chữ số** trong câu, không gọi `docSo()` như bản Nova.

### Âm lượng — bám theo cài đặt của robot

App phát tiếng đúng mức **"Âm lượng giọng nói"** cán bộ đặt trong Cài đặt → Chung.

⚠ Hai thanh trượt đó **không phải** luồng âm thanh của Android. Đo 18/08/2026: kéo từ 4%
lên 70% mà `dumpsys audio` không đổi một luồng nào. Hãng tự nhân hệ số trong player của họ,
và PdCoreSDK **không có hàm đọc** (cả 911 phương thức chỉ có callback `onVolumeChanged`).

Nên `AmLuong.kt` đọc thẳng chỗ hãng cất:

```
/sdcard/pudu/appConfig/Common.preferences_pb        (Jetpack DataStore Preferences)
khoá "VolumeConfig_CurrentVoice"  →  12 05 15 <float LE>   = 0…100
```

Đã kiểm: đặt thanh trượt 30% → app log `Âm lượng giọng nói của robot: 30.0`; đặt 70% →
đọc 70. Đọc lại theo `mtime` nên kéo thanh trượt xong là câu sau đã đúng mức mới, không
phải khởi động lại app.

⚠ **Cấp quyền đọc thẻ nhớ một lần cho mỗi robot**, nếu không app dùng mức mặc định 70:

```powershell
& $adb shell appops set com.pudutech.business.hcc MANAGE_EXTERNAL_STORAGE allow
```


---

## Đặt làm ứng dụng mặc định khi mở máy

Tài liệu hãng (`os-sdk/002-Development Environment`) chỉ nói *"đặt applicationId là
`com.pudutech.business.xxx` thì app sẽ hiện trong danh sách tự khởi động"*.
**Thử trên máy thật: CHƯA ĐỦ.** Đặt đúng tên gói mà danh sách vẫn không có app.

Phải khai thêm nguyên bộ intent-filter dưới đây — lấy từ chính mô-đun "Dẫn chỗ" của hãng
(`adb shell dumpsys package com.pudutech.business.usher`):

```xml
<intent-filter>
    <action android:name="android.intent.action.MAIN"/>
    <action android:name="com.pudutech.action.router.LAUNCHER.FUNCTION"/>
    <action android:name="com.pudutech.action.router.MAIN"/>
    <action android:name="com.pudutech.action.router.work.plan"/>
    <category android:name="com.pudutech.SETTING.HOME"/>
    <category android:name="com.pudutech.category.router.HCC"/>
    <category android:name="android.intent.category.DEFAULT"/>
</intent-filter>
```

`com.pudutech.category.router.HCC` là tên riêng của mô-đun này. **Đừng dùng trùng tên
của hãng** (`USHER`, `DELIVERY`…) — trùng là hai app tranh nhau một khe, Android bật
hộp chọn ứng dụng ngay giữa mặt người dân.

Kiểm tra dây nối đã thông chưa, không cần khởi động lại máy:

```powershell
adb shell am start -a com.pudutech.action.router.LAUNCHER.FUNCTION `
  -c com.pudutech.category.router.HCC -c com.pudutech.SETTING.HOME
```

Đúng thì app mở ra. Đây chính là intent launcher dùng để gọi mô-đun tự khởi động.

### Đường bấm trên máy

**Cài đặt → Chức năng robot → Cài đặt mô-đun → Chọn mô-đun tự khởi động**

**Đã có bằng chứng thiết lập ăn**: đọc `/sdcard/pudu/appConfig/Common.preferences_pb`
thấy khoá `key_home_launch_module` ghi

```json
{"isEnable":true,"module":"com.pudutech.business.hcc","routerInfo":{
  "action":"com.pudutech.action.router.LAUNCHER.FUNCTION",
  "activityName":"vn.roboworld.hcc.MainActivity",
  "categoryList":["com.pudutech.SETTING.HOME","com.pudutech.category.router.HCC",
                  "android.intent.category.DEFAULT"],
  "pkgName":"com.pudutech.business.hcc"}}
```

✅ **ĐÃ SỬA 19/08/2026** — app hiện ra với tên **"Hành chính công"** và icon đỏ hình tờ giấy.

Nguyên nhân ô trắng: `android:label` và `android:icon` phải khai **TRÊN THẺ
`<intent-filter>`**, không phải trên `<activity>`. Launcher đọc `ResolveInfo.labelRes` /
`ResolveInfo.icon`, mà hai trường đó chỉ nhận giá trị khai ngay trên thẻ filter:

```xml
<intent-filter
    android:label="@string/ten_mo_dun"
    android:icon="@drawable/ic_mo_dun_hcc">
```

Bằng chứng: mô-đun "Dẫn chỗ" của hãng khai đúng như vậy (đọc manifest `usher.apk` bằng
`aapt2 dump xmltree`), và chính `Common.preferences_pb` ở trên ghi `"icon":0,"label":0`
cho bản cũ.

Ghi chú cũ *"launcher lấy tên từ kho chuỗi của chính nó, không ăn android:label"* là **SAI**
— không cần hỏi hãng gì cả.

⚠ Sau khi cài đè bản mới, vào lại màn đó **chọn lại** mô-đun này để `routerInfo` trong
`Common.preferences_pb` được ghi lại kèm icon/label mới.

Muốn trả robot về như cũ: vào đúng màn đó, chọn lại **"Giao hàng"**.

### Sau khi khởi động lại

Robot **luôn** dừng ở màn **"Định vị lại"** trước, bất kể đặt mô-đun nào. Đây là cổng của
hãng, không liên quan tới app. Đưa robot về đúng điểm dừng quen thuộc rồi bấm
**"Định vị lại"**, xong nó vào thẳng app.

⚠ **Đừng bấm "Định vị lại" khi robot đang đứng chỗ khác.** Bấm là robot tự nhận mình
đang ở điểm gốc bản đồ — sai vị trí thật, và có thể tự chạy đi tìm trạm sạc.

---

## Màn hình PUDU khác màn hình Nova — đừng bê lời giải cũ sang

| | Nova | PUDU BellaBot Pro |
|---|---|---|
| Điểm ảnh | 1920×1080 | 1280×800 |
| Mật độ | 560 dpi | **160 dpi** |
| WebView được | **548×309 CSS px** | **1280×800 CSS px** |
| Hệ quả | cỡ chữ gốc rơi xuống 4,56 px, chạm SÀN 8 px của Chrome → mọi thứ to lên 1,75 lần, chữ tràn thẻ | không có chuyện đó, không cần chữa gì |

`index.html` khai `meta viewport width=1920` nên trang bố cục ở 1920 px rồi thu nhỏ vừa
1280 — đúng tỉ lệ thiết kế gốc.

**Toàn màn hình:** đặt cờ `systemUiVisibility` trong Activity là **chưa đủ** trên ROM này,
thanh trạng thái vẫn đè lên đầu trang (`dumpsys display` ghi `app 1280 x 752`). Phải khai
`windowFullscreen` ở theme — xem `res/values/styles.xml`.

Dải biểu tượng Wi-Fi / pin / giờ ở góc trên phải **là lớp phủ riêng của PUDU**, không phải
thanh trạng thái Android. App không giấu được, và mọi app của hãng cũng vậy.

---

## Micro và AI — đã giấu khỏi giao diện

`index.html` dùng chung với bản Nova, mà bên đó micro chạy được nên có nút micro cạnh ô
tìm kiếm và nút "Trò chuyện với Robot". Trên PUDU hai nút đó bấm vào không có gì xảy ra.

Ở quầy hành chính công đó là lỗi tệ nhất: người dân bấm, không thấy phản hồi, rồi nghĩ
mình làm sai.

Nên `MainActivity` chèn một đoạn JS lúc `onPageFinished` để giấu `#mic1` và `.nut-ai`
(có `MutationObserver` vì giao diện dựng lại thẻ khi đổi màn). **Không sửa `index.html`** —
giữ đúng một bản HTML cho cả hai loại máy. Ngày hãng mở khoá micro thì xoá khối
`GIAU_NUT_MICRO` là xong.

---

## Dẫn đường — khác hẳn bản Nova

| | Nova | PUDU |
|---|---|---|
| Cách gọi | `startNavigation("Quay 1")` — hãng tự tra tên | tự tìm `PdDestination` → `NaviDestinationsParams` → `INaviTask` → `.start()` |
| Đổi lại | đơn giản | nắm được hàng đợi nhiều điểm, huỷ từng điểm |

Bản PUDU so tên điểm **bỏ phân biệt hoa thường và khoảng trắng thừa** (`RobotHelper.chuanHoa`)
— cán bộ đặt điểm bằng tay nên `"Quay 1"` / `"quay 1"` / `"Quay  1"` là chuyện thường.

⚠ **Tên BẢN ĐỒ có tiền tố `0#0#`** (ví dụ `0#0#hcc-MongDuong-QN`), **tên ĐIỂM thì không.**
Đừng nhầm hai thứ khi so tên.

Máy đang giữ **45 bản đồ**, trong đó đã có sẵn `0#0#hcc-MongDuong-QN`.

---

## Bộ build

Gradle 8.7 · AGP 8.5.2 · Kotlin 1.9.24 · JDK 17 · `minSdk 27` · `compileSdk 34`

`minSdk` **27**, không phải 26 như tài liệu web ghi. Bắt buộc có
`packaging { resources.pickFirsts += "META-INF/pudusdk_release.kotlin_module" }`.

```powershell
cd D:\RBW_Claude\11-app-pudu\hcc-mong-duong
.\gradlew.bat assembleDebug

$adb = "D:\RBW_Claude\tools\pudu\platform-tools-pudu\adb.exe"
& $adb connect 192.168.1.183
& $adb install -r -t app\build\outputs\apk\debug\app-debug.apk
& $adb shell am start -n com.pudutech.business.hcc/vn.roboworld.hcc.MainActivity
```

⚠ `C:\Windows\adb.exe` là bản PUDU **1.0.39 cũ** và nằm trên PATH — gõ `adb` trần là
trúng nó. Luôn dùng `tools/pudu/platform-tools-pudu/adb.exe`.

⚠ Git Bash biến `/sdcard` thành `D:/Git/sdcard` — phải `export MSYS_NO_PATHCONV=1`,
và đích của `adb pull` phải viết kiểu Windows.

---

## Bẫy đã dính khi lắp đặt từ xa

**Màn hình chờ của robot tự bật lên và cướp cú chạm.** `ScreenSaverActivity` của
`com.pudutech.business.function` nhảy lên sau ít phút không ai đụng. Gõ `adb shell input tap`
theo toạ độ đã ngắm từ trước là chạm vào màn khác — đã có lần rơi thẳng vào **app kiểm
tra xuất xưởng** của hãng.

→ Trước MỖI cú chạm, kiểm tra cửa sổ đang ở trên cùng:

```bash
adb shell dumpsys window | grep mCurrentFocus | tail -1
```

**WebView không nhận `input tap` trần** — phải dùng `input touchscreen tap`, hoặc
`input swipe x y x y 120` (chạm giữ 120 ms).

---

## Việc còn treo

| Việc | Vì sao quan trọng |
|---|---|
| **Sinh bộ giọng tiếng Việt** cho 165 thủ tục + câu cố định | Chưa có thì robot **câm** — mất một nửa giá trị |
| **Hỏi hãng: mở tầng thoại cho app bên thứ ba** | Mở được thì bỏ hẳn bộ MP3, và có luôn micro + hội thoại |
| **Hỏi hãng: mô-đun bên thứ ba khai tên hiển thị kiểu gì** | Cán bộ đang thấy một dòng trống trong danh sách |
| Vặn to "Âm lượng giọng nói" (đang 4%) rồi thử lại TTS | Loại trừ nốt khả năng đơn giản nhất |
| Đặt 7 điểm `Quay 1`…`Quay 7` trên bản đồ `hcc-MongDuong-QN` | Chưa có thì nút dẫn đường báo "chưa có điểm này" |
| Kiểm chứng tự khởi động sau khi định vị xong tại hiện trường | Đã thông đường intent, còn thiếu đúng bước này |
