package vn.roboworld.hcc

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.pudutech.open_sdk_core.PdRobotSdk
import com.pudutech.open_sdk_core.api.RobotInitStatus
import com.pudutech.open_sdk_core.api.map.PdDestination
import com.pudutech.open_sdk_core.api.map.PdMapPoint
import com.pudutech.open_sdk_core.api.map.currentDestinationsFl
import com.pudutech.open_sdk_core.api.navigation.INaviStateListener
import com.pudutech.open_sdk_core.api.navigation.INaviTask
import com.pudutech.open_sdk_core.api.navigation.MoveDirection
import com.pudutech.open_sdk_core.api.navigation.NavError
import com.pudutech.open_sdk_core.api.navigation.NaviDestinationsParams
import com.pudutech.open_sdk_core.api.navigation.PdDestinationWithSteady
import com.pudutech.open_sdk_core.api.navigation.PdMoveError
import com.pudutech.open_sdk_core.api.navigation.RobotMoveEvent
import com.pudutech.open_sdk_core.api.navigation.RobotPauseEvent
import com.pudutech.open_sdk_core.mapManager
import com.pudutech.open_sdk_core.navigationManager
import com.pudutech.open_sdk_core.speechUIManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Lớp bọc PdCoreSDK — mọi thứ chạm vào phần cứng robot PUDU đi qua đây.
 *
 * Đây là file DUY NHẤT khác hẳn bản chạy trên OrionStar Nova. Giao diện web,
 * kho 165 thủ tục, hàng chờ số thứ tự, máy chủ cho cán bộ — dùng lại nguyên vẹn.
 * Giữ đúng tên hàm của bản Nova (doc / docCho / dieuHuongToi / lyDoChuaSanSang)
 * để Cau.kt và HangCho.kt không phải sửa gì.
 *
 * Khác biệt lớn nhất so với RobotApi của Nova:
 *   Nova : một lệnh startNavigation(tên điểm) — hãng tự tra tên trên bản đồ.
 *   PUDU : phải TỰ tìm PdDestination trong bản đồ hiện tại, gói vào
 *          NaviDestinationsParams, tạo INaviTask rồi mới start().
 *          Đổi lại nắm được cả hàng đợi nhiều điểm và huỷ từng điểm một.
 */
object RobotHelper {

    private const val TAG = "HCCRobot"
    private val main = Handler(Looper.getMainLooper())
    private val nen = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    var daKetNoi = false; private set

    @Volatile
    var dangDanDuong = false; private set

    /** Việc đang chạy — giữ lại để dừng được giữa chừng. */
    @Volatile
    private var viec: INaviTask? = null

    var onTrangThaiDoi: ((ketNoi: Boolean, treo: Boolean) -> Unit)? = null

    // ───────────────────────── Kết nối ─────────────────────────

    fun ketNoi(context: Context) {
        nen.launch {
            PdRobotSdk.connectStatusFl.collect { (trangThai, kq) ->
                daKetNoi = trangThai == RobotInitStatus.Success
                Log.d(TAG, "SDK: $trangThai ${kq?.code ?: ""} ${kq?.msg ?: ""}")
                main.post { onTrangThaiDoi?.invoke(daKetNoi, false) }
            }
        }
        PdRobotSdk.init(context.applicationContext)
    }

    // ───────────────────────── Đọc thành tiếng ─────────────────────────
    //
    // Vì sao ở đây chỉ có hai hàm mỏng: toàn bộ quyết định "đọc bằng đường nào"
    // nằm ở GiongNoi. Xem chú thích dài trong file đó về việc TTS hãng đang khoá.

    fun doc(vanBan: String, khiXong: () -> Unit) = GiongNoi.doc(vanBan) { main.post { khiXong() } }

    fun docCho(vanBan: String, hanGiay: Long = 25): Boolean = GiongNoi.docCho(vanBan, hanGiay)

    fun dungDoc() = GiongNoi.dung()

    /** Gọi TTS của hãng — hiện chưa ăn, xem GiongNoi. Nuốt mọi lỗi. */
    fun thuTtsHang(cau: String) {
        runCatching { PdRobotSdk.speechUIManager()?.playTTS(cau, mutableMapOf()) }
    }

