package com.example.myapplication

import android.animation.ObjectAnimator
import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.TextView

/**
 * WeatherHazeActivity（雾霾场景动画页）
 *
 * 用途：
 * - 作为 WeatherTimeWidget 点击后的“雾霾/灰蒙蒙”场景展示页
 * - 页面背景透明、短时间展示一句语录 + 一张雾霾图，然后自动关闭
 *
 * 设计特点：
 * - 继承 Activity + setContentView（不是 Compose）
 * - 透明背景覆盖在桌面之上，避免出现黑底/白底
 * - TOTAL_MS 控制整个页面停留时长（到点自动 finish）
 */
class WeatherHazeActivity : Activity() {

    // 主线程 Handler：用于延迟关闭 Activity（postDelayed）
    private val handler = Handler(Looper.getMainLooper())

    // 页面总停留时长（毫秒）：到时间后自动关闭
    private val TOTAL_MS = 1600L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /**
         * 透明背景 + 边到边：
         * - 让 Activity 看起来像“浮在桌面上的动画层”
         * - 不隐藏状态栏，只是让布局可以延伸到系统栏下面
         */
        window.decorView.setBackgroundColor(Color.TRANSPARENT)
        if (Build.VERSION.SDK_INT >= 30) {
            // Android 11+：允许内容绘制到系统栏区域（不额外裁剪）
            window.setDecorFitsSystemWindows(false)
        } else {
            // Android 10-：旧写法
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }

        // 加载 XML 布局（雾霾场景专用）
        setContentView(R.layout.activity_weather_haze)

        // 取出布局里的控件
        val img = findViewById<ImageView>(R.id.img_main)   // 主图（雾霾图）
        val quote = findViewById<TextView>(R.id.txt_quote) // 语录文本

        // 设置雾霾图（对应你的资源图）
        img.setImageResource(R.drawable.weather_widget_haze)

        /**
         * 语录选择逻辑：
         * - is_first_today：由 Widget 点击时传入，用来控制“今天第一次点击”的特殊文案策略
         * - raw：本地缓存的天气 JSON（用于从天气里抽取描述/明天/今天等信息）
         * - pickQuote：统一的语录选择器（白天/雾霾共用一套规则）
         */
        val isFirstToday = intent.getBooleanExtra("is_first_today", false)
        val raw = AppSettings.getWeatherCacheJson(this)

        // 雾霾场景：仍然沿用白天语录挑选规则（preferTomorrowWeather=false 表示偏向“今天/当前”）
        quote.text = WeatherUiText.pickQuote(
            rawJson = raw,
            preferTomorrowWeather = false,
            isFirstToday = isFirstToday
        )

        // 文字淡入：从透明到可见（更柔和一些）
        ObjectAnimator.ofFloat(quote, View.ALPHA, 0f, 1f).apply {
            duration = 220
            start()
        }

        /**
         * 到时间自动关闭：
         * - finish() 结束 Activity
         * - overridePendingTransition(0,0) 取消转场动画，避免闪一下
         */
        handler.postDelayed({
            finish()
            overridePendingTransition(0, 0)
        }, TOTAL_MS)

        // 启动时同样取消转场动画（更像“瞬间弹出层”）
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        // 防止 Activity 销毁后仍然触发延迟回调（避免泄漏/重复 finish）
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
