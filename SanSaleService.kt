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
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import kotlin.concurrent.thread
import kotlin.random.Random

class SanSaleService : AccessibilityService() {
    private var windowManager: WindowManager? = null
    private var floatView: LinearLayout? = null
    private var mainPanel: LinearLayout? = null
    private var miniBtn: TextView? = null

    // 2 Tâm ngắm hiển thị vị trí tọa độ
    private var coinMarker: TextView? = null
    private var orderMarker: TextView? = null
    private var markersVisible = false
    private val markerSize = 90 // Kích thước vòng tròn tâm ngắm (px)

    private var timeOffset: Long = 0
    @Volatile private var isRunning = false
    private val mainHandler = Handler(Looper.getMainLooper())

    // Tọa độ mặc định (Có thể kéo tâm ngắm trên màn hình để chỉnh trực tiếp)
    private var coinX = 1140f
    private var coinY = 895f
    private var orderX = 950f
    private var orderY = 2350f

    private var tvCoordInfo: TextView? = null

    private val showReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            mainHandler.post {
                if (floatView == null) {
                    showFloatingWindow()
                } else {
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
            x = 80
            y = 180
        }

        floatView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // 1. Nút thu gọn nhỏ (Hiện ra khi bấm "Thu gọn")
        miniBtn = TextView(this).apply {
            text = "⚡ Săn"
            setTextColor(Color.YELLOW)
            textSize = 13f
            setPadding(28, 18, 28, 18)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#EEFF4500"))
                cornerRadius = 40f
                setStroke(3, Color.WHITE)
            }
            visibility = View.GONE
        }