    fun dungTtsHang() {
        runCatching { PdRobotSdk.speechUIManager()?.resetWakeup() }
    }

    // ───────────────────────── Bản đồ ─────────────────────────

    /**
     * Danh sách điểm đến trên bản đồ ĐANG DÙNG.
     *
     * ⚠ Tên BẢN ĐỒ trên máy có tiền tố 0#0# (ví dụ 0#0#hcc-MongDuong-QN),
     *   nhưng tên ĐIỂM thì không. Đừng nhầm hai thứ khi so tên.
     */
    private suspend fun diemHienCo(): List<PdDestination> =
        withTimeoutOrNull(4000) { PdRobotSdk.mapManager()?.currentDestinationsFl?.first() } ?: emptyList()

    fun tenBanDo(): String =
        runCatching { PdRobotSdk.mapManager()?.getCurrentMapName() ?: "" }.getOrDefault("")

    fun daDinhVi(ketQua: (Boolean) -> Unit) {
        val v = runCatching { PdRobotSdk.navigationManager()?.isLocationSuccess() == true }
            .getOrDefault(false)
        main.post { ketQua(v) }
    }

    /** Đổ tình trạng bản đồ ra chuỗi cho màn tự kiểm lúc lắp đặt. */
    fun layDanhSachDiem(ketQua: (String?) -> Unit) {
        nen.launch {
            val ds = diemHienCo().joinToString(", ") { tenCua(it) }
            main.post { ketQua(ds) }
        }
    }

    /**
     * Tên từng điểm trên bản đồ đang dùng.
     *
     * ⚠ KHÔNG đọc mỗi `name`. Đo trên máy thật 18/08/2026 với bản đồ `vanphong`:
     *   bản đồ có 7 điểm mà `PdDestination.getName()` trả về CHUỖI RỖNG cho cả 7.
     *   Tên người đặt nằm ở `id`. Nên ở đây lấy tên đầu tiên khác rỗng theo thứ tự
     *   name → id → key. Sửa chỗ này là hỏng luôn cả dẫn đường lẫn màn tự kiểm,
     *   vì cả hai đều đi qua đây.
     */
    fun danhSachDiem(ketQua: (List<String>) -> Unit) {
        nen.launch {
            val ds = diemHienCo().map { tenCua(it) }.filter { it.isNotBlank() }
            main.post { ketQua(ds) }
        }
    }

    /** Tên người đặt cho một điểm — xem chú thích ở danhSachDiem. */
    private fun tenCua(d: PdDestination): String =
        listOf(d.name, d.id, d.key).firstOrNull { !it.isNullOrBlank() }.orEmpty()

    /** Đổ đủ mọi trường của từng điểm — dùng khi tên không khớp mà không hiểu vì sao. */
    fun moTaDiem(ketQua: (List<String>) -> Unit) {
        nen.launch {
            val ds = diemHienCo().map {
                "name='${it.name}' id='${it.id}' key='${it.key}' " +
                        "group='${it.group}' mode=${it.mode}"
            }
            main.post { ketQua(ds) }
        }
    }

    /**
     * Những số quầy ĐÃ đặt điểm trên bản đồ.
     *
     * Dò bằng đúng phép so tên mà dieuHuongToi dùng, nên kết quả ở đây là lời hứa thật:
     * số nào có trong danh sách này thì bấm nút dẫn đường chắc chắn tìm thấy điểm.
     * Trước đây app mặc định luôn có đủ 7 quầy — cán bộ đặt thiếu một điểm thì tới lúc
     * có người dân đứng trước mặt mới lòi ra.
     *
     * Dò tới 12 chứ không phải 7: trung tâm khác có thể nhiều quầy hơn, và đếm thừa
     * thì vô hại còn đếm thiếu thì giấu mất quầy đã đặt đúng.
     */
    fun quayCoSan(tenDiem: List<String>): List<Int> {
        val co = tenDiem.map { chuanHoa(it) }.toHashSet()
        return (1..12).filter { chuanHoa("Quay $it") in co }
    }

