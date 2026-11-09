package vn.roboworld.hcc

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream

import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.Executors

/**
 * MÁY CHỦ WEB CHẠY NGAY TRONG ROBOT — để cán bộ 7 quầy dùng hàng chờ số thứ tự.
 *
 * Cán bộ mở trình duyệt điện thoại hoặc máy tính, vào `http://<ip-robot>:8080`,
 * chọn quầy của mình, thế là xong. Không phải cài app, không phải đăng nhập.
 *
 * Vì sao đặt máy chủ trong robot chứ không đặt trên mạng:
 *   · chạy được cả khi trung tâm mất internet — mất mạng thì AI câm nhưng hàng chờ
 *     và lời gọi vẫn phải hoạt động, đó là việc cốt lõi của một trung tâm hành chính
 *   · không tốn phí tháng, không thêm hạ tầng phải bảo trì
 *   · robot vốn đã là cái loa gọi số, đặt hàng chờ cùng chỗ thì ít mắt xích hơn
 * Đổi lại: robot tắt là hàng chờ dừng. Hàng chờ có ghi ra thẻ nhớ nên bật lại là
 * còn nguyên, nhưng phường vẫn cần một quy trình giấy cho lúc robot đi sạc.
 *
 * ⚠ Tự viết bằng ServerSocket, KHÔNG thêm thư viện. Bảy máy khách hỏi 2 giây một
 *   lần thì không cần gì to hơn, và bài học JitPack còn đó: thêm một phụ thuộc là
 *   thêm một thứ có thể hỏng theo cách mình không lường được.
 *
 * ⚠ Chỉ phục vụ trong mạng nội bộ. Không mở ra internet, không ghi file theo yêu
 *   cầu từ xa, mọi tham số đều ép kiểu số trước khi dùng.
 */
object MayChu {

    private const val TAG = "TTHCMayChu"
    const val CONG = 8080

    private var socket: ServerSocket? = null
    private val tho = Executors.newFixedThreadPool(8)
    @Volatile private var chay = false

