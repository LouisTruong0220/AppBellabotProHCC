package vn.roboworld.hcc

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * HÀNG CHỜ SỐ THỨ TỰ — lõi của tính năng lấy số.
 *
 * Ba bên dùng chung đúng một hàng chờ này:
 *   · người dân   chạm màn hình robot → lấy số            (Cau.laySo)
 *   · cán bộ      mở trình duyệt → xem, bấm Xử lý/Xong    (MayChu)
 *   · robot       đọc lời gọi ở điểm lễ tân               (goiSo bên dưới)
 *
 * ⚠ MỌI thứ trong đây phải chịu được nhiều luồng gọi cùng lúc: 7 quầy hỏi 2 giây
 *   một lần, người dân bấm lấy số, robot đọc — ba luồng khác nhau. Nên toàn bộ
 *   phần đọc/sửa danh sách đều nằm trong `synchronized (khoa)`.
 *
 * ⚠ Số chạy CHUNG MỘT DÃY trong ngày (1, 2, 3…), không chia dãy theo quầy. Lý do:
 *   lời gọi luôn kèm số quầy ("số thứ tự 7 đi đến quầy số 2") nên không thể lẫn,
 *   mà một dãy thì người dân dễ hiểu hơn mã chữ cái kiểu A05.
 */
object HangCho {

    private const val TAG = "TTHCHangCho"
    private val khoa = Any()

    /** Một lượt xếp hàng. */
    data class Luot(
        val so: Int,
        val quay: Int,
        val tenThuTuc: String,
        val idThuTuc: Int,
        val lucLay: Long,
        var trangThai: String = CHO,     // CHO · DANG_XU_LY · XONG · BO_QUA
        var lucGoi: Long = 0,
        var lucXong: Long = 0
    )

    const val CHO = "dang-cho"
    const val DANG_XU_LY = "dang-xu-ly"
    const val XONG = "xong"
    const val BO_QUA = "bo-qua"

    private val ds = mutableListOf<Luot>()
    private var soTiepTheo = 1
    private var ngayHienTai = ""

    /* ── Hàng đợi lời gọi ──
       Hai quầy bấm Xử lý cùng lúc thì robot không thể nói hai câu chồng lên nhau,
       và cũng không được bỏ sót câu nào. Nên lời gọi xếp vào đây, một luồng riêng
       rút ra đọc lần lượt. */
    private val loiGoi = LinkedBlockingQueue<Luot>()
    private val thoGoi = Executors.newSingleThreadExecutor()
    @Volatile private var dangChay = false

    // ───────────────────────── Người dân lấy số ─────────────────────────

    /**
     * Cấp một số mới.
     * @return lượt vừa cấp, hoặc null nếu thiếu dữ liệu.
     */
    fun laySo(idThuTuc: Int, tenThuTuc: String, quay: Int): Luot? {
        if (tenThuTuc.isBlank() || quay <= 0) {
            Log.w(TAG, "laySo: thiếu dữ liệu (id=$idThuTuc quay=$quay)")
            return null
        }
        synchronized(khoa) {
            doiNgayNeuCan()
            val l = Luot(soTiepTheo, quay, tenThuTuc, idThuTuc, System.currentTimeMillis())
            soTiepTheo++
            ds.add(l)
            ghiFile()
            Log.d(TAG, "Cấp số ${l.so} → quầy ${l.quay} · ${l.tenThuTuc.take(50)}")
            return l
        }
    }

    /** Bao nhiêu người đang chờ trước mặt ở quầy đó — để nói cho người dân yên tâm. */
    fun soNguoiChoTruoc(l: Luot): Int = synchronized(khoa) {
        ds.count { it.quay == l.quay && it.trangThai == CHO && it.so < l.so }
    }

    // ───────────────────────── Cán bộ thao tác ─────────────────────────

    fun danhSachTheoQuay(quay: Int): List<Luot> = synchronized(khoa) {
        doiNgayNeuCan()
        ds.filter { it.quay == quay }.sortedBy { it.so }
    }

    fun tatCa(): List<Luot> = synchronized(khoa) {
        doiNgayNeuCan(); ds.sortedBy { it.so }
    }

    /**
     * Cán bộ bấm **Xử lý**: lượt chuyển sang đang xử lý VÀ robot đi gọi.
     * @return true nếu đổi được trạng thái.
     */
    fun batDauXuLy(so: Int): Boolean {
        val l = synchronized(khoa) {
            val x = ds.firstOrNull { it.so == so } ?: return false
            if (x.trangThai != CHO) {
                Log.w(TAG, "Số $so đang ở trạng thái ${x.trangThai}, không gọi lại")
                return false
            }
            x.trangThai = DANG_XU_LY
            x.lucGoi = System.currentTimeMillis()
            ghiFile()
            x
        }
        loiGoi.offer(l)
        batThoGoi()
        return true
    }

