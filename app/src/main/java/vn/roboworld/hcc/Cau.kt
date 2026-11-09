package vn.roboworld.hcc

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONObject

/**
 * Cây cầu hai chiều giữa giao diện web (index.html) và phần cứng robot.
 *
 *   Web → Android : đối tượng CAU trong JavaScript, xem các hàm gắn @JavascriptInterface
 *   Android → Web : gọi hàm JS qua evaluateJavascript
 *
 * ⚠ GIỮ NGUYÊN TÊN HÀM của bản chạy trên Nova. index.html là CÙNG MỘT FILE cho cả hai
 *   loại máy — đổi tên một hàm ở đây là giao diện gọi hụt và im lặng không làm gì.
 *
 * Khác bản Nova ở đúng một chỗ: cụm micro và hội thoại AI trả về "chưa có" thay vì gọi
 * AgentCore. Xem chú thích trong MainApplication về việc tầng thoại của PUDU đang khoá.
 *
 * ⚠ Mọi hàm @JavascriptInterface đều chạy trên LUỒNG RIÊNG của WebView, không phải luồng
 *   giao diện. Đụng tới WebView thì phải post về luồng chính, nếu không app văng.
 */
object Cau {

    private const val TAG = "HCCCau"

    @Volatile
    private var web: WebView? = null

    fun gan(w: WebView) {
        web = w
    }

    fun go() {
        web = null
    }

    // ───────────────────── Đọc thành tiếng ─────────────────────

    /** Web gọi: CAU.doc("...") */
    @JavascriptInterface
    fun doc(vanBan: String?) {
        val t = vanBan.orEmpty()
        if (t.isBlank()) return
        RobotHelper.doc(t) { baoDocXong() }
    }

    @JavascriptInterface
    fun dungDoc() = RobotHelper.dungDoc()

    private fun baoDocXong() {
        val w = web ?: return
        w.post { w.evaluateJavascript("window.robotDocXong && window.robotDocXong()", null) }
    }

    // ───────────────────── Micro & AI — CHƯA CÓ trên PUDU ─────────────────────
    //
    // Trả lời trung thực thay vì giả vờ chạy. Giao diện đọc micDangMo() và thongTinAI()
    // để tự ẩn nút micro, nên người dân không bao giờ thấy một cái nút bấm vào không có
    // gì xảy ra. Đó là lỗi tệ nhất ở quầy hành chính công: người dân tưởng mình làm sai.

    @JavascriptInterface
    fun batMic() {
        Log.d(TAG, "batMic() — tầng thoại PUDU đang khoá, bỏ qua")
    }

    @JavascriptInterface
    fun tatMic() {
    }

    @JavascriptInterface
    fun micDangMo(): Boolean = false

    @JavascriptInterface
    fun hoiRobot(cau: String?) {
        Log.d(TAG, "hoiRobot('${cau.orEmpty().take(40)}') — chưa có tầng AI trên PUDU")
        guiLoiNoi(
            "robot",
            "Xin lỗi anh chị, máy này chưa bật được phần trò chuyện. " +
                    "Mời anh chị chọn thủ tục trên màn hình giúp tôi ạ.",
            true
        )
    }

    @JavascriptInterface
    fun xoaNguCanh() {
    }

    @JavascriptInterface
    fun moTaManHinh(mo: String?) {
    }

    @JavascriptInterface
    fun thongTinAI(): String = MainApplication.thongTinAI()

    @JavascriptInterface
    fun thuLLM(cauHoi: String?, dungKhoTriThuc: Boolean) {
        Log.d(TAG, "thuLLM — chưa có tầng AI trên PUDU")
    }

    /** Đẩy từng mẩu lời nói lên màn hình trò chuyện. Gọi được từ luồng phụ. */
    fun guiLoiNoi(ai: String, chu: String, xong: Boolean) {
        val w = web ?: return
        val js = "window.nhanLoiNoi && window.nhanLoiNoi(${JSONObject.quote(ai)}," +
                "${JSONObject.quote(chu)},$xong)"
        w.post { w.evaluateJavascript(js, null) }
    }

    // ───────────────────── Lấy số thứ tự ─────────────────────

    /** @return JSON {so, quay, ten, cho_truoc} — hoặc {} nếu không cấp được. */
    @JavascriptInterface
    fun laySo(idThuTuc: Int, tenThuTuc: String?, quay: Int): String {
        val l = HangCho.laySo(idThuTuc, tenThuTuc.orEmpty(), quay) ?: return "{}"
        return JSONObject()
            .put("so", l.so).put("quay", l.quay).put("ten", l.tenThuTuc)
            .put("cho_truoc", HangCho.soNguoiChoTruoc(l))
            .toString()
    }

    /** Địa chỉ trang cán bộ — hiện ở góc màn chờ để lắp đặt xong là biết vào đâu. */
    @JavascriptInterface
    fun diaChiMayChu(): String = MayChu.diaChiDayDu()