    fun batDau(ctx: Context) {
        if (chay) return
        chay = true
        Thread({
            try {
                val s = ServerSocket(CONG)
                socket = s
                Log.d(TAG, "Máy chủ quầy đã bật: http://${diaChiIp()}:$CONG")
                while (chay) {
                    val k = runCatching { s.accept() }.getOrNull() ?: break
                    tho.execute { runCatching { xuLy(ctx, k) }.onFailure {
                        Log.w(TAG, "Lỗi khi phục vụ: ${it.message}") } }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Không bật được máy chủ cổng $CONG: ${e.message}")
            }
        }, "may-chu-quay").start()
    }

    fun dung() {
        chay = false
        runCatching { socket?.close() }
    }

    /**
     * Địa chỉ để cán bộ gõ vào trình duyệt. Hiện luôn trên màn hình chờ của robot
     * — không thì lắp đặt xong chẳng ai biết vào đường nào.
     */
    fun diaChiIp(): String {
        runCatching {
            for (gd in NetworkInterface.getNetworkInterfaces()) {
                if (gd.isLoopback || !gd.isUp) continue
                for (dc in gd.inetAddresses) {
                    if (dc.isLoopbackAddress) continue
                    val s = dc.hostAddress ?: continue
                    if (s.contains(':')) continue          // bỏ IPv6, cán bộ không gõ nổi
                    return s
                }
            }
        }
        return "chưa có mạng"
    }

    fun diaChiDayDu(): String = "http://${diaChiIp()}:$CONG"

    // ───────────────────────── Xử lý một yêu cầu ─────────────────────────

    private fun xuLy(ctx: Context, k: Socket) {
        k.soTimeout = 10_000
        k.use { s ->
            val doc = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
            val dongDau = doc.readLine() ?: return
            val phan = dongDau.split(" ")
            if (phan.size < 2) return
            val cach = phan[0]
            val duong = phan[1]

            // Đọc hết phần đầu để lấy độ dài thân yêu cầu
            var dai = 0
            while (true) {
                val d = doc.readLine() ?: break
                if (d.isEmpty()) break
                if (d.lowercase().startsWith("content-length:"))
                    dai = d.substringAfter(":").trim().toIntOrNull() ?: 0
            }
            var than = ""
            if (dai in 1..8192) {
                val buf = CharArray(dai)
                var da = 0
                while (da < dai) {
                    val n = doc.read(buf, da, dai - da)
                    if (n < 0) break
                    da += n
                }
                than = String(buf, 0, da)
            }

            val ra = s.getOutputStream()
            val hoi = duong.substringAfter('?', "")
            val nen = duong.substringBefore('?')

            when {
                nen == "/" || nen == "/index.html" -> traAsset(ctx, ra, "quay.html", "text/html")
                nen == "/bang-so"                  -> traAsset(ctx, ra, "bang-so.html", "text/html")

                nen == "/api/hang-cho" -> {
                    val q = soTuThamSo(hoi, "quay")
                    traChu(ra, 200, "application/json",
                           if (q > 0) HangCho.jsonTheoQuay(q) else HangCho.jsonBangSo())
                }
                nen == "/api/bang-so" -> traChu(ra, 200, "application/json", HangCho.jsonBangSo())

                cach == "POST" && nen == "/api/xu-ly" ->
                    traKq(ra, HangCho.batDauXuLy(soTuThamSo(than, "so")))
                cach == "POST" && nen == "/api/xong" ->
                    traKq(ra, HangCho.ketThuc(soTuThamSo(than, "so"), false))
                cach == "POST" && nen == "/api/bo-qua" ->
                    traKq(ra, HangCho.ketThuc(soTuThamSo(than, "so"), true))
                cach == "POST" && nen == "/api/hoan-tac" ->
                    traKq(ra, HangCho.hoanTac(soTuThamSo(than, "so")))

                else -> traChu(ra, 404, "text/plain", "Khong co trang nay")
            }
        }
    }

    /** Chỉ nhận số — không có đường nào để nhét chuỗi lạ vào hệ thống. */
    private fun soTuThamSo(chuoi: String, ten: String): Int {
        for (c in chuoi.split('&')) {
            val i = c.indexOf('=')
            if (i <= 0) continue
            if (c.substring(0, i) != ten) continue
            return runCatching { URLDecoder.decode(c.substring(i + 1), "UTF-8").trim().toInt() }
                .getOrDefault(0)
        }
        return 0
    }

    private fun traAsset(ctx: Context, ra: OutputStream, ten: String, kieu: String) {
        val noi = runCatching { ctx.assets.open(ten).bufferedReader().use { it.readText() } }
            .getOrElse { "Thieu file $ten trong assets" }
        traChu(ra, 200, kieu, noi)
    }

    private fun traKq(ra: OutputStream, ok: Boolean) =
        traChu(ra, if (ok) 200 else 409, "application/json", """{"ok":$ok}""")

    private fun traChu(ra: OutputStream, ma: Int, kieu: String, noi: String) {
        val than = noi.toByteArray(Charsets.UTF_8)
        val dau = buildString {
            append("HTTP/1.1 ").append(ma).append(if (ma == 200) " OK" else " ERR").append("\r\n")
            append("Content-Type: ").append(kieu).append("; charset=utf-8\r\n")
            append("Content-Length: ").append(than.size).append("\r\n")
            // Trang quầy tự hỏi lại 2 giây một lần — cấm nhớ đệm, không thì cán bộ
            // nhìn mãi một danh sách cũ mà tưởng chưa ai lấy số.
            append("Cache-Control: no-store\r\n")
            append("Connection: close\r\n\r\n")
        }
        ra.write(dau.toByteArray(Charsets.UTF_8))
        ra.write(than)
        ra.flush()
    }
}
