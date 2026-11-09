package vn.roboworld.hcc

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import java.io.File
import java.security.MessageDigest

/**
 * Tầng đọc thành tiếng — HAI ĐƯỜNG, thử lần lượt.
 *
 * ĐƯỜNG 1 — TTS của PUDU (`ISpeechUIManager.playTTS`).
 *   Đo trên máy thật 18/08/2026, SN 8BR075801070003, firmware SD2.13.21:
 *   gọi playTTS() KHÔNG ném lỗi nhưng cũng KHÔNG phát ra tiếng, không một dòng
 *   logcat nào từ com.pudutech.speech. Chạy đủ chuỗi khởi tạo của demo hãng
 *   (connect → initConfig → initConfig2 → createNewSession → init → enableUI →
 *   enable) thì `isEnable()` vẫn trả **false**, và tầng thoại bắn
 *   `onError errorType:-5  CAE startRecord fail`.
 *   Đổi applicationId sang `com.pudutech.business.*` cũng không mở được.
 *   → Kết luận: quyền dùng tầng thoại nằm ở phía HÃNG, phải hỏi qua phiếu POC.
 *   Vẫn gọi ở đây vì gọi không hại gì, và ngày hãng mở khoá là app tự nói được
 *   mà không phải sửa một dòng nào.
 *
 * ĐƯỜNG 2 — phát file MP3 đã sinh sẵn bằng giọng nhân bản.
 *   Chạy được ngay, không phụ thuộc hãng. File để ở thẻ nhớ, KHÔNG nhét vào APK:
 *   165 thủ tục đọc thành tiếng là vài trăm MB, nhét vào APK thì mỗi lần sửa một
 *   câu phải cài lại cả app.
 *
 *     /sdcard/roboworld-hcc/tieng-noi/<sha1 của câu>.mp3
 *
 *   Tên file lấy theo SHA-1 của câu đã chuẩn hoá, nên script sinh giọng và app
 *   tự khớp nhau, không cần bảng tra.
 *
 * Câu nào KHÔNG có file thì ghi vào `thieu-tieng.txt` ngay cạnh — đó chính là
 * danh sách việc cho lượt sinh giọng tiếp theo. Không đoán, không im lặng bỏ qua.
 */
object GiongNoi {

    private const val TAG = "HCCGiongNoi"
    @Volatile private var may: MediaPlayer? = null
    @Volatile private var ctx: Context? = null

    fun gan(context: Context) { ctx = context.applicationContext }

    /**
     * Thu muc dat file giong.
     *
     * ⚠ ĐỪNG quay lại dùng /sdcard/roboworld-hcc/. Đã thử trên máy thật 18/08/2026:
     *   thư mục tạo bằng `adb shell mkdir` thuộc về uid khác, app ghi vào là EPERM,
     *   mà từ Android 11 app thường cũng không được tự tạo thư mục ở gốc thẻ nhớ.
     *   Thư mục riêng của app thì vừa ghi được vừa đẩy file vào được bằng adb push:
     *
     *     adb push *.mp3 /sdcard/Android/data/com.pudutech.business.hcc/files/tieng-noi/
     */
    private fun thuMuc(): File {
        val goc = ctx?.getExternalFilesDir(null) ?: File("/sdcard")
        val d = File(goc, "tieng-noi")
        if (!d.exists()) d.mkdirs()
        return d
    }

    /** SHA-1 của câu đã chuẩn hoá — khớp với tools/pudu/sinh-giong-hcc.py */
    fun ma(cau: String): String {
        val chuan = cau.trim().replace(Regex("\\s+"), " ")
        val b = MessageDigest.getInstance("SHA-1").digest(chuan.toByteArray(Charsets.UTF_8))
        return b.joinToString("") { "%02x".format(it) }
    }

    fun tepCua(cau: String) = File(thuMuc(), ma(cau) + ".mp3")

    // ─────────────────────── Con số ───────────────────────
    //
    // ⚠ Bảng này PHẢI khớp từng chữ với hàm doc_so() trong tools/pudu/sinh-giong-hcc.py.
    //   Script sinh file theo tên "mười hai", app tra cũng phải ra "mười hai" — lệch một
    //   chữ là câu báo số câm giữa chừng, mà log thì chỉ báo thiếu một mảnh.

    private val CHUC = arrayOf("", "mười", "hai mươi", "ba mươi", "bốn mươi", "năm mươi",
                               "sáu mươi", "bảy mươi", "tám mươi", "chín mươi")
    private val DON_VI = arrayOf("không", "một", "hai", "ba", "bốn", "năm",
                                 "sáu", "bảy", "tám", "chín")

    /** Đọc số thành chữ: 12 → "mười hai", 25 → "hai mươi lăm", 21 → "hai mươi mốt". */
    fun docSo(n: Int): String {
        if (n < 0 || n > 99) return n.toString()
        if (n < 10) return DON_VI[n]
        val c = n / 10
        val d = n % 10
        if (d == 0) return CHUC[c]
        val dv = when {
            d == 1 && c > 1 -> "mốt"
            d == 5 -> "lăm"
            else -> DON_VI[d]
        }
        return "${CHUC[c]} $dv"
    }

