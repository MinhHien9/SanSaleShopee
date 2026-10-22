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
            text = "TOOL SĂN SALE SHOPEE\n(Tắt Xu: 1140, 895 -> Đặt hàng)\n\nHướng dẫn: Hãy cấp đủ 2 quyền bên dưới để hiện bảng điều khiển nổi."
            textSize = 16f
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
            text = "2. Bật Dịch vụ Trợ năng (Để hiện bảng nổi)"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                Toast.makeText(context, "Hãy tìm 'Săn Sale Shopee' và Bật lên!", Toast.LENGTH_LONG).show()
            }
        }

        layout.addView(info)
        layout.addView(btnOverlay)
        layout.addView(btnAccess)
        setContentView(layout)
    }
}
