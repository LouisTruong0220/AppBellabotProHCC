package vn.roboworld.hcc

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Nút thoát khỏi app để về Cài đặt robot / màn hình chính PuduOS.
 *
 * VÌ SAO PHẢI CÓ: app chạy toàn màn hình, không có thanh điều hướng Android, và mô-đun
 * tự khởi động thì robot vào thẳng đây. Không có nút này thì cán bộ kỹ thuật bị kẹt
 * trong app — muốn đổi mô-đun, xem bản đồ hay sửa Wi-Fi đều phải cắm cáp gõ adb.
 *
 * VÌ SAO PHẢI BẤM GIỮ chứ không bấm một cái:
 * đây là robot đặt ở quầy hành chính công, người dân tự bấm. Một nút "Thoát" bấm phát
 * ra ngay là kiểu gì cũng có người tò mò bấm, rồi robot nằm ở màn hình cài đặt Android
 * cho tới khi có cán bộ đi qua. Bấm giữ 1,2 giây là thao tác không ai làm nhầm, mà cán
 * bộ thì chỉ cần biết một lần.
 *
 * Nút để mờ (alpha 0,18) ở góc trên bên trái, sáng dần lên khi đang bấm giữ để người
 * bấm biết máy đã nhận — không có phản hồi thì người ta buông tay giữa chừng.
 */
object NutThoat {

    private const val TAG = "HCCNutThoat"
    private const val GIU_MS = 1200L

    /** Bọc một view bằng khung có nút thoát nổi lên trên. */
    fun bocQuanh(hoatDong: Activity, noiDung: View): FrameLayout {
        val khung = FrameLayout(hoatDong)
        khung.addView(
            noiDung,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        val d = hoatDong.resources.displayMetrics
        fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), d).toInt()