    /**
     * Cắt một câu có chứa CON SỐ thành các mảnh đọc được.
     *
     * Vì sao cần: câu báo số thứ tự đổi theo từng lượt khách —
     * "Số thứ tự của anh chị là 12. Mời anh chị tới quầy số 3." Sinh sẵn mọi tổ hợp
     * số × quầy là hàng nghìn file, vô lý. Cắt ra thì chỉ cần sinh vài mảnh chữ cố
     * định cộng 100 con số, dùng lại được cho mọi câu.
     *
     * Cắt tại mỗi cụm chữ số: phần chữ giữ nguyên, phần số đổi sang chữ.
     * Trả về danh sách mảnh đã bỏ khoảng trắng thừa, bỏ mảnh rỗng.
     */
    fun catManh(cau: String): List<String> {
        val ra = mutableListOf<String>()
        val m = Regex("\\d+").findAll(cau)
        var i = 0
        for (k in m) {
            cau.substring(i, k.range.first).trim().let { if (it.isNotEmpty()) ra.add(it) }
            ra.add(docSo(k.value.toIntOrNull() ?: -1))
            i = k.range.last + 1
        }
        cau.substring(i).trim().let { if (it.isNotEmpty()) ra.add(it) }
        return ra
    }

    /**
     * Đọc một câu.
     *
     * Ba nước, thử lần lượt:
     *   1. có file cho NGUYÊN câu → phát (đường thường dùng, 165 thủ tục đi lối này)
     *   2. câu có con số → cắt mảnh, phát nối tiếp nhau
     *   3. không đủ mảnh → ghi vào thieu-tieng.txt rồi im lặng
     *
     * @param khiXong luôn được gọi — kể cả khi không đọc được — để giao diện không kẹt.
     */
    fun doc(cau: String, khiXong: () -> Unit) {
        val t = cau.trim()
        if (t.isEmpty()) { khiXong(); return }

        dung()

        // Vẫn gọi TTS hãng — hiện chưa ăn, nhưng ngày hãng mở khoá là tự chạy.
        RobotHelper.thuTtsHang(t)

        // Nước 1 — nguyên câu
        val tep = tepCua(t)
        if (tep.exists()) { phatChuoi(listOf(tep), t, khiXong); return }

        // Nước 2 — cắt mảnh theo con số
        if (t.any { it.isDigit() }) {
            val manh = catManh(t)
            val tepManh = manh.map { tepCua(it) }
            if (manh.size > 1 && tepManh.all { it.exists() }) {
                Log.d(TAG, "Ghép ${manh.size} mảnh: ${manh.joinToString(" | ")}")
                phatChuoi(tepManh, t, khiXong)
                return
            }
            // Thiếu mảnh nào thì ghi RIÊNG mảnh đó, không ghi cả câu —
            // ghi cả câu là mai sau đi sinh lại một câu chỉ dùng đúng một lần.
            manh.filterIndexed { i, _ -> !tepManh[i].exists() }.forEach { ghiThieu(it) }
            Log.w(TAG, "Thiếu mảnh cho câu có số: ${t.take(60)}…")
            khiXong()
            return
        }

        // Nước 3 — chịu
        ghiThieu(t)
        Log.w(TAG, "Chưa có tiếng cho câu: ${t.take(60)}…  (${tep.name})")
        khiXong()
    }

    /** Phát lần lượt một chuỗi file, xong hết mới gọi khiXong. */
    private fun phatChuoi(ds: List<File>, nhan: String, khiXong: () -> Unit) {
        var i = 0
        fun tiep() {
            if (i >= ds.size) { dung(); khiXong(); return }
            val f = ds[i]; i++
            try {
                may = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    setDataSource(f.absolutePath)
                    // Theo đúng mức "Âm lượng giọng nói" cán bộ đặt trong Cài đặt robot.
                    // Đọc lại mỗi câu — kéo thanh trượt xong là câu sau đã đúng mức mới.
                    AmLuong.heSo().let { setVolume(it, it) }
                    setOnCompletionListener { runCatching { it.release() }; may = null; tiep() }
                    setOnErrorListener { _, a, b ->
                        Log.w(TAG, "MediaPlayer lỗi $a/$b ở ${f.name}"); dung(); khiXong(); true
                    }
                    prepare()
                    start()
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Không phát được ${f.name}: ${e.message}")
                khiXong()
            }
        }
        Log.d(TAG, "Đang đọc: ${nhan.take(50)}…")
        tiep()
    }

    /** Đọc và CHỜ đọc xong. ⚠ Đừng gọi từ luồng chính — nó chặn luồng gọi. */
    fun docCho(cau: String, hanGiay: Long = 25): Boolean {
        val xong = java.util.concurrent.CountDownLatch(1)
        doc(cau) { xong.countDown() }
        return xong.await(hanGiay, java.util.concurrent.TimeUnit.SECONDS)
    }

    fun dung() {
        val m = may ?: return
        may = null
        runCatching { if (m.isPlaying) m.stop() }
        runCatching { m.release() }
        RobotHelper.dungTtsHang()
    }

    /** Có đang đọc không — hàng chờ số dùng để khỏi cắt ngang câu đang đọc. */
    fun dangDoc(): Boolean = runCatching { may?.isPlaying == true }.getOrDefault(false)

    /** Bao nhiêu câu đã có tiếng — hiện lúc lắp đặt để biết bộ giọng đủ chưa. */
    fun soTepCoSan(): Int = thuMuc().listFiles { f -> f.name.endsWith(".mp3") }?.size ?: 0

    private fun ghiThieu(cau: String) {
        try {
            val f = File(thuMuc().parentFile, "thieu-tieng.txt")
            val da = if (f.exists()) f.readLines().toHashSet() else hashSetOf()
            val dong = ma(cau) + "\t" + cau.replace("\n", " ")
            if (dong !in da) f.appendText(dong + "\n")
        } catch (e: Throwable) {
            Log.w(TAG, "Không ghi được thieu-tieng.txt: ${e.message}")
        }
    }
}
