package vn.roboworld.hcc

import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Đọc mức "Âm lượng giọng nói" mà cán bộ đặt trong Cài đặt của robot, rồi áp lên
 * tiếng do app phát ra.
 *
 * VÌ SAO PHẢI LÀM THỦ CÔNG THẾ NÀY:
 * hai thanh trượt trong Cài đặt → Chung của PUDU **không phải** luồng âm thanh của
 * Android. Đo trên máy thật 18/08/2026: kéo "Âm lượng giọng nói" từ 4% lên 70% mà
 * `dumpsys audio` không đổi một luồng nào (`STREAM_MUSIC` vẫn 15/15, `STREAM_VOICE_CALL`
 * vẫn 3/5). Hãng tự nhân hệ số trong player của họ. PdCoreSDK cũng không có hàm đọc —
 * cả bộ 911 phương thức chỉ có `IClientListener.onVolumeChanged(int)` là callback,
 * không có getter.
 *
 * Nên chỉ còn cách đọc thẳng chỗ hãng cất giá trị:
 *
 *     /sdcard/pudu/appConfig/Common.preferences_pb        (Jetpack DataStore Preferences)
 *     khoá "VolumeConfig_CurrentVoice"  →  số thực 0…100
 *
 * Cấu trúc sau tên khoá, đã soi bằng tay:
 *
 *     12 05        field 2 (Value), dài 5 byte
 *     15           field 2, wire type 5 (fixed32) = float
 *     00 00 8c 42  float little-endian = 70.0
 *
 * Không dùng thư viện protobuf để bóc: chỉ cần đúng một khoá, mà kéo cả bộ sinh mã
 * protobuf vào cho một số thực thì nặng hơn phần việc.
 *
 * ⚠ CẦN QUYỀN ĐỌC THẺ NHỚ. Android 11 trở lên chặn app đọc file lạ ngoài thư mục
 *   riêng. Cấp một lần cho mỗi robot, bằng adb:
 *
 *     adb shell appops set com.pudutech.business.hcc MANAGE_EXTERNAL_STORAGE allow
 *
 *   Không cấp thì app vẫn chạy, chỉ là dùng mức mặc định — xem MUC_MAC_DINH.
 */
object AmLuong {

    private const val TAG = "HCCAmLuong"
    private const val TEP = "/sdcard/pudu/appConfig/Common.preferences_pb"
    private const val KHOA = "VolumeConfig_CurrentVoice"

    /**
     * Dùng khi không đọc được cấu hình của hãng.
     *
     * 70 chứ không phải 100: máy giao tới tay khách thường để rất nhỏ (máy này lúc nhận
     * là 4%), mà đặt 100 thì lần đầu bật lên robot hét giữa sảnh. 70 là mức nghe rõ ở
     * khoảng cách một quầy mà không giật mình.
     */
    private const val MUC_MAC_DINH = 70f

    /** Sàn: dưới mức này coi như cán bộ muốn tắt tiếng, nhưng vẫn để nghe được lí nhí. */
    private const val SAN = 0.05f

    @Volatile private var demCuoi = 0L        // mtime của lần đọc gần nhất
    @Volatile private var mucCuoi = MUC_MAC_DINH
    @Volatile private var daBaoThieuQuyen = false

    /**
     * Hệ số nhân cho MediaPlayer.setVolume — 0…1.
     *
     * Đọc lại file mỗi khi nó đổi (so theo mtime), nên cán bộ kéo thanh trượt xong là
     * câu tiếp theo đã đúng mức mới, không phải khởi động lại app.
     */
    fun heSo(): Float {
        val muc = docMuc()
        return (muc / 100f).coerceIn(SAN, 1f)
    }

    /** Mức 0…100 như hãng ghi. Trả mức mặc định nếu không đọc được. */
    fun docMuc(): Float {
        val f = File(TEP)
        val m = runCatching { if (f.canRead()) f.lastModified() else 0L }.getOrDefault(0L)
        if (m == 0L) {
            if (!daBaoThieuQuyen) {
                daBaoThieuQuyen = true
                Log.w(TAG, "Không đọc được $TEP — dùng mức mặc định $MUC_MAC_DINH. " +
                        "Cấp quyền: adb shell appops set ${MainApplication.boiCanh?.packageName} " +
                        "MANAGE_EXTERNAL_STORAGE allow")
            }
            return mucCuoi
        }
        if (m == demCuoi) return mucCuoi

        val v = runCatching { bocMuc(f.readBytes()) }.getOrNull()
        demCuoi = m
        if (v != null && v in 0f..100f) {
            if (v != mucCuoi) Log.d(TAG, "Âm lượng giọng nói của robot: $v")
            mucCuoi = v
        } else {
            Log.w(TAG, "Đọc được file nhưng không thấy khoá $KHOA — giữ mức $mucCuoi")
        }
        return mucCuoi
    }

    /**
     * Tìm khoá trong file rồi đọc số thực ngay sau nó.
     * Trả null nếu không thấy đúng khuôn `12 05 15 <4 byte>`.
     */
    private fun bocMuc(d: ByteArray): Float? {
        val k = KHOA.toByteArray(Charsets.UTF_8)
        var i = timChuoi(d, k)
        if (i < 0) return null
        i += k.size
        // 12 05 15 <float LE>
        if (i + 7 > d.size) return null
        if (d[i] != 0x12.toByte() || d[i + 1] != 0x05.toByte() || d[i + 2] != 0x15.toByte()) {
            Log.w(TAG, "Khuôn byte sau khoá đã đổi: ${d.copyOfRange(i, minOf(i + 8, d.size))
                .joinToString("") { "%02x".format(it) }}")
            return null
        }
        return ByteBuffer.wrap(d, i + 3, 4).order(ByteOrder.LITTLE_ENDIAN).float
    }

    private fun timChuoi(d: ByteArray, k: ByteArray): Int {
        outer@ for (i in 0..d.size - k.size) {
            for (j in k.indices) if (d[i + j] != k[j]) continue@outer
            return i
        }
        return -1
    }
}
