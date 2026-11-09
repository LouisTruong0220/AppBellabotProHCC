package vn.roboworld.hcc

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity

/**
 * Màn hình duy nhất của app: một WebView chạy toàn bộ giao diện tra cứu.
 *
 * Giao diện nằm ở assets/index.html — CÙNG MỘT FILE với bản chạy trên OrionStar Nova.
 * Sửa nội dung hay bố cục về sau chỉ cần chạy lại dung-app.py bên thư mục Nova rồi
 * chép index.html sang, không phải build lại app.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        web = WebView(this)
        // Nút thoát nổi lên trên WebView — xem NutThoat về việc vì sao phải BẤM GIỮ.
        setContentView(NutThoat.bocQuanh(this, web))
        anThanhHeThong()

        WebView.setWebContentsDebuggingEnabled(true)   // để soi lỗi bằng chrome://inspect

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_NO_CACHE
            allowFileAccess = true
            textZoom = 100
            mediaPlaybackRequiresUserGesture = false

            /*
             * ⚠ ĐÂY LÀ CHỖ MÀN HÌNH PUDU KHÁC HẲN MÀN HÌNH NOVA — đừng bê nguyên
             *   lời giải của bản Nova sang.
             *
             *   Nova : 1920×1080 px ở 560 dpi → WebView chỉ có 548×309 CSS px.
             *          Cỡ chữ gốc rơi xuống 4,56 px, mà Chrome có SÀN 8 px nên nó tự
             *          nâng lên, mọi thứ to lên 1,75 lần và chữ tràn ra ngoài thẻ.
             *   PUDU : 1280×800 px ở 160 dpi → WebView có TRỌN 1280×800 CSS px.
             *          Không có chuyện chạm sàn cỡ chữ, không cần chữa gì.
             *
             * Vẫn bật useWideViewPort + loadWithOverviewMode vì index.html khai
             * meta viewport width=1920: trang bố cục ở 1920 px rồi thu nhỏ vừa 1280,
             * đúng tỉ lệ thiết kế gốc. Hai dòng minimumFontSize giữ lại làm lớp chặn.
             */
            useWideViewPort = true
            loadWithOverviewMode = true
            minimumFontSize = 1
            minimumLogicalFontSize = 1
        }

        // In lỗi JavaScript ra logcat — WebView trên robot có thể cũ hơn trên máy tính,
        // cú pháp JS đời mới sẽ lỗi ở đây chứ không lỗi lúc build.
        web.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                Log.d("HCCWeb", "${m.messageLevel()} ${m.message()} @${m.lineNumber()}")
                return true
            }
        }

        /*
         * Giấu hai nút cần MICRO ngay khi trang dựng xong.
         *
         * index.html là CÙNG MỘT FILE với bản chạy trên Nova — bên đó micro chạy được nên
         * hai nút này đúng. Trên PUDU tầng thoại đang khoá (xem MainApplication), bấm vào
         * sẽ không có gì xảy ra. Ở quầy hành chính công đó là lỗi tệ nhất: người dân bấm,
         * không thấy phản hồi, rồi nghĩ mình làm sai.
         *
         * Giấu bằng JS chèn từ đây chứ KHÔNG sửa index.html: giữ đúng một bản HTML cho cả
         * hai loại máy. Ngày hãng mở khoá micro thì xoá khối này là xong.
         */
        web.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageFinished(v: WebView?, url: String?) {
                v?.evaluateJavascript(GIAU_NUT_MICRO, null)
            }
        }

        Cau.gan(web)
        web.addJavascriptInterface(Cau, "CAU")
        web.loadUrl("file:///android_asset/index.html")

        Log.d("HCCWeb", "Android ${Build.VERSION.SDK_INT} · WebView " +
                (WebView.getCurrentWebViewPackage()?.versionName ?: "không đọc được"))
    }

    /** Ra khỏi tiền cảnh thì thôi đọc, khỏi vọng tiếng ra phòng chờ. */
    override fun onPause() {
        super.onPause()
        RobotHelper.dungDoc()
    }

    /** Chạy toàn màn hình — robot không có thanh trạng thái để người dân bấm nhầm. */
    @Suppress("DEPRECATION")
    private fun anThanhHeThong() {
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) anThanhHeThong()
    }

    /**
     * Nút Back: lùi trong trang trước, hết mới thoát app.
     * Lớp web tự quyết định lùi về đâu (window.luiHeThong) — chỉ khi nó đang ở màn chờ
     * biểu cảm mới trả 0 cho phép thoát ra màn hình chính của robot.
     */
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        web.evaluateJavascript("window.luiHeThong ? window.luiHeThong() : '0'") { kq ->
            if (kq.contains("0")) super.onBackPressed()
        }
    }

    override fun onDestroy() {
        Cau.go()
        RobotHelper.dungDoc()
        web.destroy()
        super.onDestroy()
    }

    companion object {
        /**
         * Giấu nút micro cạnh ô tìm kiếm và nút "Trò chuyện với Robot".
         * Chạy lại sau mỗi lần đổi màn vì giao diện dựng lại thẻ — dùng MutationObserver
         * thay vì gọi một lần, nếu không nút quay lại sau vài thao tác.
         */
        private const val GIAU_NUT_MICRO = """
            (function(){
              function giau(){
                var m = document.getElementById('mic1');
                if (m) m.style.display = 'none';
                document.querySelectorAll('.nut-ai').forEach(function(n){ n.style.display='none'; });
              }
              giau();
              new MutationObserver(giau).observe(document.body, {childList:true, subtree:true});
            })();
        """
    }
}
