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
 * WeatherNightActivity（夜晚场景动画页）
 *
 * 用途：
 * - WeatherTimeWidget 点击后，根据时间/调试场景，可能会打开夜晚页面
 * - 页面背景透明，显示月亮 + 一句语录，然后短暂停留后自动关闭
 *
 * 行为特点：
 * - preferTomorrowWeather = true：夜晚更偏向展示“明天的天气/提醒类语录”
 * - TOTAL_MS 控制页面停留总时长
 */
class WeatherNightActivity : Activity() {

    // 主线程 Handler：用于定时关闭 Activity
    private val handler = Handler(Looper.getMainLooper())

    // 页面展示总时长（毫秒）：到点自动 finish
    private val TOTAL_MS = 1500L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /**
         * 透明背景 + 边到边：
         * - 让 Activity 像一个“浮层动画”，覆盖在桌面上不突兀
         * - 不隐藏系统栏，只是允许内容延伸到系统栏区域
         */
        window.decorView.setBackgroundColor(Color.TRANSPARENT)
        if (Build.VERSION.SDK_INT >= 30) {
            // Android 11+：内容可绘制到系统栏区域
            window.setDecorFitsSystemWindows(false)
        } else {
            // Android 10-：旧版本的沉浸式布局写法
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }

        // 加载夜晚场景 XML 布局
        setContentView(R.layout.activity_weather_night)

        // 获取布局里的控件
        val moon = findViewById<ImageView>(R.id.img_moon)  // 月亮图
        val quote = findViewById<TextView>(R.id.txt_quote) // 语录文本

        /**
         * 读取“今天是否第一次点击”标记：
         * - 一般由 Widget 点击时传进来，用于决定是否走“首次点击特殊语录”
         *
         * raw 为本地缓存的天气 JSON：
         * - WeatherUiText.pickQuote 会基于 rawJson 抽取描述/明天/今天等信息
         */
        val isFirstToday = intent.getBooleanExtra("is_first_today", false)
        val raw = AppSettings.getWeatherCacheJson(this)

        /**
         * 夜晚语录策略：
         * - preferTomorrowWeather = true：更倾向“明日天气/提醒类语录”
         * - isFirstToday：今天第一次点击时可能优先展示“欢迎/提示类语录”
         */
        quote.text = WeatherUiText.pickQuote(
            rawJson = raw,
            preferTomorrowWeather = true,
            isFirstToday = isFirstToday
        )

        // 语录淡入（更柔和）
        ObjectAnimator.ofFloat(quote, View.ALPHA, 0f, 1f).apply {
            duration = 220
            start()
        }

        /**
         * 月亮呼吸效果：
         * - alpha 在 0.85 -> 1.0 之间轻微变化
         * - repeatCount=1 + REVERSE：来回一次（共两段）
         */
        ObjectAnimator.ofFloat(moon, View.ALPHA, 0.85f, 1f).apply {
            duration = 900
            repeatMode = ObjectAnimator.REVERSE
            repeatCount = 1
            start()
        }

        /**
         * 定时关闭页面：
         * - finish() 结束 Activity
         * - overridePendingTransition(0,0) 取消转场动画，避免闪动
         */
        handler.postDelayed({
            finish()
            overridePendingTransition(0, 0)
        }, TOTAL_MS)

        // 启动时也取消转场动画（更像弹出层）
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        // 清理延迟回调：避免 Activity 销毁后仍触发 finish（或潜在泄漏）
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
