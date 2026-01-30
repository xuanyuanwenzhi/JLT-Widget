package com.example.myapplication

import android.app.Activity
import android.os.Bundle
import android.os.SystemClock
import android.widget.Button
import android.widget.Toast

/**
 * ResetActivity（复原页 / 旧版复原逻辑）
 *
 * 这是一个传统 View（XML）页面：layout = activity_reset
 * 页面里通常只有一个按钮（btn_reset），点击后执行两件事：
 *
 * 1) 普通功能：复原小组件“抽签状态”
 *    - 把 FortuneBambooWidget 从“已抽签状态”重置回“未抽签状态”
 *    - 不会清空点击计数（click count）
 *
 * 2) 隐藏功能：10 秒内连续点击 10 次 -> 清空点击计数
 *    - 用 SystemClock.elapsedRealtime() 来计时（不会受系统时间被改影响，更稳定）
 *    - 超过 10 秒则重新开始计数窗口
 *
 * 说明：
 * - 你现在的 MainActivity 里已经有一个“复原按钮 + 1 秒内连点清计数”的版本
 * - 这个 ResetActivity 更像是“旧实现”或“备用实现”，是否保留取决于你最终产品设计
 */



/**
 *
 *
 * ￥￥￥￥￥￥￥￥￥￥￥￥$$$$$$$$$$$&&&&&&&&&&&&&&&&（这一行是为了醒目）
 *
 *
 * 我看了一下gpt加的注释，那应该是，我忘了删除了，那我也懒得删除了，你们想看就看一下，没必要，反正用不到了
 * 就是说这个代码文件是用不着了，没用了
 * 加固混淆用，其实我是开源，也没混淆
 *
 * ￥￥￥￥￥￥￥￥￥￥￥￥$$$$$$$$$$$&&&&&&&&&&&&&&&&（这一行是为了醒目）
 *
 *
 */




class ResetActivity : Activity() {

    /**
     * armedStartMs：
     * - 隐藏连点窗口的开始时间（elapsedRealtime）
     * - 0 表示“当前未开启窗口”
     */
    private var armedStartMs: Long = 0L

    /**
     * taps：
     * - 当前 10 秒窗口内的点击次数
     */
    private var taps: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 使用 XML 布局（传统 View 页面）
        setContentView(R.layout.activity_reset)

        // 复原按钮
        val btn = findViewById<Button>(R.id.btn_reset)

        btn.setOnClickListener {

            // -------------------------
            // 1) 普通功能：每次点击都复原抽签状态（不清计数）
            // -------------------------
            // resetWidgetStateOnly() 会：
            // - 把 KEY_DRAWN 置为 false
            // - 清空 KEY_TEXT
            // - 并刷新小组件 UI
            FortuneBambooWidget.resetWidgetStateOnly(this)

            // -------------------------
            // 2) 隐藏功能：10 秒内点够 10 次 -> 清空点击计数
            // -------------------------
            // elapsedRealtime：从开机到现在的毫秒数，适合做“时间间隔”的判断
            val now = SystemClock.elapsedRealtime()

            // 如果：
            // - 还没开始计时（armedStartMs == 0）
            // - 或者已经超过 10 秒
            // 就重新开启一个 10 秒窗口，并把 taps 重置为 1
            if (armedStartMs == 0L || now - armedStartMs > 10_000) {
                armedStartMs = now
                taps = 1

                // 提示：还需要再点 9 次（总共 10 次）就能清空计数
                Toast.makeText(this, "复原完成（10秒内再点9次可清空计数）", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 仍在 10 秒窗口内：累加点击次数
            taps += 1

            // 剩余次数（到 10 次为止）
            val left = 10 - taps

            if (taps >= 10) {
                // 达到阈值：清空点击计数，并复位窗口状态
                FortuneBambooWidget.clearClickCount(this)
                armedStartMs = 0L
                taps = 0
                Toast.makeText(this, "点击计数已清空", Toast.LENGTH_SHORT).show()
            } else {
                // 未达到阈值：提示还需要点几次
                Toast.makeText(this, "再点 $left 次可清空计数", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
