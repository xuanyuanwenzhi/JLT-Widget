package com.example.myapplication

import android.animation.ObjectAnimator
import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.TextView

/**
 * WeatherWindActivity
 *
 * 作用：
 * - 以“帧动画”的方式播放龙卷风/大风动效（4 张图片）
 * - 使用两张 ImageView 交替淡入淡出，实现交叉渐变（cross-fade）
 * - 同时展示一条 WeatherUiText 的语录，并在短时间后自动 finish
 *
 * 动画节奏（每一帧总时长 0.5s）：
 * - holdMs  : 当前帧先保持不动一会儿（0.2s）
 * - fadeMs  : 当前帧开始淡出（0.3s）
 * - 下一帧在 nextFadeInDelay 时开始淡入（0.2s），与淡出重叠产生更柔和的切换
 */
class WeatherWindActivity : Activity() {

    /** 主线程 Handler：用于定时切帧、结束 Activity 等 */
    private val handler = Handler(Looper.getMainLooper())

    // ===== 动画节奏参数 =====
    /** 每一帧的总时长：0.5s = 0.2s 持有 + 0.3s 淡出（并叠加下一帧淡入） */
    private val frameTotalMs = 500L

    /** 当前帧在淡出之前，先保持显示的时间 */
    private val holdMs = 200L

    /** 淡入/淡出动画的持续时间 */
    private val fadeMs = 300L

    /**
     * 下一张图片开始淡入的延迟
     * - 这里设置为 200ms：即“淡出开始 200ms 后”启动下一帧淡入
     * - 因为淡出是 holdMs(200) 后开始，所以整体效果是：当前帧先 hold 0.2s，然后开始淡出；
     *   下一帧在淡出开始的同一时刻就准备（因为 nextFadeInDelay==holdMs 时视觉上会更紧凑）
     *
     * 你的注释里写了“淡出开始后 0.2s 开始淡入”，那就对应 nextFadeInDelay=200ms。
     */
    private val nextFadeInDelay = 200L

    /** 帧序列：4 张龙卷风图片（按顺序播放） */
    private val frames = intArrayOf(
        R.drawable.weather_tornado_01,
        R.drawable.weather_tornado_02,
        R.drawable.weather_tornado_03,
        R.drawable.weather_tornado_04
    )

    /**
     * 保存创建过的 ObjectAnimator，便于 onDestroy 时 cancel，防止泄漏/残留动画
     */
    private val animators = mutableListOf<ObjectAnimator>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ===== 透明背景 + 全屏沉浸式布局（与你其他天气 Activity 一致）=====
        window.decorView.setBackgroundColor(Color.TRANSPARENT)
        if (Build.VERSION.SDK_INT >= 30) {
            // Android 11+：让内容可以延伸到系统栏区域
            window.setDecorFitsSystemWindows(false)
        } else {
            // Android 10-：使用旧的 systemUiVisibility 做全屏布局
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }

        setContentView(R.layout.activity_weather_wind)

        // 两张 ImageView 用来做交叉渐变：一个显示当前帧，一个显示下一帧
        val a = findViewById<ImageView>(R.id.img_a)
        val b = findViewById<ImageView>(R.id.img_b)

        // 语录条
        val quote = findViewById<TextView>(R.id.txt_quote)

        // ===== 语录条：和 Sunrise / Snow 同一套逻辑 =====
        val isFirstToday = intent.getBooleanExtra("is_first_today", false)
        val raw = AppSettings.getWeatherCacheJson(this)
        quote.text = WeatherUiText.pickQuote(
            rawJson = raw,
            preferTomorrowWeather = false,
            isFirstToday = isFirstToday
        )
        // 语录淡入（220ms）
        ObjectAnimator.ofFloat(quote, View.ALPHA, 0f, 1f).apply { duration = 220 }.start()

        // ===== 帧动画初始化 =====
        var index = 0

        // 第一帧先放在 a 上显示
        a.setImageResource(frames[index])
        a.alpha = 1f
        b.alpha = 0f
        index++

        /**
         * 播放下一帧：
         * - 通过判断当前哪个 ImageView 更“亮”（alpha 更大）来决定 current/next
         * - current 做淡出，next 做淡入
         * - 每 frameTotalMs 调一次，直到帧播完，然后稍微延迟 finish
         */
        fun playNext() {
            // 播完所有帧：稍微延迟结束，让最后一帧有一点点停留
            if (index >= frames.size) {
                handler.postDelayed({
                    finish()
                    overridePendingTransition(0, 0)
                }, 200L)
                return
            }

            // 当前显示的那张（alpha 更大的一张）作为 current
            val current = if (a.alpha >= b.alpha) a else b
            // 另一张作为 next，用来承接下一帧淡入
            val next = if (current === a) b else a

            // 给 next 塞入下一帧资源，并从透明开始淡入
            next.setImageResource(frames[index])
            next.alpha = 0f
            index++

            // current：先 holdMs 保持，然后开始淡出到 0
            val fadeOut = ObjectAnimator.ofFloat(current, View.ALPHA, 1f, 0f).apply {
                startDelay = holdMs
                duration = fadeMs
                interpolator = LinearInterpolator() // 匀速，避免忽快忽慢
            }

            // next：延迟 nextFadeInDelay 后开始淡入到 1
            val fadeIn = ObjectAnimator.ofFloat(next, View.ALPHA, 0f, 1f).apply {
                startDelay = nextFadeInDelay
                duration = fadeMs
                interpolator = LinearInterpolator()
            }

            // 记录 animator，onDestroy 时统一 cancel
            animators += fadeOut
            animators += fadeIn

            // 启动本轮两条动画
            fadeOut.start()
            fadeIn.start()

            // frameTotalMs 后切到下一帧
            handler.postDelayed({ playNext() }, frameTotalMs)
        }

        // 开始播放
        playNext()

        // 进入/退出都不做系统默认转场（避免 widget 弹窗感太重）
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        // 取消所有动画，避免 Activity 销毁后还有引用/回调
        animators.forEach { it.cancel() }
        animators.clear()

        // 移除所有延迟任务，避免 onDestroy 后还触发 playNext/finish
        handler.removeCallbacksAndMessages(null)

        super.onDestroy()
    }
}
