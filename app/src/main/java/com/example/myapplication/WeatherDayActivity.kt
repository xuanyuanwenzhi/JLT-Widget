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
 * WeatherDayActivity（白天动画页）
 * - 被 WeatherTimeWidget 点击后跳转打开（白天场景）
 * - 透明背景 + 轻量动画：文字淡入、云朵左右飘
 * - 一段时间后自动 finish() 关闭（像“弹出动画卡片”一样）
 */
class WeatherDayActivity : Activity() {

    /** 用于定时关闭页面（防止停留太久） */
    private val handler = Handler(Looper.getMainLooper())

    /**
     * 页面总停留时长（毫秒）
     * - 现在是 1600ms，也就是 1.6s 后自动关闭
     * - 如果你觉得停留太久/太短，可以改这个值
     */
    private val TOTAL_MS = 1600L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 让 Activity 背景透明（配合 theme=Transparent）
        window.decorView.setBackgroundColor(Color.TRANSPARENT)

        // 不隐藏状态栏，只让内容延伸到系统栏下面（避免全屏提示）
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }

        // 加载布局（包含云朵 ImageView 和 文案 TextView）
        setContentView(R.layout.activity_weather_day)

        // 获取控件引用
        val cloud = findViewById<ImageView>(R.id.img_cloud)
        val quote = findViewById<TextView>(R.id.txt_quote)

        // is_first_today：由 WeatherTimeWidget 传入，用于判断“今天第一次点击”
        val isFirstToday = intent.getBooleanExtra("is_first_today", false)

        // 读取天气缓存（用于从缓存里解析“当前/明天”等信息，生成文案）
        val raw = AppSettings.getWeatherCacheJson(this)

        /**
         * 白天：按规则抽一句
         * - preferTomorrowWeather=false：偏向“当前/今天”而不是明天
         * - isFirstToday：允许你在“今天第一次点击”时走特殊文案逻辑
         * - 内部还会有一定概率抽“天气语录”（你代码里写过 20%）
         */
        quote.text = WeatherUiText.pickQuote(
            rawJson = raw,
            preferTomorrowWeather = false,
            isFirstToday = isFirstToday
        )

        // 文案淡入动画（0 -> 1）
        ObjectAnimator.ofFloat(quote, View.ALPHA, 0f, 1f).apply {
            duration = 220
        }.start()

        /**
         * 云朵左右轻微飘动
         * - translationX：-12px -> 12px
         * - repeatCount=1 + REVERSE：来回一次（相当于两段）
         */
        ObjectAnimator.ofFloat(cloud, View.TRANSLATION_X, -12f, 12f).apply {
            duration = 1000
            repeatMode = ObjectAnimator.REVERSE
            repeatCount = 1
            start()
        }

        // TOTAL_MS 后自动关闭，且不播放切换动画（保持“弹出层”的感觉）
        handler.postDelayed({
            finish()
            overridePendingTransition(0, 0)
        }, TOTAL_MS)

        // 打开时也不播放切换动画
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        // 避免 handler 引用导致泄漏 / Activity 退出后仍回调
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
