package com.sansale.shopee

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.random.Random

class SanSaleService : AccessibilityService() {
    private var windowManager: WindowManager? = null
    private var floatView: LinearLayout? = null
    private var mainPanel: LinearLayout? = null
    private var miniBtn: TextView? = null
    private var tvLiveClock: TextView? = null

    // 2 Tâm ngắm hiển thị vị trí tọa độ
    private var coinMarker: TextView? = null
    private var orderMarker: TextView? = null
    private var markersVisible = false
    private val markerSize = 90

    @Volatile private var timeOffset: Long = 0
    @Volatile private var isRunning = false
    private var isMinimized = false
    
    // false = Chế độ X:59:59.980 | true = Chế độ X:00:00.000
    private var isZeroMode = false 

    private val mainHandler = Handler(Looper.getMainLooper())
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private val miniTimeFormat = SimpleDateFormat("HH:mm:ss.S", Locale.getDefault())

    // Tọa độ mặc định của bạn
    private var coinX = 1140f
    private var coinY = 895f
    private var orderX = 950f
    private var orderY = 2350f

    private var tvCoordInfo: TextView? = null

    private val clockRunnable = object : Runnable {
        override fun run() {
            val nowShopee = System.currentTimeMillis() + timeOffset
            val dateObj = Date(nowShopee)
            if (!isMinimized) {
                tvLiveClock?.text = "⏱ " + timeFormat.format(dateObj)
            } else {
                miniBtn?.text = "⚡ " + miniTimeFormat.format(dateObj)
            }
            mainHandler.postDelayed(this, 33)
        }
    }

    private val showReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            mainHandler.post {
                if (floatView == null) {
                    showFloatingWindow()
                } else {
                    isMinimized = false
                    mainPanel?.visibility = View.VISIBLE
                    miniBtn?.visibility = View.GONE
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val filter = IntentFilter("com.sansale.shopee.SHOW_PANEL")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(showReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(showReceiver, filter)
        }
        showFloatingWindow()
        mainHandler.post(clockRunnable)
    }

    private fun showFloatingWindow() {
        if (floatView != null) return
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 70
            y = 150
        }

        floatView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // 1. Nút thu gọn (Có hiện đồng hồ nhảy giây trực tiếp)
        miniBtn = TextView(this).apply {
            text = "⚡ 00:00:00.0"
            setTextColor(Color.YELLOW)
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setPadding(26, 16, 26, 16)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#EE1B1B1B"))
                cornerRadius = 40f
                setStroke(3, Color.parseColor("#00E676"))
            }
            visibility = View.GONE
        }