    /**
     * Số của mình đã tới lượt chưa — cho nút "Xem lại số của tôi".
     * App KHÔNG in phiếu nên người dân phải nhớ số; đây là đường tra lại duy nhất.
     */
    @JavascriptInterface
    fun traSo(so: Int): String {
        val l = HangCho.tatCa().firstOrNull { it.so == so } ?: return "{}"
        return HangCho.luotThanhJson(l).put("cho_truoc", HangCho.soNguoiChoTruoc(l)).toString()
    }

    /** Robot vừa gọi một số — đẩy lên màn hình để người ngồi xa cũng đọc được. */
    fun baoGoiSo(so: Int, quay: Int) {
        val w = web ?: return
        val js = "window.baoGoiSo && window.baoGoiSo($so,$quay)"
        w.post { w.evaluateJavascript(js, null) }
    }

    // ───────────────────── Dẫn đường ─────────────────────

    /**
     * Robot dẫn người dân tới quầy.
     *
     * Tên điểm trên bản đồ phải là "Quay 1" … "Quay 7". Bản PUDU so tên đã bỏ phân biệt
     * hoa thường và khoảng trắng thừa (xem RobotHelper.chuanHoa) nên dễ tính hơn bản Nova,
     * nhưng vẫn phải đúng chữ — xem huong-dan-dat-ten-diem-ban-do.md.
     *
     * ⚠ SUỐT QUÃNG ĐƯỜNG ĐI ROBOT IM LẶNG. Chỉ nói đúng một câu lúc bắt đầu.
     *   Người dân vừa đi theo robot vừa phải nghe cán bộ gọi tên mình ở quầy.
     *   Diễn biến dọc đường chỉ hiện thành chữ. Ngoại lệ duy nhất là LỖI.
     */
    @JavascriptInterface
    fun danDuongToiQuay(soQuay: Int) {
        val diem = "Quay $soQuay"
        Log.d(TAG, "Xin dẫn đường tới $diem")

        val vuong = RobotHelper.lyDoChuaSanSang()
        if (vuong != null) {
            baoDanDuong("loi", vuong)
            RobotHelper.doc(vuong) {}
            return
        }

        baoDanDuong("bat-dau", "")
        RobotHelper.doc("Xin mời đi theo tôi.") {}

        RobotHelper.dieuHuongToi(
            diem,
            // Tới nơi: KHÔNG nói gì ở đây. Lớp web tự trả người dân về trang thủ tục
            // và cho robot đọc lại hướng dẫn từ đầu — nói thêm ở đây là chồng tiếng.
            khiToiNoi = { baoDanDuong("toi-noi", "") },
            khiLoi = { loi ->
                RobotHelper.dungDieuHuong()
                baoDanDuong("loi", loi)
                RobotHelper.doc(loi) {}
            },
            // Chỉ đẩy chữ lên màn hình, tuyệt đối không gọi RobotHelper.doc ở đây
            khiCapNhat = { tin -> baoDanDuong("dang-di", tin) }
        )
    }

    @JavascriptInterface
    fun dungDanDuong() {
        RobotHelper.dungDieuHuong()
        baoDanDuong("da-dung", "Tôi dừng lại rồi ạ.")
    }

    @JavascriptInterface
    fun lyDoChuaDanDuongDuoc(): String = RobotHelper.lyDoChuaSanSang() ?: ""

    @JavascriptInterface
    fun robotSanSang(): Boolean = RobotHelper.lyDoChuaSanSang() == null

    /**
     * Hỏi robot: bản đồ đang có những điểm nào, đã định vị chưa.
     * Dùng lúc lắp đặt để biết chắc 7 điểm "Quay 1".."Quay 7" đã đặt đúng tên chưa —
     * thay vì bấm dẫn đường rồi ngồi đoán vì sao robot đứng im.
     */
    @JavascriptInterface
    fun kiemTraBanDo() {
        RobotHelper.daDinhVi { dinhVi ->
            RobotHelper.layDanhSachDiem { ds ->
                val w = web ?: return@layDanhSachDiem
                val j = JSONObject()
                    .put("daDinhVi", dinhVi)
                    .put("banDo", RobotHelper.tenBanDo())
                    .put("danhSachDiem", ds ?: "")
                Log.d(TAG, "Bản đồ: đãĐịnhVị=$dinhVi điểm=$ds")
                val js = "window.baoTinhTrangBanDo && " +
                        "window.baoTinhTrangBanDo(${JSONObject.quote(j.toString())})"
                w.post { w.evaluateJavascript(js, null) }
            }
        }
    }

    // Bản Nova có traThuTucChoAI() và guiGoiYSangManHinh() để phục vụ mô hình của hãng.
    // Bản PUDU chưa có mô hình nên bỏ hẳn — thêm hàm rỗng chỉ tổ làm người đọc sau
    // tưởng có đường dây AI mà đi tìm.

    private fun baoDanDuong(trangThai: String, loiNhan: String) {
        val w = web ?: return
        val js = "window.baoDanDuong && window.baoDanDuong(${JSONObject.quote(trangThai)}," +
                "${JSONObject.quote(loiNhan)})"
        w.post { w.evaluateJavascript(js, null) }
    }

    private val main = Handler(Looper.getMainLooper())
}
