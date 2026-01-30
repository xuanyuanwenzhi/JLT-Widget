package com.example.myapplication

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import kotlin.math.max
import kotlin.random.Random

/**
 * WeatherSnowActivity
 * - “下雪场景”的全屏透明动画页：雪人背景 + 语录条 + 动态雪花飘落
 *
 * 关键点：
 * 1) 使用透明 Activity（像浮层一样弹出）
 * 2) 雪花用 overlay(FrameLayout) 动态 addView，保证能飘过语录条
 * 3) 每个雪花用两个 ObjectAnimator：
 *    - translationY：下落（短距离慢飘）
 *    - alpha：渐隐（更像远近层次）
 * 4) 动画结束回调里 restartOne() 形成循环飘落效果
 */
class WeatherSnowActivity : Activity() {

    // 主线程 Handler：用于延时关闭页面
    private val handler = Handler(Looper.getMainLooper())

    // 页面总停留时长（毫秒）：到点自动 finish
    private val totalMs = 1800L

    // 雪花数量范围：每次进入随机生成一批
    private val minFlakes = 6
    private val maxFlakes = 18

    // 雪花图片素材（随机抽一个，增加变化）
    private val snowDrawables = intArrayOf(
        R.drawable.weather_anim_snowflake_1,
        R.drawable.weather_anim_snowflake_2,
        R.drawable.weather_anim_snowflake_3,
        R.drawable.weather_anim_snowflake_4
    )

    /**
     * 记录所有正在跑的 animator，便于 onDestroy() 里统一 cancel
     * 避免 Activity 退出后动画还在引用 View，导致泄漏或异常回调
     */
    private val runningAnimators = mutableListOf<ObjectAnimator>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ 透明背景：像桌面浮层一样弹出，不挡住桌面背景
        window.decorView.setBackgroundColor(Color.TRANSPARENT)