    fun ketThuc(so: Int, boQua: Boolean): Boolean = synchronized(khoa) {
        val x = ds.firstOrNull { it.so == so } ?: return false
        x.trangThai = if (boQua) BO_QUA else XONG
        x.lucXong = System.currentTimeMillis()
        ghiFile()
        true
    }

    /** Bấm nhầm thì lùi lại — cán bộ có 30 giây để sửa, xem trang quầy. */
    fun hoanTac(so: Int): Boolean = synchronized(khoa) {
        val x = ds.firstOrNull { it.so == so } ?: return false
        x.trangThai = CHO
        x.lucGoi = 0; x.lucXong = 0
        ghiFile()
        true
    }

    // ───────────────────────── Robot gọi số ─────────────────────────

    private fun batThoGoi() {
        if (dangChay) return
        dangChay = true
        thoGoi.execute {
            try {
                while (true) {
                    val l = loiGoi.poll(30, TimeUnit.SECONDS) ?: break
                    goiMotLuot(l)
                }
            } finally {
                dangChay = false
                // Có lượt lọt vào đúng lúc vòng lặp thoát thì bật lại ngay.
                if (loiGoi.isNotEmpty()) batThoGoi()
            }
        }
    }

    /**
     * Robot ra điểm lễ tân rồi đọc lời gọi.
     *
     * ⚠ Robot đang bận dẫn đường thì CHỜ, không cắt ngang: người dân đang đi theo
     *   robot mà nó quay đầu bỏ đi giữa chừng là hỏng hẳn niềm tin. Chờ tối đa 90
     *   giây, quá thì gọi tại chỗ — thà đứng đọc còn hơn nuốt mất lượt của người ta.
     */
    private fun goiMotLuot(l: Luot) {
        /* ⚠ Để NGUYÊN CHỮ SỐ ở đây, đừng gọi docSo() nữa.
           Bản Nova phải đổi số sang chữ vì TTS của hãng đọc "12" thành "một hai".
           Bản PUDU đọc bằng file MP3 sinh sẵn: GiongNoi tự cắt câu tại chỗ có chữ số
           rồi ghép mảnh (xem GiongNoi.catManh). Đổi sang chữ ở đây là câu không còn
           chữ số nào để cắt, thành ra phải sinh sẵn cả nghìn tổ hợp số × quầy. */
        val cau = "Xin mời quý khách số thứ tự ${l.so}, đi đến quầy số ${l.quay}."

        var doi = 0
        while (RobotHelper.dangDanDuong && doi < 90) { Thread.sleep(1000); doi++ }
        if (doi > 0) Log.d(TAG, "Chờ ${doi}s cho robot dẫn đường xong rồi mới gọi số ${l.so}")

        val diem = DIEM_LE_TAN
        val xong = java.util.concurrent.CountDownLatch(1)
        RobotHelper.dieuHuongToi(
            diem,
            khiToiNoi = { xong.countDown() },
            khiLoi = { loi ->
                // Chưa đặt điểm lễ tân trên bản đồ thì vẫn phải gọi, chỉ là gọi tại chỗ.
                Log.w(TAG, "Không ra được điểm '$diem' ($loi) — gọi tại chỗ")
                xong.countDown()
            }
        )
        xong.await(60, TimeUnit.SECONDS)

        /* Đọc HAI LẦN, và CHỜ đọc xong hẳn từng lần.
           Người lớn tuổi nghe một lần rất dễ trượt, mà trượt số là mất lượt — app
           không in phiếu nên không có đường tra lại.
           ⚠ Phải dùng docCho() chứ không dùng doc(): doc() bắn lệnh rồi trả về ngay,
             nên hai quầy bấm Xử lý cùng lúc là lời gọi sau cắt ngang lời gọi trước.
             Đo 11/08/2026: hai câu cách nhau đúng 1,2 giây. */
        Cau.baoGoiSo(l.so, l.quay)          // chữ lên màn hình trước, cho người nhìn
        MainApplication.nhoCauTuDoc(cau)    // để tiếng vọng TTS khỏi hiện lặp
        RobotHelper.docCho(cau)
        Thread.sleep(700)
        RobotHelper.docCho(cau)
        Thread.sleep(900)                   // nghỉ giữa hai lượt gọi, đỡ dồn dập
        Log.d(TAG, "Đã gọi: $cau")
    }