        val nut = TextView(hoatDong).apply {
            text = "☰"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            alpha = 0.18f
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#000000"))
                setStroke(dp(1), Color.parseColor("#66FFFFFF"))
            }
            contentDescription = "Bấm giữ để thoát ra ngoài"
        }

        // Bấm một cái: chỉ nhắc, KHÔNG thoát.
        nut.setOnClickListener {
            Toast.makeText(hoatDong, "Bấm GIỮ nút này để thoát ra ngoài", Toast.LENGTH_SHORT).show()
        }
        // Bấm giữ: mới thật sự mở bảng chọn.
        nut.setOnLongClickListener {
            hienBangChon(hoatDong)
            true
        }
        // Sáng dần lên trong lúc giữ, để người bấm biết máy đã nhận.
        nut.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> v.animate().alpha(0.85f).setDuration(GIU_MS).start()
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> {
                    v.animate().cancel(); v.animate().alpha(0.18f).setDuration(200).start()
                }
            }
            false
        }

        khung.addView(
            nut,
            FrameLayout.LayoutParams(dp(44), dp(44), Gravity.TOP or Gravity.START).apply {
                leftMargin = dp(6); topMargin = dp(6)
            }
        )
        return khung
    }

    private fun hienBangChon(hoatDong: Activity) {
        val muc = arrayOf(
            "Kiểm tra bản đồ  (làm trước khi bàn giao)",
            "Cài đặt robot  (đổi mô-đun, bản đồ, Wi-Fi)",
            "Màn hình chính của robot",
            "Định vị lại trên bản đồ",
            "Danh sách ứng dụng Android"
        )
        AlertDialog.Builder(hoatDong)
            .setTitle("Bảng của cán bộ kỹ thuật")
            .setItems(muc) { _, i ->
                when (i) {
                    0 -> KiemTraBanDo.hien(hoatDong)
                    1 -> theoDuong(hoatDong, "Cài đặt robot", "SETTINGS")
                    2 -> veManChinh(hoatDong)
                    3 -> theoDuong(hoatDong, "Định vị lại", "CHECKLOCATION")
                    4 -> moDanhSachApp(hoatDong)
                }
            }
            .setNegativeButton("Ở lại", null)
            .show()
    }

    /**
     * Đi qua "bộ định tuyến" của PUDU thay vì gọi thẳng tên lớp.
     *
     * ⚠ ĐỪNG quay lại cách gọi thẳng ComponentName. Đã thử trên máy thật 18/08/2026:
     *   com.pudutech.function.setting.ui.SettingActivity KHÔNG exported, app bên thứ ba
     *   gọi vào là SecurityException "not exported from uid 10084".
     *
     * Hãng mở sẵn bốn cửa qua action com.pudutech.action.router.MAIN, phân biệt bằng
     * category (đọc bằng: adb shell dumpsys package com.pudutech.business.function):
     *
     *   SETTINGS       → trang Cài đặt (đổi mô-đun tự khởi động ở đây)
     *   GOHOME         → về chỗ đứng chờ / màn hình chính
     *   CHECKLOCATION  → màn định vị lại trên bản đồ
     *   ONECHARGE      → đi sạc
     *
     * Ghim setPackage cho chắc: action router.MAIN chính app này cũng khai (để hiện ra
     * trong danh sách mô-đun tự khởi động), không ghim thì có ngày Android bật hộp
     * chọn ứng dụng ngay giữa mặt người dân.
     */
    private fun theoDuong(hoatDong: Activity, nhan: String, loai: String) =
        thu(hoatDong, nhan) {
            Intent("com.pudutech.action.router.MAIN").apply {
                addCategory("com.pudutech.category.router.$loai")
                setPackage("com.pudutech.business.function")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

    /**
     * Về màn hình chính của robot (màn chọn mô-đun: Giao hàng · Tuần tra · Hành chính công…).
     *
     * ⚠ PHẢI CÓ HAI ĐƯỜNG. Đo trên máy thật 19/08/2026:
     *
     *   Đường 1 — router `category.router.GOHOME`: hãng CHẶN khi robot chưa định vị trên
     *   bản đồ. Bắn intent đi, hệ thống nhận, mà màn hình không đổi gì cả, cũng không báo
     *   lỗi. Đây là trường hợp thường gặp nhất trong xưởng.
     *
     *   Đường 2 — gọi thẳng `com.pudutech.function.FunctionInitActivity`: CHẠY ĐƯỢC ngay
     *   cả khi robot chưa định vị. Activity này có `android.intent.action.MAIN` +
     *   `category.DEFAULT` nên app ngoài gọi vào được.
     *
     * ⚠ Vì sao cái này quan trọng đến thế: `com.pudutech.business.function` có ĐÚNG 0
     *   intent-filter mang `category.LAUNCHER` — tức nó KHÔNG có icon trong danh sách app
     *   Android. Ai lỡ thoát ra màn danh sách app là **không còn đường bấm tay nào** để về
     *   màn chính robot, phải cắm cáp gõ adb. Nút này là lối ra duy nhất.
     */
    private fun veManChinh(hoatDong: Activity) {
        val ok = runCatching {
            hoatDong.startActivity(
                Intent("com.pudutech.action.router.MAIN").apply {
                    addCategory("com.pudutech.category.router.GOHOME")
                    setPackage("com.pudutech.business.function")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }.isSuccess

        // Bắn được intent KHÔNG có nghĩa là màn hình đã đổi — hãng nuốt lệnh khi chưa định
        // vị. Nên cứ gọi tiếp đường 2 đè lên; nó tự đưa về đúng màn chính, không hại gì.
        thu(hoatDong, "Màn hình chính của robot") {
            Intent().apply {
                setClassName(
                    "com.pudutech.business.function",
                    "com.pudutech.function.FunctionInitActivity"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        Log.d(TAG, "về màn chính — router GOHOME gửi được: $ok")
    }

    private fun moDanhSachApp(hoatDong: Activity) = thu(hoatDong, "Danh sách ứng dụng") {
        Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Bắn intent, hỏng thì nói ra.
     * Tên lớp của hãng có thể đổi sau một lần nâng cấp firmware — im lặng nuốt lỗi thì
     * cán bộ bấm mãi không thấy gì và không hiểu vì sao.
     */
    private fun thu(hoatDong: Activity, nhan: String, dung: () -> Intent) {
        try {
            hoatDong.startActivity(dung())
        } catch (e: Throwable) {
            Log.w(TAG, "Không mở được $nhan: ${e.javaClass.simpleName} ${e.message}")
            Toast.makeText(
                hoatDong,
                "Không mở được $nhan. Có thể firmware đã đổi tên màn hình này.",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
