package vn.roboworld.hcc

import android.app.Activity
import android.app.AlertDialog
import android.widget.Toast

/**
 * Màn tự kiểm bản đồ — làm TRƯỚC KHI BÀN GIAO, ngay tại chỗ.
 *
 * Vì sao cần: đặt điểm xong nhìn danh sách trên máy thì thấy đủ, nhưng thừa một dấu
 * cách hay lỡ gõ dấu tiếng Việt (`Quầy 3` thay vì `Quay 3`) là mắt thường không thấy.
 * Tới lúc có người dân đứng trước mặt bấm nút mới lòi ra, và lúc đó thì cán bộ kỹ thuật
 * đã về rồi.
 *
 * Màn này hỏi thẳng robot chứ không đọc lại cấu hình của app, và dò bằng ĐÚNG phép so
 * tên mà lệnh dẫn đường dùng (RobotHelper.quayCoSan). Nên dấu ✔ ở đây là lời hứa thật:
 * quầy nào ✔ thì bấm nút dẫn đường chắc chắn tìm thấy điểm.
 */
object KiemTraBanDo {

    /** Số quầy mà bản triển khai Mông Dương cần. Nơi khác nhiều/ít hơn thì sửa ở đây. */
    private const val SO_QUAY_CAN = 7

    fun hien(hoatDong: Activity) {
        Toast.makeText(hoatDong, "Đang hỏi robot…", Toast.LENGTH_SHORT).show()

        RobotHelper.danhSachDiem { diem ->
            RobotHelper.moTaDiem { chiTiet ->
                RobotHelper.daDinhVi { daDinhVi ->
                    if (hoatDong.isFinishing) return@daDinhVi
                    AlertDialog.Builder(hoatDong)
                        .setTitle("Kiểm tra bản đồ")
                        .setMessage(dungBaoCao(diem, daDinhVi))
                        .setPositiveButton("Xong", null)
                        .setNeutralButton("Chi tiết") { _, _ ->
                            AlertDialog.Builder(hoatDong)
                                .setTitle("Mọi trường của từng điểm")
                                .setMessage(chiTiet.joinToString("\n\n"))
                                .setPositiveButton("Xong", null)
                                .show()
                        }
                        .show()
                }
            }
        }
    }

    private fun dungBaoCao(diem: List<String>, daDinhVi: Boolean): String {
        val quay = RobotHelper.quayCoSan(diem)
        val thieu = (1..SO_QUAY_CAN).filterNot { it in quay }
        val thua = quay.filter { it > SO_QUAY_CAN }

        return buildString {
            append("Bản đồ đang dùng: ${RobotHelper.tenBanDo().ifBlank { "(chưa đọc được)" }}\n")
            append(if (daDinhVi) "Định vị: ✔ đã định vị xong\n"
                   else "Định vị: ✘ CHƯA ĐỊNH VỊ — mọi lệnh dẫn đường sẽ bị từ chối\n")
            append("Tổng số điểm trên bản đồ: ${diem.size}\n\n")

            append("QUẦY\n")
            for (n in 1..SO_QUAY_CAN) {
                append(if (n in quay) "  ✔ Quay $n\n" else "  ✘ Quay $n  — CHƯA ĐẶT\n")
            }
            if (thua.isNotEmpty()) {
                append("  (bản đồ còn có thêm: ${thua.joinToString(", ") { "Quay $it" }})\n")
            }

            append("\nĐIỂM NÊN CÓ\n")
            append(if (RobotHelper.coDiem("Le tan", diem)) "  ✔ Le tan\n"
                   else "  ✘ Le tan — robot sẽ gọi số tại chỗ, không ra sảnh\n")
            append(if (RobotHelper.coDiem("Tram sac", diem)) "  ✔ Tram sac\n"
                   else "  ✘ Tram sac — robot không tự về sạc được\n")

            append("\nTẤT CẢ ĐIỂM ROBOT ĐỌC ĐƯỢC\n")
            append(if (diem.isEmpty()) "  (không có điểm nào)\n"
                   else diem.joinToString("\n") { "  · $it" } + "\n")

            append("\n")
            when {
                !daDinhVi ->
                    append("→ Bấm ☰ → \"Định vị lại trên bản đồ\", đưa robot về đúng " +
                           "điểm dừng rồi định vị, xong quay lại kiểm tiếp.")
                thieu.isEmpty() ->
                    append("→ Đủ ${SO_QUAY_CAN} quầy. Bấm thử nút dẫn đường ở một thủ tục " +
                           "để chạy thật một lượt trước khi bàn giao.")
                else ->
                    append("→ CÒN THIẾU: ${thieu.joinToString(", ") { "Quay $it" }}.\n" +
                           "   Nếu trên máy nhìn thấy điểm đó rồi mà đây vẫn báo thiếu thì " +
                           "tên bị sai — hay gặp nhất là gõ dấu (Quầy 3) hoặc thừa khoảng trắng. " +
                           "So với danh sách bên trên.")
            }
        }
    }
}