    /**
     * Đọc số cho máy phát âm đúng: "12" → "mười hai".
     *
     * KHÔNG dùng cho câu gọi số nữa (xem chú thích ở goiMotLuot) — bản chuyển số sang
     * chữ mà cả app lẫn script sinh giọng cùng dùng nằm ở GiongNoi.docSo().
     * Giữ hàm này để mã còn khớp với bản Nova, và phòng khi cần đọc số ra chữ ở chỗ khác.
     */
    @Suppress("unused")
    private fun docSo(n: Int): String {
        val chuc = arrayOf("", "mười", "hai mươi", "ba mươi", "bốn mươi", "năm mươi",
                           "sáu mươi", "bảy mươi", "tám mươi", "chín mươi")
        val dv = arrayOf("không", "một", "hai", "ba", "bốn", "năm", "sáu", "bảy", "tám", "chín")
        if (n < 10) return dv[n]
        if (n < 100) {
            val c = n / 10; val d = n % 10
            if (d == 0) return chuc[c]
            val donVi = when {
                d == 1 && c > 1 -> "mốt"
                d == 5 -> "lăm"
                else -> dv[d]
            }
            return "${chuc[c]} $donVi"
        }
        return n.toString()   // quá 99 lượt một ngày thì cứ đọc số, hiếm khi tới
    }

    const val DIEM_LE_TAN = "Le tan"

    // ───────────────────────── Lưu ra thẻ nhớ ─────────────────────────
    //
    // Mất điện hoặc app bị đóng giữa buổi mà mất hàng chờ thì cả trung tâm loạn:
    // người dân cầm số cũ, máy phát lại từ 1. Nên ghi ra file sau MỖI thay đổi
    // (ngày một file, vài chục dòng, không đáng lo về tốc độ).

    private fun ngay(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun thuMuc(): File? {
        val ctx = MainApplication.boiCanh ?: return null
        return File(ctx.getExternalFilesDir(null), "hang-cho").apply { mkdirs() }
    }

    /** Sang ngày mới thì hàng chờ về rỗng và số chạy lại từ 1. */
    private fun doiNgayNeuCan() {
        val h = ngay()
        if (h == ngayHienTai) return
        if (ngayHienTai.isEmpty()) { napFile(h); return }
        Log.d(TAG, "Sang ngày mới ($ngayHienTai → $h) — hàng chờ về 1")
        ds.clear(); soTiepTheo = 1; ngayHienTai = h; ghiFile()
    }

    /** Gọi lúc app khởi động để lấy lại hàng chờ nếu app vừa bị đóng giữa buổi. */
    fun nap() = synchronized(khoa) { napFile(ngay()) }

    private fun napFile(h: String) {
        ngayHienTai = h
        ds.clear(); soTiepTheo = 1
        val f = File(thuMuc() ?: return, "$h.json")
        if (!f.exists()) return
        runCatching {
            val j = JSONObject(f.readText())
            val a = j.optJSONArray("ds") ?: JSONArray()
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                ds.add(Luot(o.getInt("so"), o.getInt("quay"), o.getString("ten"),
                            o.optInt("id"), o.optLong("lucLay"),
                            o.optString("trangThai", CHO),
                            o.optLong("lucGoi"), o.optLong("lucXong")))
            }
            soTiepTheo = j.optInt("soTiepTheo", (ds.maxOfOrNull { it.so } ?: 0) + 1)
            Log.d(TAG, "Nạp lại hàng chờ ngày $h: ${ds.size} lượt, số tiếp theo $soTiepTheo")
        }.onFailure { Log.w(TAG, "Không đọc được hàng chờ: ${it.message}") }
    }

    private fun ghiFile() {
        val f = File(thuMuc() ?: return, "${ngayHienTai.ifEmpty { ngay() }}.json")
        runCatching {
            val a = JSONArray()
            ds.forEach {
                a.put(JSONObject()
                    .put("so", it.so).put("quay", it.quay).put("ten", it.tenThuTuc)
                    .put("id", it.idThuTuc).put("lucLay", it.lucLay)
                    .put("trangThai", it.trangThai)
                    .put("lucGoi", it.lucGoi).put("lucXong", it.lucXong))
            }
            f.writeText(JSONObject().put("soTiepTheo", soTiepTheo).put("ds", a).toString())
        }.onFailure { Log.w(TAG, "Không ghi được hàng chờ: ${it.message}") }
    }

    // ───────────────────────── Đổi sang JSON cho trang web ─────────────────────────

    fun luotThanhJson(l: Luot): JSONObject = JSONObject()
        .put("so", l.so).put("quay", l.quay).put("ten", l.tenThuTuc)
        .put("trangThai", l.trangThai).put("lucLay", l.lucLay)
        .put("lucGoi", l.lucGoi).put("lucXong", l.lucXong)

    fun jsonTheoQuay(quay: Int): String {
        val a = JSONArray()
        danhSachTheoQuay(quay).forEach { a.put(luotThanhJson(it)) }
        return JSONObject().put("quay", quay).put("ngay", ngay()).put("ds", a).toString()
    }

    fun jsonBangSo(): String {
        val a = JSONArray()
        synchronized(khoa) {
            ds.filter { it.trangThai == DANG_XU_LY }.sortedBy { it.lucGoi }
              .forEach { a.put(luotThanhJson(it)) }
        }
        return JSONObject().put("ngay", ngay()).put("dang_goi", a).toString()
    }
}