    /** Có điểm tên này trên bản đồ không — dùng cho Le tan, Tram sac. */
    fun coDiem(ten: String, tenDiem: List<String>): Boolean =
        tenDiem.any { chuanHoa(it) == chuanHoa(ten) }

    // ───────────────────────── Dẫn đường ─────────────────────────

    /**
     * Dẫn khách tới một điểm đã đặt tên trên bản đồ.
     *
     * So tên KHÔNG phân biệt hoa thường và bỏ khoảng trắng thừa: cán bộ đặt điểm
     * bằng tay trên máy tính bảng, "Quay 1" / "quay 1" / "Quay  1" là chuyện thường,
     * mà sai một ký tự thì robot đứng im không báo gì.
     */
    fun dieuHuongToi(
        tenDiem: String,
        khiToiNoi: () -> Unit,
        khiLoi: (String) -> Unit,
        khiCapNhat: (String) -> Unit = {}
    ) {
        val vuong = lyDoChuaSanSang()
        if (vuong != null) {
            main.post { khiLoi(vuong) }
            return
        }

        nen.launch {
            val ds = diemHienCo()
            val can = chuanHoa(tenDiem)
            val diem = ds.firstOrNull { chuanHoa(tenCua(it)) == can }
            if (diem == null) {
                // Chi tiết kỹ thuật ĐỂ TRONG LOG, không đọc ra loa: câu này robot nói to
                // giữa sảnh cho người dân nghe, liệt kê tên điểm ra là vừa khó hiểu vừa
                // biến sự cố nội bộ thành chuyện của khách.
                Log.w(TAG, "Không thấy điểm $tenDiem. Bản đồ ${tenBanDo()} đang có: " +
                        ds.joinToString(", ") { tenCua(it) } +
                        "  → quầy đã đặt: " + quayCoSan(ds.map { tenCua(it) }))
                main.post {
                    khiLoi("Chỗ đó tôi chưa được chỉ đường tới. " +
                            "Nhờ anh chị hỏi cán bộ ở quầy giúp tôi ạ.")
                }
                return@launch
            }

            try {
                dangDanDuong = true
                val t = PdRobotSdk.navigationManager()?.newDestinationNaviTask(
                    NaviDestinationsParams(listOf(PdDestinationWithSteady(diem, false)))
                )
                if (t == null) {
                    dangDanDuong = false
                    main.post {
                        khiLoi("Robot chưa nhận được lệnh đi. Nhờ nhân viên kỹ thuật kiểm tra giúp ạ.")
                    }
                    return@launch
                }
                viec = t
                t.addNaviStatusListener(nghe(khiToiNoi, khiLoi, khiCapNhat))
                t.start()
                Log.d(TAG, "Bắt đầu đi tới ${tenCua(diem)} trên bản đồ ${tenBanDo()}")
            } catch (e: Throwable) {
                dangDanDuong = false
                Log.w(TAG, "Lỗi khi tạo việc dẫn đường: ${e.message}")
                main.post {
                    khiLoi("Có trục trặc khi bắt đầu đi (${e.javaClass.simpleName}). " +
                            "Mời anh chị chờ nhân viên hỗ trợ.")
                }
            }
        }
    }

