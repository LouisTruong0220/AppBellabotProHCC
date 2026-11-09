package vn.roboworld.hcc

import android.app.Application
import android.util.Log

/**
 * Điểm khởi động app.
 *
 * ⚠ File này MỎNG HƠN HẲN bản chạy trên Nova (604 dòng → hơn 100 dòng), và đó là chủ ý.
 *
 * Trên Nova, MainApplication ôm cả tầng AI của OrionStar: AppAgent, AgentCore, LLMConfig,
 * bốn công tắc SDK, ba lớp chặn bịa. Toàn bộ cụm đó là của AgentOS — PUDU không có thứ
 * tương đương mà app bên thứ ba gọi được.
 *
 * Đo trên máy thật 18/08/2026 (BellaBot Pro, SN 8BR075801070003, firmware SD2.13.21):
 * chạy đủ chuỗi khởi tạo tầng thoại của demo hãng vẫn ra
 *   - isEnable() = false
 *   - onError errorType:-5  "CAE startRecord fail"
 *   - playTTS() im lặng tuyệt đối, không callback, không log
 * Đổi applicationId sang com.pudutech.business.* cũng không mở được.
 * → Micro và hội thoại AI là CỔNG PHÍA HÃNG, phải hỏi qua phiếu POC đang mở.
 *
 * Nên bản PUDU này bỏ hẳn tầng AI thay vì giả vờ có. Giao diện tự biết ẩn nút micro
 * khi thongTinAI() báo chưa có — xem window.thamDoAI trong index.html.
 *
 * Phần LÕI của app không cần AI: menu ba tầng 143 thẻ, tra cứu 165 thủ tục,
 * lấy số thứ tự, dẫn đường tới quầy. Tất cả chạy đủ.
 */
class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        boiCanh = this
        GiongNoi.gan(this)
        RobotHelper.ketNoi(this)
        HangCho.nap()
        MayChu.batDau(this)
        Log.d(TAG, "App hành chính công khởi động — gói ${packageName}")
    }

    companion object {
        private const val TAG = "HCCApp"

        @Volatile
        @JvmStatic
        var boiCanh: MainApplication? = null
            private set

        /**
         * Bản Nova cần hàm này để lọc tiếng vọng: robot đọc ra loa thì micro nghe lại
         * chính nó rồi hiện thành lời của người dân. Bản PUDU chưa có micro nên
         * chưa có tiếng vọng để lọc — giữ hàm để HangCho.kt dùng chung được mã với
         * bản Nova, ngày mở được micro thì điền ruột vào đây.
         */
        @JvmStatic
        fun nhoCauTuDoc(cau: String) {
            Log.d(TAG, "robot tự đọc: ${cau.take(60)}")
        }

        /** Tự kiểm lúc lắp đặt — giao diện gọi qua CAU.thongTinAI(). */
        @JvmStatic
        fun thongTinAI(): String = buildString {
            append("Nền tảng: PUDU BellaBot Pro (PdCoreSDK)\n")
            append("Gói app: ${boiCanh?.packageName ?: "?"}\n")
            append("SDK nối được: ${RobotHelper.daKetNoi}\n")
            append("Bản đồ đang dùng: ${RobotHelper.tenBanDo()}\n")
            append("Micro + AI hội thoại: CHƯA CÓ — tầng thoại của hãng đang khoá\n")
            append("Đọc thành tiếng: file giọng nhân bản, đã có ${GiongNoi.soTepCoSan()} câu\n")
        }
    }
}
