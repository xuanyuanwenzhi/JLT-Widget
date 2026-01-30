package com.example.myapplication

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowInsets
import android.widget.ImageView
import android.widget.TextView

/**
 * FortuneAnimActivity
 * - “抽签/竹筒”小组件点击后弹出的短动画页（透明背景）
 * - 作用：播放纸卷展开动画 + 显示抽到的签文，然后自动关闭
 *
 * 设计思路：
 * - 使用透明 Activity 叠在桌面之上（theme 一般是 Transparent）
 * - 动画总时长固定 TOTAL_MS，结束后 finish() 返回
 * - 文案优先从 Intent 传入，其次从 SharedPreferences 兜底读取
 */
class FortuneAnimActivity : Activity() {

    /**
     * 主线程 Handler：用来做 “延时关闭页面”
     * 注意：onDestroy() 里必须 removeCallbacks，避免 Activity 已销毁仍回调导致泄漏/异常
     */
    private val handler = Handler(Looper.getMainLooper())

    /**
     * 动画页面停留总时长（毫秒）
     * - 动画播放完后再稍等一会儿就自动关闭
     * - 如果觉得太短/太长，调这个值即可
     */
    private val TOTAL_MS = 2800L

    /**
     * 把普通横排文本转成“竖排显示”
     * - 这里做法很简单：把每个字符用 '\n' 分隔
     * - 同时去掉原始文本中的换行，避免出现空行/排版怪
     *
     * 注意：
     * - 这种竖排不是真正的排版引擎竖排（不处理标点旋转等），但胜在简单好用
     */
    private fun toVertical(text: String): String {
        val cleaned = text.replace("\n", "").trim()
        if (cleaned.isEmpty()) return ""
        return cleaned.toCharArray().joinToString("\n")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /**
         * 让窗口背景透明：
         * - 这里是硬设置背景透明，配合 Transparent Theme，确保动画像“浮在桌面上”
         */
        window.decorView.setBackgroundColor(Color.TRANSPARENT)

        /**
         * 让内容可以绘制到系统栏（状态栏/导航栏）下面：
         * - Android 11（API 30）及以上：用 setDecorFitsSystemWindows(false)
         * - 旧版本：用 systemUiVisibility 的 LAYOUT_FULLSCREEN
         *
         * 你之前提过“不想出现全屏提示/沉浸提示”，所以这里没有 hide statusBars
         * 只是让布局延伸到系统栏区域，看起来更贴边、更像浮层动画
         */
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            // 不隐藏状态栏，只让内容铺到系统栏下面即可
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }

        /**
         * 加载动画布局：
         * - img_scroll_mid：中间卷轴（起始状态）
         * - img_scroll_open：展开卷轴（后续出现）
         * - txt_fortune_on_paper：签文文字
         */
        setContentView(R.layout.activity_fortune_anim)

        val mid = findViewById<ImageView>(R.id.img_scroll_mid)
        val open = findViewById<ImageView>(R.id.img_scroll_open)
        val tv = findViewById<TextView>(R.id.txt_fortune_on_paper)

        /**
         * 签文来源（优先级）：
         * 1) Intent 里传进来的 "fortune_text"（小组件/主界面点击时可以塞）
         * 2) SharedPreferences 里保存的 "fortune_text"（兜底，防止 Intent 没传）
         *
         * 说明：
         * - PREF_NAME 和 key 要和 AppSettings / 其他地方保持一致
         * - 这里直接用字符串写死，属于“快速实现”，后续可统一为常量
         */
        val fromIntent = intent.getStringExtra("fortune_text")
        val prefs = getSharedPreferences("fortune_widget_prefs", MODE_PRIVATE)
        val fromPrefs = prefs.getString("fortune_text", "") ?: ""
        val text = (fromIntent ?: fromPrefs)

        /**
         * 在 Activity 中显示竖排签文
         * - 这样更符合“纸卷展开 + 竖排字”的风格
         */
        tv.text = toVertical(text)

        /**
         * 设置初始状态（非常关键）：
         * - open 初始透明且稍微上移（等待“出现+下落”）
         * - 文本初始透明（等待最后渐显）
         */
        open.alpha = 0f
        open.translationY = -120f
        tv.alpha = 0f

        // ---------------- 动画分三段：step1 -> step2 -> step3 ----------------

        /**
         * step1：中间纸卷上移 + 渐隐
         * - 视觉上像“旧卷轴往上收走”
         */
        val midUp = ObjectAnimator.ofFloat(mid, View.TRANSLATION_Y, 0f, -140f)
        val midFade = ObjectAnimator.ofFloat(mid, View.ALPHA, 1f, 0f)
        val step1 = AnimatorSet().apply {
            playTogether(midUp, midFade)
            duration = 650
        }

        /**
         * step2：展开卷轴出现 + 下落到位
         * - open 从透明变不透明
         * - 同时从上方 (-120f) 掉回原位 (0f)
         */
        val openFadeIn = ObjectAnimator.ofFloat(open, View.ALPHA, 0f, 1f)
        val openDown = ObjectAnimator.ofFloat(open, View.TRANSLATION_Y, -120f, 0f)
        val step2 = AnimatorSet().apply {
            playTogether(openFadeIn, openDown)
            duration = 850
        }

        /**
         * step3：文字渐显
         * - 等 open 落到位后，文字再出现更自然
         * - startDelay 是为了留一点“落地”缓冲
         */
        val textFade = ObjectAnimator.ofFloat(tv, View.ALPHA, 0f, 1f)
        val step3 = AnimatorSet().apply {
            playTogether(textFade)
            duration = 520
            startDelay = 120
        }

        /**
         * 顺序播放：step1 -> step2 -> step3
         */
        AnimatorSet().apply {
            playSequentially(step1, step2, step3)
            start()
        }

        /**
         * 延迟关闭页面：
         * - TOTAL_MS 到点后自动 finish()
         * - overridePendingTransition(0,0) 保证进出都“无切换动画”，像弹层消失
         */
        handler.postDelayed({
            finish()
            overridePendingTransition(0, 0)
        }, TOTAL_MS)

        // 进入页面时也禁用切换动画（更像浮层）
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        // 清理延迟任务，避免内存泄漏或 Activity 销毁后仍回调
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