        // ✅ 边到边显示：内容允许铺到状态栏区域
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }

        // 加载布局（包含：雪人 ImageView、语录 TextView、雪花 overlay FrameLayout）
        setContentView(R.layout.activity_weather_snow)

        val snowman = findViewById<ImageView>(R.id.img_snowman)
        val quote = findViewById<TextView>(R.id.txt_quote)

        // ✅ 雪花叠加层：动态雪花都会添加到这里（全屏覆盖，可飘过语录条）
        val overlay = findViewById<FrameLayout>(R.id.snow_overlay)

        // 设置雪人背景图
        snowman.setImageResource(R.drawable.weather_widget_snowman)

        // ------- 语录条：淡入显示 -------
        val isFirstToday = intent.getBooleanExtra("is_first_today", false)
        val raw = AppSettings.getWeatherCacheJson(this)

        quote.text = WeatherUiText.pickQuote(
            rawJson = raw,
            preferTomorrowWeather = false,
            isFirstToday = isFirstToday
        )

        // 文案淡入，避免突然出现
        ObjectAnimator.ofFloat(quote, View.ALPHA, 0f, 1f).apply {
            duration = 220
        }.start()

        // ------- 生成雪花 -------
        // overlay 需要等 layout 完成（有 width/height）才能正确随机坐标
        overlay.post { spawnFlakes(overlay) }

        // 到点关闭：该 Activity 只做短动画展示
        handler.postDelayed({
            finish()
            overridePendingTransition(0, 0)
        }, totalMs)

        // 进场也不做转场动画，更像浮层弹出
        overridePendingTransition(0, 0)
    }

    /**
     * 在 overlay 上创建一批雪花并启动各自的飘落循环
     */
    private fun spawnFlakes(root: FrameLayout) {
        val w = root.width
        val h = root.height
        if (w <= 0 || h <= 0) return

        // 随机雪花数量（每次进来都不一样）
        val count = Random.nextInt(minFlakes, maxFlakes + 1)

        repeat(count) {
            // 新建一个雪花 ImageView
            val flake = ImageView(this).apply {
                setImageResource(snowDrawables.random())
                // 初始透明度随机，营造远近层次
                alpha = Random.nextFloat().coerceIn(0.45f, 0.85f)
                contentDescription = null
            }

            // 雪花大小随机（dp -> px）
            val sizeDp = Random.nextInt(18, 42)
            val sizePx = dp(sizeDp)
            flake.layoutParams = FrameLayout.LayoutParams(sizePx, sizePx)

            // 添加到 overlay：覆盖全屏
            root.addView(flake)

            // 初始位置：先给个合理值，真正的循环会在 restartOne() 里重设
            flake.translationX = Random.nextInt(0, max(1, w - sizePx)).toFloat()
            // Y 从屏幕上方一点点开始，避免一开始就集中在某个区域
            flake.translationY = (-Random.nextInt(dp(20), dp(220))).toFloat()

            // 启动该雪花的循环飘落动画
            startFallingLoop(root, flake)
        }
    }

    /**
     * 让单个雪花进入“循环飘落”：
     * - 每次执行 restartOne() 都会随机一个起点/终点/时长/透明度变化
     * - 动画结束后再次调用 restartOne()，形成持续飘落的效果
     */
    private fun startFallingLoop(root: FrameLayout, flake: ImageView) {
        val w = root.width
        val h = root.height
        val size = flake.layoutParams.width

        fun restartOne() {
            // Activity 退出/销毁后不要再启动新动画
            if (isFinishing || isDestroyedCompat()) return

            // X：随机一条落线（确保不超出右边界）
            val startX = Random.nextInt(0, max(1, w - size)).toFloat()

            // ✅ 短距离慢飘：
            // startY：随机从屏幕范围内开始（不是统一从顶端开始）
            val startY = Random.nextInt(0, max(1, h - size)).toFloat()
            // deltaY：一次只飘一段距离，结束后再随机下一段
            val deltaY = Random.nextInt(dp(120), dp(260)).toFloat()
            // endY：限制到屏幕底部附近，避免超出太多
            val maxY = (h + dp(10)).toFloat()
            val endY = (startY + deltaY).coerceAtMost(maxY)

            // 重设初始位置
            flake.translationX = startX
            flake.translationY = startY

            // 透明度也随机：a0 -> a1（更有层次）
            val a0 = Random.nextFloat().coerceIn(0.55f, 0.9f)
            val a1 = Random.nextFloat().coerceIn(0.15f, 0.45f)
            flake.alpha = a0

            // 随机时长、随机延时：让雪花不那么“同步”
            val dur = Random.nextLong(1400L, 2600L)
            val delay = Random.nextLong(0L, 500L)

            // 下落动画：translationY 从 startY 到 endY
            val fall = ObjectAnimator.ofFloat(flake, View.TRANSLATION_Y, startY, endY).apply {
                duration = dur
                startDelay = delay
                interpolator = LinearInterpolator()
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        // 一段飘完 -> 立刻重开下一段，形成循环
                        restartOne()
                    }
                })
            }

            // 同步做一个渐隐：配合下落更自然
            val fade = ObjectAnimator.ofFloat(flake, View.ALPHA, a0, a1).apply {
                duration = dur
                startDelay = delay
                interpolator = LinearInterpolator()
            }

            // 记录 animator，便于销毁时 cancel
            runningAnimators += fall
            runningAnimators += fade

            fall.start()
            fade.start()
        }

        // 启动第一段飘落
        restartOne()
    }

    /**
     * dp 转 px：让动画参数/尺寸在不同分辨率下视觉一致
     */
    private fun dp(v: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            v.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    /**
     * 兼容判断：API >= 17 才有 isDestroyed
     */
    private fun isDestroyedCompat(): Boolean {
        return Build.VERSION.SDK_INT >= 17 && isDestroyed
    }

    override fun onDestroy() {
        // ✅ 先停掉所有动画：避免回调继续触发 restartOne()
        runningAnimators.forEach { it.cancel() }
        runningAnimators.clear()

        // 清掉延时 finish 的任务
        handler.removeCallbacksAndMessages(null)

        // ✅ 移除 overlay 里动态添加的雪花 View（避免残留引用）
        val overlay = findViewById<ViewGroup?>(R.id.snow_overlay)
        overlay?.removeAllViews()

        super.onDestroy()
    }
}