    private fun nghe(
        khiToiNoi: () -> Unit,
        khiLoi: (String) -> Unit,
        khiCapNhat: (String) -> Unit
    ) = object : INaviStateListener {

        override fun onStart() {
            Log.d(TAG, "onStart")
        }

        override fun onStop() {
            Log.d(TAG, "onStop")
            dangDanDuong = false
        }

        override fun onArrive(point: PdMapPoint) {
            Log.d(TAG, "Tới nơi: $point")
            dangDanDuong = false
            main.post { khiToiNoi() }
        }

        /**
         * Dọc đường chỉ ĐẨY CHỮ lên màn hình, tuyệt đối không cho robot nói.
         * Người dân vừa đi theo robot vừa phải nghe cán bộ gọi tên mình ở quầy —
         * robot lải nhải dọc đường là át mất. Ngoại lệ duy nhất là lỗi.
         */
        override fun onMovingState(event: RobotMoveEvent) {
            Log.d(TAG, "onMovingState $event")
            val tin = when (event) {
                RobotMoveEvent.Avoid -> "Phía trước đang có vật cản, tôi chờ một chút."
                RobotMoveEvent.Stuck -> "Tôi đang bị kẹt, nhờ anh chị nhường lối giúp ạ."
                RobotMoveEvent.Approaching -> "Sắp tới nơi rồi ạ."
                RobotMoveEvent.Normal -> "Đường đã thông, tôi đi tiếp."
                else -> return
            }
            main.post { khiCapNhat(tin) }
        }

        override fun onPauseState(event: RobotPauseEvent) {
            Log.d(TAG, "onPauseState $event")
        }

        override fun onMoveDirectionChange(d: MoveDirection) {}

        override fun onGoalCruise() {}

        override fun onFillIn() {}

        override fun onActive(d: PdDestination, kc: Double) {
            Log.d(TAG, "onActive ${d.name} còn ${"%.1f".format(kc)} m")
        }

        override fun onCancel(destination: List<PdDestination>) {
            Log.d(TAG, "onCancel $destination")
            dangDanDuong = false
        }

        override fun onError(errorList: List<PdMoveError>) {
            dangDanDuong = false
            val ma = errorList.firstOrNull()?.error_code ?: 0
            Log.w(TAG, "Lỗi dẫn đường: " + errorList.joinToString { "${it.error_code}/${it.detail}" })
            main.post { khiLoi(dichMaLoi(ma)) }
        }

        override fun onElevatorStateUpdate(
            s: com.pudutech.open_sdk_core.api.elevator.ElevatorTaskState
        ) {
        }

        override fun otaPileState(
            s: com.pudutech.open_sdk_core.api.navigation.PileOtaState, m: String
        ) {
        }

        override fun onAutoTrackStatusUpdate(
            e: com.pudutech.open_sdk_core.api.navigation.RobotAutoTrackEvent
        ) {
        }
    }

    fun dungDieuHuong() {
        dangDanDuong = false
        val t = viec ?: return
        viec = null
        runCatching { t.stop() }.onFailure { Log.w(TAG, "stop() lỗi: ${it.message}") }
    }

    /** Dịch mã lỗi của hãng thành câu người thường đọc được. */
    private fun dichMaLoi(ma: Int): String = when (ma) {
        NavError.ERROR_LOST_LOCATION ->
            "Tôi bị mất định vị trên bản đồ. Nhờ cán bộ kỹ thuật định vị lại giúp ạ."

        NavError.ERROR_DEST_NOT_EXITS ->
            "Chưa có điểm này trên bản đồ. Nhờ cán bộ kỹ thuật đặt điểm giúp ạ."

        NavError.ERROR_PLAN, NavError.ERROR_REPLAN ->
            "Tôi không tìm được đường tới chỗ đó, có thể lối đi đang bị chắn. " +
                    "Mời anh chị đi theo biển chỉ dẫn giúp tôi ạ."

        NavError.ERROR_HARDWARE, NavError.ERROR_WHEEL_EXCEPTION ->
            "Robot đang có trục trặc kỹ thuật. Nhờ anh chị báo cán bộ giúp ạ."

        else -> "Có trục trặc khi dẫn đường (mã $ma). Mời anh chị chờ nhân viên hỗ trợ."
    }

    private fun chuanHoa(s: String) = s.trim().replace(Regex("\\s+"), " ").lowercase()

    // ───────────────────────── Sẵn sàng chưa ─────────────────────────

    /**
     * Vì sao chưa dẫn đường được — trả null nghĩa là sẵn sàng.
     * Viết bằng tiếng người thường vì câu này ĐỌC THẲNG cho dân nghe.
     */
    fun lyDoChuaSanSang(): String? = when {
        !daKetNoi ->
            "Tôi chưa kết nối được với hệ thống của robot. Nhờ anh chị báo cán bộ kỹ thuật giúp ạ."

        runCatching { PdRobotSdk.navigationManager()?.isLocationSuccess() != true }
            .getOrDefault(true) ->
            "Tôi chưa định vị được trên bản đồ nên chưa tự đi được. " +
                    "Nhờ cán bộ kỹ thuật định vị lại giúp ạ."

        else -> null
    }
}