        // 2. Bảng điều khiển chính
        mainPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E6121212"))
                cornerRadius = 24f
                setStroke(2, Color.parseColor("#44FFFFFF"))
            }
            setPadding(24, 20, 24, 20)
        }

        val tvStatus = TextView(this).apply {
            text = "✥ Giữ dòng này để kéo bảng"
            setTextColor(Color.parseColor("#00FF7F"))
            textSize = 12f
        }

        tvCoordInfo = TextView(this).apply {
            text = "Xu:(${coinX.toInt()},${coinY.toInt()}) | Đặt:(${orderX.toInt()},${orderY.toInt()})"
            setTextColor(Color.LTGRAY)
            textSize = 10.5f
            setPadding(0, 4, 0, 4)
        }

        val rowTime = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val lblHour = TextView(this).apply {
            text = "Giờ(X): "
            setTextColor(Color.WHITE)
            textSize = 12f
        }

        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val edtHour = EditText(this).apply {
            setText(currentHour.toString())
            setTextColor(Color.YELLOW)
            textSize = 13f
            inputType = InputType.TYPE_CLASS_NUMBER
            width = 100
        }

        val lblMin = TextView(this).apply {
            text = " :59:59."
            setTextColor(Color.WHITE)
            textSize = 12f
        }

        val edtMs = EditText(this).apply {
            setText("980")
            setTextColor(Color.YELLOW)
            textSize = 13f
            inputType = InputType.TYPE_CLASS_NUMBER
            width = 130
        }

        rowTime.addView(lblHour)
        rowTime.addView(edtHour)
        rowTime.addView(lblMin)
        rowTime.addView(edtMs)

        // Hàng nút chức năng 1: Giờ mạng VN | Giờ máy | Tâm ngắm
        val rowBtn1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val btnSyncVN = Button(this).apply {
            text = "Giờ VN"
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

        rowBtn1.addView(btnSyncVN)
        rowBtn1.addView(btnLocalTime)
        rowBtn1.addView(btnMarker)

        // Hàng nút chức năng 2: Chạy hẹn giờ | Thu gọn
        val rowBtn2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val btnStart = Button(this).apply {
            text = "▶ BẮT ĐẦU CHẠY"
            textSize = 11f
        }

        val btnMinimize = Button(this).apply {
            text = "Thu gọn"
            textSize = 10f
        }

        rowBtn2.addView(btnStart)
        rowBtn2.addView(btnMinimize)

        mainPanel?.addView(tvStatus)
        mainPanel?.addView(tvCoordInfo)
        mainPanel?.addView(rowTime)
        mainPanel?.addView(rowBtn1)
        mainPanel?.addView(rowBtn2)

        floatView?.addView(miniBtn)
        floatView?.addView(mainPanel)

        // Bỏ focus bàn phím khi chạm ra ngoài
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

        // Kéo thả bảng chính
        setupDrag(tvStatus, params, floatView!!, null)
        // Kéo thả nút thu gọn và bấm để mở lại bảng
        setupDrag(miniBtn!!, params, floatView!!) {
            miniBtn?.visibility = View.GONE
            mainPanel?.visibility = View.VISIBLE
        }

        // Nút 1A: Đồng bộ Giờ chuẩn Việt Nam (Qua Cloudflare / Google - Không đụng tới Shopee)
        btnSyncVN.setOnClickListener {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            windowManager?.updateViewLayout(floatView, params)
            tvStatus.text = "Đang lấy giờ chuẩn VN..."
            thread {
                val urls = listOf("https://1.1.1.1", "https://www.google.com.vn", "https://www.apple.com")
                var synced = false
                for (u in urls) {
                    try {
                        val t1 = System.currentTimeMillis()
                        val conn = URL(u).openConnection() as HttpURLConnection
                        conn.requestMethod = "HEAD"
                        conn.connectTimeout = 2000
                        conn.readTimeout = 2000
                        conn.connect()
                        val serverDate = conn.date
                        val t2 = System.currentTimeMillis()
                        conn.disconnect()

                        if (serverDate > 0) {
                            val rtt = (t2 - t1) / 2
                            timeOffset = (serverDate + rtt) - t2
                            val cal = Calendar.getInstance().apply { timeInMillis = System.currentTimeMillis() + timeOffset }
                            val h = cal.get(Calendar.HOUR_OF_DAY)
                            mainHandler.post {
                                edtHour.setText(h.toString())
                                tvStatus.text = "Giờ VN chuẩn | Lệch: ${timeOffset}ms (Ping ${rtt * 2}ms)"
                            }
                            synced = true
                            break
                        }
                    } catch (_: Exception) {}
                }
                if (!synced) {
                    timeOffset = 0
                    mainHandler.post {
                        tvStatus.text = "Đã dùng Giờ Điện Thoại (Lệch: 0ms)"
                    }
                }
            }
        }

        // Nút 1B: Dùng trực tiếp Giờ Điện Thoại (An toàn tuyệt đối 100%, không cần mạng)
        btnLocalTime.setOnClickListener {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            windowManager?.updateViewLayout(floatView, params)
            timeOffset = 0
            val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            edtHour.setText(h.toString())
            tvStatus.text = "Đang dùng Giờ Điện Thoại (Offline 100%)"
        }

        // Nút 2: Bật/Tắt Tâm ngắm tọa độ trên màn hình
        btnMarker.setOnClickListener {
            toggleMarkers()
            btnMarker.text = if (markersVisible) "Tâm: Bật" else "Tâm: Tắt"
        }

        // Nút 3: Thu gọn bảng thành icon nhỏ (Sửa lỗi mất bảng)
        btnMinimize.setOnClickListener {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            windowManager?.updateViewLayout(floatView, params)
            mainPanel?.visibility = View.GONE
            miniBtn?.visibility = View.VISIBLE
        }

        // Nút 4: Chạy hẹn giờ
        btnStart.setOnClickListener {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            windowManager?.updateViewLayout(floatView, params)

            if (isRunning) {
                isRunning = false
                btnStart.text = "▶ BẮT ĐẦU CHẠY"
                tvStatus.text = "Đã hủy hẹn giờ"
                return@setOnClickListener
            }

            // Tự động ẩn tâm ngắm khi bắt đầu chạy để không cản trở thao tác chạm
            if (markersVisible) {
                toggleMarkers()
                btnMarker.text = "Tâm: Tắt"
            }

            val targetHour = edtHour.text.toString().toIntOrNull() ?: 23
            val targetMs = edtMs.text.toString().toIntOrNull() ?: 980

            isRunning = true
            btnStart.text = "⏹ HỦY CHẠY"
            tvStatus.text = "Đợi: $targetHour:59:59.$targetMs"

            thread {
                val now = System.currentTimeMillis() + timeOffset
                val cal = Calendar.getInstance().apply {
                    timeInMillis = now
                    set(Calendar.HOUR_OF_DAY, targetHour)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, targetMs)
                }
                var targetTime = cal.timeInMillis
                if (targetTime < now) {
                    targetTime += 24 * 60 * 60 * 1000L
                }

                while (isRunning) {
                    val remain = targetTime - (System.currentTimeMillis() + timeOffset)
                    if (remain <= 0) break
                    if (remain > 150) Thread.sleep(20)
                }

                if (!isRunning) return@thread

                // BƯỚC 1: Tắt Shopee Xu tại tọa độ tâm Xanh (Có random ±3px như tay người thật)
                humanTap(coinX, coinY)

                // BƯỚC 2: Đợi 110ms cho giá cập nhật rồi bấm Đặt hàng tại tọa độ tâm Đỏ
                Thread.sleep(110)
                humanTap(orderX, orderY)
                Thread.sleep(50)
                humanTap(orderX, orderY)

                isRunning = false
                mainHandler.post {
                    btnStart.text = "▶ BẮT ĐẦU CHẠY"
                    tvStatus.text = "Đã tắt Xu & bấm Đặt hàng!"
                }
            }
        }

        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        windowManager?.addView(floatView, params)
    }

    // Hàm hiển thị / ẩn 2 Tâm ngắm tọa độ
    private fun toggleMarkers() {
        if (markersVisible) {
            coinMarker?.let { windowManager?.removeView(it) }
            orderMarker?.let { windowManager?.removeView(it) }
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

    // Tạo vòng tròn tâm ngắm có thể kéo thả trực tiếp trên màn hình
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
            setTextColor(Color.WHITE)
            textSize = 10f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(bgColor)
                setStroke(4, Color.YELLOW)
            }
        }

        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f

        tv.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - touchX).toInt()
                    params.y = startY + (event.rawY - touchY).toInt()
                    windowManager?.updateViewLayout(tv, params)
                    val centerX = params.x + markerSize / 2f
                    val centerY = params.y + markerSize / 2f
                    onMoved(centerX, centerY)
                    true
                }
                else -> false
            }
        }

        windowManager?.addView(tv, params)
        return tv
    }

    private fun updateCoordText() {
        tvCoordInfo?.text = "Xu:(${coinX.toInt()},${coinY.toInt()}) | Đặt:(${orderX.toInt()},${orderY.toInt()})"
    }

    // Hỗ trợ kéo thả mượt mà và phân biệt giữa Kéo và Bấm
    private fun setupDrag(handleView: View, params: WindowManager.LayoutParams, rootView: View, onClick: (() -> Unit)?) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        handleView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(rootView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dx = kotlin.math.abs(event.rawX - initialTouchX)
                    val dy = kotlin.math.abs(event.rawY - initialTouchY)
                    if (dx < 12 && dy < 12) {
                        onClick?.invoke()
                    }
                    true
                }
                else -> false
            }
        }
    }

    // Chạm giả lập ngón tay người thật (Random tọa độ ±3px và thời gian giữ 20-35ms)
    private fun humanTap(x: Float, y: Float) {
        val jitterX = (x + Random.nextInt(-3, 4)).coerceAtLeast(1f)
        val jitterY = (y + Random.nextInt(-3, 4)).coerceAtLeast(1f)
        val duration = Random.nextLong(20, 36)

        val path = Path().apply { moveTo(jitterX, jitterY) }
        val stroke = GestureDescription.StrokeDescription(path, 0, duration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(showReceiver) } catch (_: Exception) {}
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}
