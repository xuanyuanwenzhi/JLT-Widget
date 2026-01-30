package com.example.myapplication

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
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
 * StarAnimActivity（星星瓶点击后的动画页面）
 *
 * 设计目标：
 * - Activity 全透明背景 + 覆盖在桌面上（像弹出小动画）
 * - 播放完成后自动 finish，不在最近任务里留痕迹（manifest 里已配置）
 *
 * 动画流程大概是：
 * 1) 瓶子弹跳（上 -> 下 -> 回到原位）
 * 2) 弹跳结束瞬间：瓶子从 3 星切换成 2 星，同时一颗星旋转并渐隐
 * 3) 拖尾从右侧飞入到中间（文字也跟随一起移动）
 * 4) 文字淡入
 * 5) 停留 HOLD_MS 后关闭页面
 *
 * 注意：
 * - 这里同时做了两种“自动关闭兜底”：
 *   A) TOTAL_MS 的 handler.postDelayed（防止动画链条异常卡住）
 *   B) 动画链末尾再 handler.postDelayed(HOLD_MS) 关闭（正常路径）
 */
class StarAnimActivity : Activity() {

    /**
     * 主线程 handler：用于定时 finish()
     * - 动画结束后的停留（HOLD_MS）
     * - 总时长兜底关闭（TOTAL_MS）
     */
    private val handler = Handler(Looper.getMainLooper())

    // ========== 关键时间参数（毫秒） ==========
    // 你要的三段弹跳时间：上升、下落、回弹
    private val UP_MS = 300L
    private val DOWN_MS = 450L
    private val BACK_MS = 150L

    // 后续整体放慢（星旋转渐隐、拖尾飞入、文字淡入、最后停留）
    private val STAR_SPIN_FADE_MS = 650L
    private val TRAIL_IN_MS = 900L
    private val TEXT_FADE_MS = 400L

    /**
     * ✅ 最后停留时间
     * - 当前 800ms（0.8 秒）
     */
    private val HOLD_MS = 800L

    /**
     * 总时长兜底 finish（防止异常卡住）
     * - 把所有动画时间都加起来，再 +300ms 作为缓冲
     * - 即使动画监听没触发，也会在 TOTAL_MS 后强制关闭
     */
    private val TOTAL_MS =
        UP_MS + DOWN_MS + BACK_MS + STAR_SPIN_FADE_MS + TRAIL_IN_MS + TEXT_FADE_MS + HOLD_MS + 300L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 透明背景（让 Activity 像浮在桌面上）
        window.decorView.setBackgroundColor(Color.TRANSPARENT)

