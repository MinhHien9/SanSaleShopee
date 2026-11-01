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
import kotlin.math.abs
import kotlin.random.Random

class SanSaleService : AccessibilityService() {
    private var windowManager: WindowManager? = null
    private var floatView: LinearLayout? = null
    private var mainPanel: LinearLayout? = null
    private var miniBtn: TextView? = null
    private var tvLiveClock: TextView? = null
    private var tvCoordInfo: TextView? = null

    private var coinMarker: TextView? = null
    private var orderMarker: TextView? = null
    private var markersVisible = false
    private val markerSize = 90

    @Volatile private var timeOffset: Long = 0
    @Volatile private var isRunning = false
    private var isMinimized = false
    private var isZeroMode = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val miniTimeFormat = SimpleDateFormat("HH:mm:ss.S", Locale.US)

    private var coinX = 1140f
    private var coinY = 895f
    private var orderX = 950f
    private var orderY = 2350f

    private val clockRunnable = object : Runnable {
        override fun run() {
            val nowShopee = System.currentTimeMillis() + timeOffset
            val dateObj = Date(nowShopee)
            if (!isMinimized) {
                tvLiveClock?.text = timeFormat.format(dateObj)
            } else {
                miniBtn?.text = miniTimeFormat.format(dateObj)
            }
            mainHandler.postDelayed(this, 35)
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

        floatView = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        miniBtn = TextView(this).apply {
            text = "00:00:00.0"
            setTextColor(Color.YELLOW)
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setPadding(24, 14, 24, 14)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#EE1B1B1B"))
                cornerRadius = 40f
                setStroke(3, Color.parseColor("#00E676"))
            }
            visibility = View.GONE
        }

        mainPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#EA121212"))
                cornerRadius = 24f
                setStroke(2, Color.parseColor("#55FFFFFF"))
            }
            setPadding(22, 16, 22, 16)
        }

        tvLiveClock = TextView(this).apply {
            text = "00:00:00.000"
            setTextColor(Color.parseColor("#00FF66"))
            textSize = 18f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(12, 8, 12, 8)
            background = GradientDrawable().apply {
                setColor(Color.BLACK)
                cornerRadius = 12f
                setStroke(2, Color.parseColor("#00E676"))
            }
        }

        val tvStatus = TextView(this).apply {
            text = "Che do: X:59:59.980 (Giu dong ho de keo)"
            setTextColor(Color.parseColor("#FFD54F"))
            textSize = 11f
            setPadding(0, 6, 0, 2)
        }

        tvCoordInfo = TextView(this).apply {
            text = "Xu:(${coinX.toInt()},${coinY.toInt()}) | Dat:(${orderX.toInt()},${orderY.toInt()})"
            setTextColor(Color.LTGRAY)
            textSize = 10.5f
            setPadding(0, 2, 0, 4)
        }

        val rowTime = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val btnToggleMode = Button(this).apply {
            text = "59:59"
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

        val rowBtn1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val btnSyncShopee = Button(this).apply { text = "Gio Shopee"; textSize = 10f }
        val btnLocalTime = Button(this).apply { text = "Gio May"; textSize = 10f }
        val btnMarker = Button(this).apply { text = "Tam: Tat"; textSize = 10f }
        rowBtn1.addView(btnSyncShopee)
        rowBtn1.addView(btnLocalTime)
        rowBtn1.addView(btnMarker)

        val rowBtn2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val btnStart = Button(this).apply { text = "CHAY"; textSize = 11f }
        val btnMinimize = Button(this).apply { text = "Thu gon"; textSize = 10f }
        val btnExit = Button(this).apply {
            text = "Tat App"
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

        val updateModeUI = {
            val cal = Calendar.getInstance().apply { timeInMillis = System.currentTimeMillis() + timeOffset }
            val h = cal.get(Calendar.HOUR_OF_DAY)
            if (isZeroMode) {
                btnToggleMode.text = "00:00"
                lblMinSec.text = ":00:00."
                edtMs.setText("000")
                edtHour.setText(((h + 1) % 24).toString())
            } else {
                btnToggleMode.text = "59:59"
                lblMinSec.text = ":59:59."
                edtMs.setText("980")
                edtHour.setText(h.toString())
            }
        }

        btnToggleMode.setOnClickListener {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            windowManager?.updateViewLayout(floatView, params)
            isZeroMode = !isZeroMode
            updateModeUI()
            tvStatus.text = if (isZeroMode) "Che do: X:00:00.000" else "Che do: X:59:59.980"
        }

        btnSyncShopee.setOnClickListener {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            windowManager?.updateViewLayout(floatView, params)
            tvStatus.text = "Dang dong bo gio Shopee..."
            thread {
                var synced = false
                var rtt = 0L
                var src = ""
                try {
                    val t1 = System.currentTimeMillis()
                    val conn = URL("https://shopee.vn/api/v4/client/timestamp").openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.useCaches = false
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 15) Chrome/131.0.0.0 Mobile")
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
                            src = "Shopee API"
                            synced = true
                        }
                    }
                    conn.disconnect()
                } catch (_: Exception) {}

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
                            val serverTs = (tsLine.removePrefix("ts=").toDouble() * 1000.0).toLong()
                            rtt = (t2 - t1) / 2
                            timeOffset = (serverTs + rtt) - t2
                            src = "Gio VN Chuan"
                            synced = true
                        }
                    } catch (_: Exception) {}
                }

                mainHandler.post {
                    updateModeUI()
                    if (synced) {
                        tvStatus.text = "OK $src | Lech: ${timeOffset}ms | Ping: ${rtt * 2}ms"
                    } else {
                        timeOffset = 0
                        tvStatus.text = "Dang dung Gio May (Lech: 0ms)"
                    }
                }
            }
        }

        btnLocalTime.setOnClickListener {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            windowManager?.updateViewLayout(floatView, params)
            timeOffset = 0
            updateModeUI()
            tvStatus.text = "Dang dung Gio May (Offline 100%)"
        }

        btnMarker.setOnClickListener {
            toggleMarkers()
            btnMarker.text = if (markersVisible) "Tam: Bat" else "Tam: Tat"
        }

        btnMinimize.setOnClickListener {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            windowManager?.updateViewLayout(floatView, params)
            isMinimized = true
            mainPanel?.visibility = View.GONE
            miniBtn?.visibility = View.VISIBLE
        }

        btnExit.setOnClickListener { shutdownApp() }

        btnStart.setOnClickListener {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            windowManager?.updateViewLayout(floatView, params)

            if (isRunning) {
                isRunning = false
                btnStart.text = "CHAY"
                tvStatus.text = "Da huy hen gio"
                return@setOnClickListener
            }

            if (markersVisible) {
                toggleMarkers()
                btnMarker.text = "Tam: Tat"
            }

            val targetHour = (edtHour.text.toString().toIntOrNull() ?: 0).coerceIn(0, 23)
            val targetMs = (edtMs.text.toString().toIntOrNull() ?: 0).coerceIn(0, 999)
            val targetMinSec = if (isZeroMode) 0 else 59
            val minSecLabel = if (isZeroMode) "00:00" else "59:59"
            val msFormatted = String.format(Locale.US, "%03d", targetMs)

            isRunning = true
            btnStart.text = "HUY"
            tvStatus.text = "Canh: $targetHour:$minSecLabel.$msFormatted"

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
                if (targetTime <= now) {
                    targetTime += 24 * 60 * 60 * 1000L
                }

                while (isRunning) {
                    val remain = targetTime - (System.currentTimeMillis() + timeOffset)
                    if (remain <= 0) break
                    if (remain > 150) Thread.sleep(20)
                }

                if (!isRunning) return@thread

                humanTap(coinX, coinY)
                Thread.sleep(110)
                humanTap(orderX, orderY)
                Thread.sleep(50)
                humanTap(orderX, orderY)

                isRunning = false
                mainHandler.post {
                    btnStart.text = "CHAY"
                    tvStatus.text = "Da ban luc $targetHour:$minSecLabel.$msFormatted!"
                }
            }
        }

        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        windowManager?.addView(floatView, params)
    }

    private fun shutdownApp() {
        isRunning = false
        mainHandler.removeCallbacks(clockRunnable)
        if (markersVisible) toggleMarkers()
        floatView?.let { try { windowManager?.removeView(it) } catch (_: Exception) {} }
        floatView = null
        Toast.makeText(this, "Da tat hoan toan Tool San Sale!", Toast.LENGTH_SHORT).show()
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
            coinMarker = createMarker("XU", Color.parseColor("#AA00E676"), coinX, coinY) { nx, ny ->
                coinX = nx; coinY = ny; updateCoordText()
            }
            orderMarker = createMarker("DAT", Color.parseColor("#AAFF3D00"), orderX, orderY) { nx, ny ->
                orderX = nx; orderY = ny; updateCoordText()
            }
            markersVisible = true
        }
    }

    private fun createMarker(label: String, bgColor: Int, initX: Float, initY: Float, onMoved: (Float, Float) -> Unit): TextView {
        val p = WindowManager.LayoutParams(
            markerSize, markerSize,
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
            textSize = 11f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(bgColor)
                setStroke(4, Color.YELLOW)
            }
        }

        var sx = 0; var sy = 0; var tx = 0f; var ty = 0f
        tv.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> { sx = p.x; sy = p.y; tx = ev.rawX; ty = ev.rawY; true }
                MotionEvent.ACTION_MOVE -> {
                    p.x = sx + (ev.rawX - tx).toInt()
                    p.y = sy + (ev.rawY - ty).toInt()
                    windowManager?.updateViewLayout(tv, p)
                    onMoved(p.x + markerSize / 2f, p.y + markerSize / 2f)
                    true
                }
                else -> false
            }
        }
        windowManager?.addView(tv, p)
        return tv
    }

    private fun updateCoordText() {
        tvCoordInfo?.text = "Xu:(${coinX.toInt()},${coinY.toInt()}) | Dat:(${orderX.toInt()},${orderY.toInt()})"
    }

    private fun setupDrag(handleView: View, params: WindowManager.LayoutParams, rootView: View, onClick: (() -> Unit)?) {
        var ix = 0; var iy = 0; var tx = 0f; var ty = 0f
        handleView.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> { ix = params.x; iy = params.y; tx = ev.rawX; ty = ev.rawY; true }
                MotionEvent.ACTION_MOVE -> {
                    params.x = ix + (ev.rawX - tx).toInt()
                    params.y = iy + (ev.rawY - ty).toInt()
                    windowManager?.updateViewLayout(rootView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (abs(ev.rawX - tx) < 12 && abs(ev.rawY - ty) < 12) onClick?.invoke()
                    true
                }
                else -> false
            }
        }
    }

    private fun humanTap(x: Float, y: Float) {
        val jx = (x + Random.nextInt(-3, 4)).coerceAtLeast(1f)
        val jy = (y + Random.nextInt(-3, 4)).coerceAtLeast(1f)
        val dur = Random.nextLong(20, 36)
        val path = Path().apply { moveTo(jx, jy) }
        val stroke = GestureDescription.StrokeDescription(path, 0, dur)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacks(clockRunnable)
        try { unregisterReceiver(showReceiver) } catch (_: Exception) {}
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}