        // 2. Bảng điều khiển chính
        mainPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#EA121212"))
                cornerRadius = 26f
                setStroke(2, Color.parseColor("#55FFFFFF"))
            }
            setPadding(24, 18, 24, 18)
        }

        // MÀN HÌNH ĐỒNG HỒ LED TRỰC QUAN
        tvLiveClock = TextView(this).apply {
            text = "⏱ 00:00:00.000"
            setTextColor(Color.parseColor("#00FF66"))
            textSize = 18.5f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(14, 8, 14, 8)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FF000000"))
                cornerRadius = 14f
                setStroke(2, Color.parseColor("#00E676"))
            }
        }

        val tvStatus = TextView(this).apply {
            text = "Chế độ: X:59:59.980 (Giữ đồng hồ để kéo)"
            setTextColor(Color.parseColor("#FFD54F"))
            textSize = 11f
            setPadding(0, 6, 0, 2)
        }

        tvCoordInfo = TextView(this).apply {
            text = "Xu:(${coinX.toInt()},${coinY.toInt()}) | Đặt:(${orderX.toInt()},${orderY.toInt()})"
            setTextColor(Color.LTGRAY)
            textSize = 10.5f
            setPadding(0, 2, 0, 4)
        }

        // Hàng chỉnh giờ + Nút chuyển đổi 2 chế độ (59:59 <-> 00:00)
        val rowTime = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val btnToggleMode = Button(this).apply {
            text = "⚡ 59:59"
            textSize = 10f
        }

        val lblHour = TextView(this).apply {
            text = " X:"
            setTextColor(Color.WHITE)
            textSize = 12f
        }

        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val edtHour = EditText(this).apply {
            setText(currentHour.toString())
            setTextColor(Color.YELLOW)
            textSize = 13f
            inputType = InputType.TYPE_CLASS_NUMBER
            width = 85
            gravity = Gravity.CENTER
        }

        val lblMinSec = TextView(this).apply {
            text = ":59:59."
            setTextColor(Color.WHITE)
            textSize = 12f
        }

        val edtMs = EditText(this).apply {
            setText("980")
            setTextColor(Color.YELLOW)
            textSize = 13f
            inputType = InputType.TYPE_CLASS_NUMBER
            width = 110
            gravity = Gravity.CENTER
        }

        rowTime.addView(btnToggleMode)
        rowTime.addView(lblHour)
        rowTime.addView(edtHour)
        rowTime.addView(lblMinSec)
        rowTime.addView(edtMs)

        // Hàng nút chức năng 1: Giờ Shopee | Giờ Máy | Tâm ngắm
        val rowBtn1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val btnSyncShopee = Button(this).apply {
            text = "Giờ Shopee"
            textSize = 10f
        }

        val btnLocalTime = Button(this).apply {
            text = "Giờ Máy"
            textSize = 10f
        }

        val btnMarker = Button(this).apply {
            text = "Tâm: Tắt"
            textSize = 10f
        }

        rowBtn1.addView(btnSyncShopee)
        rowBtn1.addView(btnLocalTime)
        rowBtn1.addView(btnMarker)

        // Hàng nút chức năng 2: Chạy | Thu gọn | Tắt hẳn App
        val rowBtn2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val btnStart = Button(this).apply {
            text = "▶ CHẠY"
            textSize = 11f
        }

        val btnMinimize = Button(this).apply {
            text = "Thu gọn"
            textSize = 10f
        }

        val btnExit = Button(this).apply {
            text = "✖ Tắt App"
            textSize = 10f
            setTextColor(Color.parseColor("#FF5252"))
        }

        rowBtn2.addView(btnStart)
        rowBtn2.addView(btnMinimize)
        rowBtn2.addView(btnExit)

        mainPanel?.addView(tvLiveClock)
        mainPanel?.addView(tvStatus)
        mainPanel?.addView(tvCoordInfo)
        mainPanel?.addView(rowTime)
        mainPanel?.addView(rowBtn1)
        mainPanel?.addView(rowBtn2)

        floatView?.addView(miniBtn)
        floatView?.addView(mainPanel)

        floatView?.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                windowManager?.updateViewLayout(floatView, params)
            }
            false
        }

        val enableKeyboard = {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            windowManager?.updateViewLayout(floatView, params)
        }
        edtHour.setOnClickListener { enableKeyboard() }
        edtMs.setOnClickListener { enableKeyboard() }

        setupDrag(tvLiveClock!!, params, floatView!!, null)
        setupDrag(tvStatus, params, floatView!!, null)

        setupDrag(miniBtn!!, params, floatView!!) {
            isMinimized = false
            miniBtn?.visibility = View.GONE
            mainPanel?.visibility = View.VISIBLE
        }

        // Hàm tự động cập nhật giờ X theo chế độ đang chọn (59:59 hay 00:00)
        val updateDefaultHourAndMode = {
            val cal = Calendar.getInstance().apply { timeInMillis = System.currentTimeMillis() + timeOffset }
            val h = cal.get(Calendar.HOUR_OF_DAY)
            if (isZeroMode) {
                btnToggleMode.text = "⚡ 00:00"
                lblMinSec.text = ":00:00."
                edtMs.setText("000")
                edtHour.setText(((h + 1) % 24).toString()) // Tự động gợi ý giờ kế tiếp (VD: 23h -> 0h)
            } else {
                btnToggleMode.text = "⚡ 59:59"
                lblMinSec.text = ":59:59."
                edtMs.setText("980")
                edtHour.setText(h.toString())
            }
        }

        // SỰ KIỆN CHUYỂN ĐỔI XEN KẼ 2 OPTION: X:59:59.980 <-> X:00:00.000
        btnToggleMode.setOnClickListener {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            windowManager?.updateViewLayout(floatView, params)
            isZeroMode = !isZeroMode
            updateDefaultHourAndMode()
            val modeDesc = if (isZeroMode) "X:00:00.000 (Đúng 0h)" else "X:59:59.980 (Trước 0h)"
            tvStatus.text = "Đã chọn: $modeDesc"
        }

        // NÚT ĐỒNG BỘ GIỜ SHOPEE ẨN DANH
        btnSyncShopee.setOnClickListener {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            windowManager?.updateViewLayout(floatView, params)
            tvStatus.text = "Đang lấy giờ Shopee (Ẩn danh)..."

            thread {
                var synced = false
                var rtt = 0L
                var sourceName = ""

                val apiUrls = listOf(
                    "https://shopee.vn/api/v4/client/timestamp",
                    "https://mall.shopee.vn/api/v4/client/timestamp"
                )
                for (apiUrl in apiUrls) {
                    try {
                        val t1 = System.currentTimeMillis()
                        val conn = URL(apiUrl).openConnection() as HttpURLConnection
                        conn.requestMethod = "GET"
                        conn.useCaches = false
                        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 15; OPPO Find X9 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36")
                        conn.setRequestProperty("Accept", "application/json, text/plain, */*")
                        conn.setRequestProperty("Accept-Language", "vi-VN,vi;q=0.9")
                        conn.connectTimeout = 2500
                        conn.readTimeout = 2500

                        if (conn.responseCode == 200) {
                            val res = conn.inputStream.bufferedReader().readText()
                            val t2 = System.currentTimeMillis()
                            rtt = (t2 - t1) / 2
                            val dataObj = JSONObject(res).optJSONObject("data")
                            if (dataObj != null && dataObj.has("timestamp")) {
                                val rawTs = dataObj.getLong("timestamp")
                                val serverTs = if (rawTs < 10000000000L) rawTs * 1000L + 500L else rawTs
                                timeOffset = (serverTs + rtt) - t2
                                sourceName = "Shopee API"
                                synced = true
                            }
                        }
                        conn.disconnect()
                        if (synced) break
                    } catch (_: Exception) {}
                }

                if (!synced) {
                    try {
                        val t1 = System.currentTimeMillis()
                        val conn = URL("https://shopee.vn").openConnection() as HttpURLConnection
                        conn.requestMethod = "HEAD"
                        conn.useCaches = false
                        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 15) Chrome/131.0.0.0 Mobile")
                        conn.connectTimeout = 2500
                        conn.readTimeout = 2500
                        conn.connect()
                        val dateHeader = conn.date
                        val t2 = System.currentTimeMillis()
                        conn.disconnect()

                        if (dateHeader > 0) {
                            rtt = (t2 - t1) / 2
                            val estimatedServerTs = dateHeader + 500L
                            val rawDiff = (estimatedServerTs + rtt) - t2
                            timeOffset = if (kotlin.math.abs(rawDiff) < 1200) 0L else rawDiff
                            sourceName = "Shopee Server"
                            synced = true
                        }
                    } catch (_: Exception) {}
                }

                if (!synced) {
                    try {
                        val t1 = System.currentTimeMillis()
                        val conn = URL("https://1.1.1.1/cdn-cgi/trace").openConnection() as HttpURLConnection
                        conn.connectTimeout = 2000
                        conn.readTimeout = 2000
                        val text = conn.inputStream.bufferedReader().readText()
                        val t2 = System.currentTimeMillis()
                        conn.disconnect()
                        val tsLine = text.lines().find { it.startsWith("ts=") }
                        if (tsLine != null) {
                            val tsDouble = tsLine.removePrefix("ts=").toDouble()
                            val serverTs = (tsDouble * 1000.0).toLong()
                            rtt = (t2 - t1) / 2
                            timeOffset = (serverTs + rtt) - t2
                            sourceName = "Giờ VN Chuẩn"
                            synced = true
                        }
                    } catch (_: Exception) {}
                }

                mainHandler.post {
                    updateDefaultHourAndMode()
                    if (synced) {
                        tvStatus.text = "✓ $sourceName | Lệch: ${timeOffset}ms \vert{} Ping:${rtt * 2}ms"
                    } else {
                        timeOffset = 0
                        tvStatus.text = "Đang dùng Giờ Máy (Lệch: 0ms)"
                    }
                }
            }
        }

        // Nút Dùng Giờ Điện Thoại
        btnLocalTime.setOnClickListener {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            windowManager?.updateViewLayout(floatView, params)
            timeOffset = 0
            updateDefaultHourAndMode()
            tvStatus.text = "✓ Đang dùng Giờ Máy (Offline 100%)"
        }

        // Nút Bật/Tắt Tâm ngắm tọa độ
        btnMarker.setOnClickListener {
            toggleMarkers()
            btnMarker.text = if (markersVisible) "Tâm: Bật" else "Tâm: Tắt"
        }

        // Nút Thu gọn thành đồng hồ mini
        btnMinimize.setOnClickListener {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            windowManager?.updateViewLayout(floatView, params)
            isMinimized = true
            mainPanel?.visibility = View.GONE
            miniBtn?.visibility = View.VISIBLE
        }

        // NÚT TẮT HẲN ỨNG DỤNG (KẾT THÚC HOÀN TOÀN)
        btnExit.setOnClickListener {
            shutdownApp()
        }

        // Nút Bắt đầu chạy hẹn giờ (Hỗ trợ cả 2 chế độ X:59:59.980 và X:00:00.000)
        btnStart.setOnClickListener {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            windowManager?.updateViewLayout(floatView, params)

            if (isRunning) {
                isRunning = false
                btnStart.text = "▶ CHẠY"
                tvStatus.text = "Đã hủy hẹn giờ"
                return@setOnClickListener
            }

            if (markersVisible) {
                toggleMarkers()
                btnMarker.text = "Tâm: Tắt"
            }

            val targetHour = (edtHour.text.toString().toIntOrNull() ?: 0).coerceIn(0, 23)
            val targetMs = (edtMs.text.toString().toIntOrNull() ?: 0).coerceIn(0, 999)
            val targetMinSec = if (isZeroMode) 0 else 59
            val minSecLabel = if (isZeroMode) "00:00" else "59:59"
            val msFormatted = String.format(Locale.US, "%03d", targetMs)

            isRunning = true
            btnStart.text = "⏹ HỦY"
            tvStatus.text = "Đang canh: $targetHour:$minSecLabel.$msFormatted"

            thread {
                val now = System.currentTimeMillis() + timeOffset
                val cal = Calendar.getInstance().apply {
                    timeInMillis = now
                    set(Calendar.HOUR_OF_DAY, targetHour)
                    set(Calendar.MINUTE, targetMinSec)
                    set(Calendar.SECOND, targetMinSec)
                    set(Calendar.MILLISECOND, targetMs)
                }
                var targetTime = cal.timeInMillis
                // Nếu mốc thời gian đã qua trong hôm nay thì tự động hẹn sang ngày mai
                if (targetTime <= now) {
                    targetTime += 24 * 60 * 60 * 1000L
                }

                while (isRunning) {
                    val remain = targetTime - (System.currentTimeMillis() + timeOffset)
                    if (remain <= 0) break
                    if (remain > 150) Thread.sleep(20)
                }

                if (!isRunning) return@thread

                // BƯỚC 1: Tắt Shopee Xu tại tọa độ tâm Xanh
                humanTap(coinX, coinY)

                // BƯỚC 2: Đợi 110ms cho giá cập nhật rồi bấm Đặt hàng tại tọa độ tâm Đỏ
                Thread.sleep(110)
                humanTap(orderX, orderY)
                Thread.sleep(50)
                humanTap(orderX, orderY)

                isRunning = false
                mainHandler.post {
                    btnStart.text = "▶ CHẠY"
                    tvStatus.text = "✓ Đã bắn lúc $targetHour:$minSecLabel.$msFormatted!"
                }
            }
        }

        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        windowManager?.addView(floatView, params)
    }

    // Hàm đóng hoàn toàn ứng dụng và giải phóng toàn bộ tài nguyên
    private fun shutdownApp() {
        isRunning = false
        mainHandler.removeCallbacks(clockRunnable)
        if (markersVisible) {
            toggleMarkers()
        }
        floatView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
        }
        floatView = null
        Toast.makeText(this, "Đã tắt hoàn toàn Tool Săn Sale!", Toast.LENGTH_SHORT).show()
        // Tự động ngắt Accessibility Service để kết thúc ứng dụng triệt để
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            disableSelf()
        } else {
            stopSelf()
        }
    }

    private fun toggleMarkers() {
        if (markersVisible) {
            coinMarker?.let { try { windowManager?.removeView(it) } catch (_: Exception) {} }
            orderMarker?.let { try { windowManager?.removeView(it) } catch (_: Exception) {} }
            coinMarker = null
            orderMarker = null
            markersVisible = false
        } else {
            coinMarker = createMarker("⊕ XU", Color.parseColor("#AA00E676"), coinX, coinY) { newX, newY ->
                coinX = newX
                coinY = newY
                updateCoordText()
            }
            orderMarker = createMarker("⊕ ĐẶT", Color.parseColor("#AAFF3D00"), orderX, orderY) { newX, newY ->
                orderX = newX
                orderY = newY
                updateCoordText()
            }
            markersVisible = true
        }
    }

    private fun createMarker(label: String, bgColor: Int, initX: Float, initY: Float, onMoved: (Float, Float) -> Unit): TextView {
        val params = WindowManager.LayoutParams(
            markerSize,
            markerSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (initX - markerSize / 2f).toInt()
            y = (initY - markerSize / 2f).toInt()
        }

        val tv = TextView(this).apply {
            text = label
            setTextColor(
