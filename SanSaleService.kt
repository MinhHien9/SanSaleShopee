package com.sansale.shopee

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import kotlin.concurrent.thread

class SanSaleService : AccessibilityService() {
    private var windowManager: WindowManager? = null
    private var floatView: LinearLayout? = null
    private var timeOffset: Long = 0
    @Volatile private var isRunning = false
    private val mainHandler = Handler(Looper.getMainLooper())

    // Tọa độ nút Dùng Shopee Xu của bạn
    private val COIN_X = 1140f
    private val COIN_Y = 895f
    // Tọa độ dự phòng nút Đặt hàng
    private val ORDER_X = 950f
    private val ORDER_Y = 2550f

    override fun onServiceConnected() {
        super.onServiceConnected()
        showFloatingWindow()
    }

    private fun showFloatingWindow() {
        if (floatView != null) return
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 200
        }

        floatView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#DD000000"))
            setPadding(24, 20, 24, 20)
        }

        val tvStatus = TextView(this).apply {
            text = "Giữ dòng này để kéo bảng"
            setTextColor(Color.parseColor("#00FF7F"))
            textSize = 12f
        }

        val rowTime = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val lblHour = TextView(this).apply {
            text = "Giờ(X): "
            setTextColor(Color.WHITE)
            textSize = 13f
        }

        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val edtHour = EditText(this).apply {
            setText(currentHour.toString())
            setTextColor(Color.YELLOW)
            textSize = 14f
            inputType = InputType.TYPE_CLASS_NUMBER
            width = 110
        }

        val lblMin = TextView(this).apply {
            text = " :59:59."
            setTextColor(Color.WHITE)
            textSize = 13f
        }

        val edtMs = EditText(this).apply {
            setText("980")
            setTextColor(Color.YELLOW)
            textSize = 14f
            inputType = InputType.TYPE_CLASS_NUMBER
            width = 140
        }

        rowTime.addView(lblHour)
        rowTime.addView(edtHour)
        rowTime.addView(lblMin)
        rowTime.addView(edtMs)

        val rowBtn = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val btnSync = Button(this).apply {
            text = "1. Đồng bộ"
            textSize = 11f
        }

        val btnStart = Button(this).apply {
            text = "2. Chạy"
            textSize = 11f
        }

        val btnHide = Button(this).apply {
            text = "Ẩn"
            textSize = 11f
        }

        rowBtn.addView(btnSync)
        rowBtn.addView(btnStart)
        rowBtn.addView(btnHide)

        floatView?.addView(tvStatus)
        floatView?.addView(rowTime)
        floatView?.addView(rowBtn)

        // Cho phép ẩn bàn phím khi bấm ra ngoài
        floatView?.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                windowManager?.updateViewLayout(floatView, params)
            }
            false
        }

        val focusListener = {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            windowManager?.updateViewLayout(floatView, params)
        }
        edtHour.setOnClickListener { focusListener() }
        edtMs.setOnClickListener { focusListener() }

        // Kéo thả bảng nổi
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        tvStatus.setOnTouchListener { _, event ->
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
                    windowManager?.updateViewLayout(floatView, params)
                    true
                }
                else -> false
            }
        }

        // Nút 1: Đồng bộ giờ với máy chủ Shopee
        btnSync.setOnClickListener {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            windowManager?.updateViewLayout(floatView, params)
            tvStatus.text = "Đang đồng bộ Shopee..."
            thread {
                try {
                    val t1 = System.currentTimeMillis()
                    val conn = URL("https://shopee.vn/api/v4/client/timestamp").openConnection() as HttpURLConnection
                    conn.connectTimeout = 3000
                    conn.readTimeout = 3000
                    val res = conn.inputStream.bufferedReader().readText()
                    val t2 = System.currentTimeMillis()
                    val rtt = (t2 - t1) / 2
                    val serverTs = JSONObject(res).getJSONObject("data").getLong("timestamp") * 1000L
                    timeOffset = (serverTs + rtt) - t2

                    val cal = Calendar.getInstance().apply { timeInMillis = System.currentTimeMillis() + timeOffset }
                    val shopeeHour = cal.get(Calendar.HOUR_OF_DAY)

                    mainHandler.post {
                        edtHour.setText(shopeeHour.toString())
                        tvStatus.text = "Lệch: ${timeOffset}ms | Ping: ${rtt * 2}ms"
                    }
                } catch (e: Exception) {
                    mainHandler.post { tvStatus.text = "Lỗi mạng khi lấy giờ!" }
                }
            }
        }

        // Nút 2: Hẹn giờ chạy
        btnStart.setOnClickListener {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            windowManager?.updateViewLayout(floatView, params)

            if (isRunning) {
                isRunning = false
                btnStart.text = "2. Chạy"
                tvStatus.text = "Đã hủy hẹn giờ"
                return@setOnClickListener
            }

            val targetHour = edtHour.text.toString().toIntOrNull() ?: 23
            val targetMs = edtMs.text.toString().toIntOrNull() ?: 980

            isRunning = true
            btnStart.text = "Hủy"
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

                // Vòng lặp canh giờ chính xác đến từng mili-giây
                while (isRunning) {
                    val remain = targetTime - (System.currentTimeMillis() + timeOffset)
                    if (remain <= 0) break
                    if (remain > 150) Thread.sleep(20)
                }

                if (!isRunning) return@thread

                // BƯỚC 1: Tắt Shopee Xu tại (1140, 895) đúng 98 khắc
                tap(COIN_X, COIN_Y)

                // BƯỚC 2: Chờ 110ms cho giá cập nhật rồi bắn liên hoàn vào nút Đặt hàng
                Thread.sleep(110)
                clickOrderButton()
                Thread.sleep(45)
                clickOrderButton()
                Thread.sleep(45)
                clickOrderButton()

                isRunning = false
                mainHandler.post {
                    btnStart.text = "2. Chạy"
                    tvStatus.text = "Đã tắt Xu & Đặt hàng!"
                }
            }
        }

        btnHide.setOnClickListener {
            isRunning = false
            windowManager?.removeView(floatView)
            floatView = null
        }

        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        windowManager?.addView(floatView, params)
    }

    private fun tap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 15)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    private fun clickOrderButton() {
        val root = rootInActiveWindow
        if (root != null) {
            val nodes = root.findAccessibilityNodeInfosByText("Đặt hàng")
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    if (node.isClickable) {
                        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        return
                    }
                    var parent = node.parent
                    while (parent != null) {
                        if (parent.isClickable) {
                            parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            return
                        }
                        parent = parent.parent
                    }
                }
            }
        }
        tap(ORDER_X, ORDER_Y)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}