        // 不隐藏状态栏（避免部分系统弹“全屏提示/Go it”之类的干扰）
        // 这里只是让内容可以画到系统栏下面（沉浸式布局），但不是真正隐藏状态栏
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }

        // 绑定 XML 布局
        setContentView(R.layout.activity_star_anim)

        // 根容器：用于获取屏幕宽度，从右侧飞入需要用到 root.width
        val root = findViewById<View>(R.id.star_root)

        // 主要控件：
        val jar = findViewById<ImageView>(R.id.img_star_jar)                 // 瓶子（先 3 星 -> 再 2 星）
        val star = findViewById<ImageView>(R.id.img_fly_star)                // 旋转渐隐的星星
        val trail = findViewById<ImageView>(R.id.img_star_trail)             // 拖尾（从右飞入）
        val tv = findViewById<TextView>(R.id.txt_blessing_on_trail)          // 正文
        val tvShadow = findViewById<TextView>(R.id.txt_blessing_shadow)      // 阴影层（增强可读性）

        // 文案来源：
        // - 优先 Intent 传入（点击时传来的）
        // - 否则 fallback 到 SharedPreferences 的 star_text
        val fromIntent = intent.getStringExtra("blessing_text")
        val prefs = getSharedPreferences("fortune_widget_prefs", MODE_PRIVATE)
        val fromPrefs = prefs.getString("star_text", "") ?: ""
        val text = (fromIntent ?: fromPrefs)

        // 两层文字都显示相同内容：一层正文，一层阴影
        tv.text = text
        tvShadow.text = text

        // ---------- 初始状态（避免“闪一下”） ----------
        // 初始瓶子为 3 星
        jar.setImageResource(R.drawable.star_anim_jar_3stars)

        // 星星一开始隐藏（透明）
        star.alpha = 0f
        star.rotation = 0f

        // 拖尾和文字一开始都隐藏
        trail.alpha = 0f
        tv.alpha = 0f
        tvShadow.alpha = 0f

        /**
         * 等布局测量完再开始：
         * - root.width 在 onCreate 时可能还是 0
         * - 用 root.post 保证 view 已经有尺寸
         */
        root.post {
            // 拖尾最终位置：XML 已经居中，所以 final translationX=0
            // 起始位置：从屏幕右侧外开始（0.6f 越大起点越远）
            val startX = root.width.toFloat() * 0.6f

            // 拖尾 + 两层文字都从右侧 startX 起步，随后一起飞到 0
            trail.translationX = startX
            tv.translationX = startX
            tvShadow.translationX = startX

            // 开始动画主流程
            startAnim(jar, star, trail, tv, tvShadow)
        }

        // ========== 兜底关闭：防止异常卡住 ==========
        handler.postDelayed({
            finish()
            overridePendingTransition(0, 0)
        }, TOTAL_MS)

        // 关闭转场动画（避免闪屏/黑屏）
        overridePendingTransition(0, 0)
    }

    /**
     * 主动画流程
     * - 通过 AnimatorListener 把几个阶段串起来
     */
    private fun startAnim(
        jar: ImageView,
        star: ImageView,
        trail: ImageView,
        tv: TextView,
        tvShadow: TextView
    ) {
        /**
         * 弹跳幅度（单位：px）
         * - dyUp：向上抬起
         * - dyDown：回弹到略低于原位
         */
        val dyUp = -32f
        val dyDown = 15f

        // 1) 瓶子上下弹跳：上 -> 下 -> 归位
        val jarUp = ObjectAnimator.ofFloat(jar, View.TRANSLATION_Y, 0f, dyUp).apply { duration = UP_MS }
        val jarDown = ObjectAnimator.ofFloat(jar, View.TRANSLATION_Y, dyUp, dyDown).apply { duration = DOWN_MS }
        val jarBack = ObjectAnimator.ofFloat(jar, View.TRANSLATION_Y, dyDown, 0f).apply { duration = BACK_MS }

        val bounce = AnimatorSet().apply {
            playSequentially(jarUp, jarDown, jarBack)
        }

        // 2) 弹跳结束：星星出现 1 帧，然后旋转 60° 并渐隐
        val starShow = ObjectAnimator.ofFloat(star, View.ALPHA, 0f, 1f).apply { duration = 1L }
        val starRotate = ObjectAnimator.ofFloat(star, View.ROTATION, 0f, 60f).apply { duration = STAR_SPIN_FADE_MS }
        val starFade = ObjectAnimator.ofFloat(star, View.ALPHA, 1f, 0f).apply { duration = STAR_SPIN_FADE_MS }

        val starStep = AnimatorSet().apply {
            playTogether(starRotate, starFade)
        }

        // 3) 拖尾从右飞入到中间（并同时淡入）
        val trailFadeIn = ObjectAnimator.ofFloat(trail, View.ALPHA, 0f, 1f).apply { duration = 1L }
        val trailMove = ObjectAnimator.ofFloat(trail, View.TRANSLATION_X, trail.translationX, 0f).apply { duration = TRAIL_IN_MS }

        // 两层文字跟随拖尾一起移动
        val textMove = ObjectAnimator.ofFloat(tv, View.TRANSLATION_X, tv.translationX, 0f).apply { duration = TRAIL_IN_MS }
        val shadowMove = ObjectAnimator.ofFloat(tvShadow, View.TRANSLATION_X, tvShadow.translationX, 0f).apply { duration = TRAIL_IN_MS }

        val trailStep = AnimatorSet().apply {
            playTogether(trailFadeIn, trailMove, textMove, shadowMove)
        }

        // 4) 拖尾到位后：文字两层一起淡入（提高可读性）
        val textFadeIn = ObjectAnimator.ofFloat(tv, View.ALPHA, 0f, 1f).apply { duration = TEXT_FADE_MS }
        val shadowFadeIn = ObjectAnimator.ofFloat(tvShadow, View.ALPHA, 0f, 1f).apply { duration = TEXT_FADE_MS }

        // ========== 串联逻辑（监听结束 -> 开始下一段） ==========
        bounce.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                // 弹跳结束时切换瓶子图片：3 星 -> 2 星
                jar.setImageResource(R.drawable.star_anim_jar_2stars)

                // 星星准备出现并做旋转渐隐
                star.alpha = 1f
                starShow.start()
                starStep.start()
            }
        })

        starStep.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                // 星星渐隐结束后：开始拖尾飞入
                trailStep.start()

                // 拖尾飞入结束后：文字淡入 + 停留后关闭页面
                trailStep.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        // 文字两层一起淡入
                        AnimatorSet().apply {
                            playTogether(textFadeIn, shadowFadeIn)
                            start()
                        }

                        // ✅ 最后停留一会再关（你觉得长就改 HOLD_MS）
                        handler.postDelayed({
                            finish()
                            overridePendingTransition(0, 0)
                        }, HOLD_MS)
                    }
                })
            }
        })

        // 启动第一段：弹跳
        bounce.start()
    }

    override fun onDestroy() {
        // 清掉所有延时任务，避免 Activity 已销毁仍回调导致异常
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
