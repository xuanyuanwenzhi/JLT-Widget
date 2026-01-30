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
import java.util.Calendar

/**
 * WeatherRainActivity
 * - “下雨场景”的全屏透明动画页（实际上就是：展示一张雨天背景图 + 一句语录）
 * - 设计成短暂弹出，显示一会儿后自动关闭，不进入最近任务列表
 *
 * 用途：
 * - WeatherTimeWidget（或调试面板）判断天气为雨时启动该 Activity
 * - 用来做“点击小组件 -> 弹出一段氛围动画/文案”的效果
 */
class WeatherRainActivity : Activity() {

    // 主线程 Handler：用于延时关闭页面（到点 finish）
    private val handler = Handler(Looper.getMainLooper())

    // 页面停留时长（毫秒）：显示完自动关闭
    private val totalMs = 1600L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ 透明背景：让这个 Activity 像“浮层”一样盖在桌面上
        window.decorView.setBackgroundColor(Color.TRANSPARENT)

        // ✅ 边到边显示：内容可以铺到状态栏区域（避免出现黑边）
        // 不主动隐藏状态栏，避免某些机型出现“全屏提示/沉浸提示”
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }

        // 加载雨天布局（一般包含一张背景图 + 一段文案 TextView）
        setContentView(R.layout.activity_weather_rain)

        // 主图（雨天背景/插画）
        val img = findViewById<ImageView>(R.id.img_main)

        // 文案显示区域
        val quote = findViewById<TextView>(R.id.txt_quote)

        // 设置雨天的背景资源
        img.setImageResource(R.drawable.weather_widget_rain)

        // Widget 点击时可能会携带：今天是否首次点击（用于早安逻辑）
        val isFirstToday = intent.getBooleanExtra("is_first_today", false)

        // 从缓存里拿到天气 JSON（Juhe / wttr 都可能）
        val raw = AppSettings.getWeatherCacheJson(this)

        // 简单规则：18 点以后更倾向用“明日天气类语录”（让晚上更像预告明天）
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val preferTomorrow = hour >= 18

        // 按时间段概率 + 天气解析，从语录库中挑一句
        quote.text = WeatherUiText.pickQuote(
            rawJson = raw,
            preferTomorrowWeather = preferTomorrow,
            isFirstToday = isFirstToday
        )

        // 文案淡入（避免突兀出现）
        ObjectAnimator.ofFloat(quote, View.ALPHA, 0f, 1f).apply {
            duration = 220
        }.start()

        // 到点自动关闭：让这个页面像短动画一样一闪而过
        handler.postDelayed({
            finish()
            // 关闭时不做转场动画（更像“浮层消失”）
            overridePendingTransition(0, 0)
        }, totalMs)

        // 打开时也不做转场动画
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        // 避免 Activity 销毁后还有延时任务导致泄漏/误触发
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
