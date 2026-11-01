package com.sansale.shopee

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(50, 50, 50, 50)
        }

        val info = TextView(this).apply {
            text = "TOOL SĂN SALE SHOPEE v3.0 (AN TOÀN)\n\n" +
                   "• Đồng bộ giờ chuẩn VN / Giờ máy (Không chạm API Shopee)\n" +
                   "• Có Tâm ngắm kéo thả trực tiếp vào nút Xu & Đặt hàng\n" +
                   "• Thu gọn thành icon nổi không lo bị mất bảng"
            textSize = 15f
            setPadding(0, 0, 0, 40)
        }

        val btnOverlay = Button(this).apply {
            text = "1. Cấp quyền Hiển thị trên ứng dụng khác"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                } else {
                    Toast.makeText(context, "Đã cấp quyền Hiển thị nổi!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val btnAccess = Button(this).apply {
            text = "2. Bật Dịch vụ Trợ năng"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                Toast.makeText(context, "Hãy bật 'Săn Sale Shopee' trong Trợ năng!", Toast.LENGTH_LONG).show()
            }
        }

        val btnRestore = Button(this).apply {
            text = "3. Hiện lại Bảng điều khiển (Nếu đã ẩn)"
            setOnClickListener {
                sendBroadcast(Intent("com.sansale.shopee.SHOW_PANEL").setPackage(packageName))
                Toast.makeText(context, "Đã gửi lệnh hiện bảng điều khiển!", Toast.LENGTH_SHORT).show()
            }
        }

        layout.addView(info)
        layout.addView(btnOverlay)
        layout.addView(btnAccess)
        layout.addView(btnRestore)
        setContentView(layout)
    }
}
