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
 * WeatherSunriseActivity
 * - 用于天气小组件“日出场景”的轻量动画弹层 Activity
 * - 设计目标：
 *   1) 透明背景覆盖在桌面/当前界面上（不做全屏沉浸，避免系统提示）
 *   2) 显示一句语录（早上第一次点击会更偏“早安”）
 *   3) 太阳从下往上升起，然后停留一会儿自动关闭
 */
class WeatherSunriseActivity : Activity() {

    // 主线程 Handler：用于延迟关闭 Activity
    private val handler = Handler(Looper.getMainLooper())

    // ===== 动画参数（你要调节效果，改这里即可） =====
    private val SUN_RISE_MS = 900L      // 太阳上升动画时长
    private val SUN_START_Y = 120f      // 太阳初始偏移（向下 120px）
    private val HOLD_MS = 900L          // 太阳升起后停留时长
    private val TOTAL_MS = SUN_RISE_MS + HOLD_MS + 200L // 兜底总时长（多加一点缓冲）

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ 透明背景：让 Activity 看起来像“悬浮动效”
        window.decorView.setBackgroundColor(Color.TRANSPARENT)

        // ✅ 不隐藏状态栏：只做 layout_fullscreen（避免全屏提示/手势条异常）
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }

        // UI 布局：包含太阳图 + 语录 TextView
        setContentView(R.layout.activity_weather_sunrise)

        val sun = findViewById<ImageView>(R.id.img_sun)
        val quote = findViewById<TextView>(R.id.txt_quote)

        // 从 intent 里拿“今天是否第一次点击”（用于早安语录逻辑）
        val isFirstToday = intent.getBooleanExtra("is_first_today", false)

        // 天气原始缓存（用于 WeatherUiText 做“天气类语录”判断）
        val raw = AppSettings.getWeatherCacheJson(this)

        /**
         * 早上语录选择：
         * - WeatherUiText 内部会根据时间段 + 概率做分流
         * - 若 6~9 且 isFirstToday=true，会优先给“早安”
         */
        quote.text = WeatherUiText.pickQuote(
            rawJson = raw,
            preferTomorrowWeather = false,
            isFirstToday = isFirstToday
        )

        // 语录淡入，让文字出现更柔和
        ObjectAnimator.ofFloat(quote, View.ALPHA, 0f, 1f).apply {
            duration = 220
            start()
        }

        /**
         * 太阳上升动画：
         * - 先把太阳放到“更下面”（translationY = SUN_START_Y）
         * - 再动画移动到 0（回到布局定义的位置）
         */
        sun.translationY = SUN_START_Y
        ObjectAnimator.ofFloat(sun, View.TRANSLATION_Y, SUN_START_Y, 0f).apply {
            duration = SUN_RISE_MS
            start()
        }

        // ✅ 兜底自动关闭：到时间就 finish，避免异常卡住不退
        handler.postDelayed({
            finish()
            overridePendingTransition(0, 0) // 关闭不带转场，像“弹层消失”
        }, TOTAL_MS)

        // 打开时也不带转场：像“突然出现的动画层”
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        // 释放所有延迟任务，避免内存泄漏/重复回调
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
