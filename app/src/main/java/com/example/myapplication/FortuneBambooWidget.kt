package com.example.myapplication

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews

/**
 * FortuneBambooWidget（竹筒抽签小组件）
 *
 * 这个类继承 AppWidgetProvider，用来处理：
 * 1) 桌面小组件的刷新（onUpdate）
 * 2) 用户点击小组件后的逻辑（onReceive -> ACTION_TAP）
 *
 * 核心逻辑：
 * - 第一次点击：随机抽取一条签文，保存到 SharedPreferences，并显示在小组件上
 * - 后续刷新：从 SharedPreferences 读出状态，决定显示“未抽签”还是“已抽签”
 * - 可选：如果动画开关开启，点击后会跳转到 FortuneAnimActivity 播放动画
 */
class FortuneBambooWidget : AppWidgetProvider() {

    companion object {
        /**
         * 小组件点击时发送的广播 action
         * 注意：必须和 Manifest / PendingIntent 的 action 一致
         */
        private const val ACTION_TAP = "com.example.myapplication.FORTUNE_TAP"

        // ===== 签文库（你自己随便改/加）=====
        /**
         * 可抽取的签文列表（用于随机抽签）
         * 说明：
         * - 这里只是一个静态数组，random() 会从中随机挑选
         * - 你可以继续添加，或做分类，或按节日替换等
         */
        private val FORTUNES = arrayOf(
            // 原有经典款（去重+控7字内）
            "平安喜乐", "万事顺意", "好运常在", "得偿所愿", "岁岁无忧",
            "今日宜笑", "暖意盈怀", "顺遂无虞", "日日有欢喜", "平安伴朝夕",
            "好运落肩头", "万事皆胜意", "岁岁皆安康", "今朝多顺遂", "人间好时节",
            "温风拂日常", "所遇皆温柔", "日子温柔又闪亮", "万事皆可期可盼", "三餐四季皆安暖", "岁岁年年常欢愉",

            // 新增「上上签/好运」主题（无重复）
            "抽中上上签", "今日有好运", "好运正敲门", "今日运超旺",
            "签签皆好运", "好运随身行", "今日好运气", "好运来敲门", "上上签在手",

            // 新增「顺遂/如意」主题（无重复）
            "日日皆顺遂", "万事皆如意", "今日事事顺", "顺意伴朝夕", "处处皆顺遂",
            "心顺事也顺", "今日无烦忧", "万事皆顺意", "顺遂伴今日", "如意绕心头",

            // 新增「欢喜/温暖」主题（无重复）
            "欢喜伴今日", "暖意绕心怀", "今日多暖意", "温柔满今日",
            "欢喜落肩头", "今日有温风", "心暖万事甜", "今日多温柔", "暖意随身行",

            // 新增「安康/常乐」主题（无重复）
            "今日多安康", "常乐伴今日", "安康随身行",
            "日日皆安康", "今日心常乐", "常乐绕心头", "安康伴今日", "心安乐也安",

            // 新增「财运」主题
            "今日财运旺", "财气随身来", "小财常进门", "财运节节高",
            "今日财气满", "财来皆顺利", "口袋添新财",

            // 新增「学业」主题
            "今日学业顺", "思路皆清晰", "题题皆顺手", "学业步步升",
            "今日脑灵光", "学业皆顺遂", "好好学习天天向上"
        )

        // ===== widget 持久化（和 AppSettings 用同一个 prefs）=====
        /**
         * 小组件本地存储的 SharedPreferences 名称
         * 说明：
         * - 你这里用的是同一个 PREF_NAME（fortune_widget_prefs）
         * - AppSettings 也在用这个 prefs，所以能共享数据
         */
        private const val PREF_NAME = "fortune_widget_prefs"

        /**
         * 已抽到的签文文本（显示在小组件上）
         */
        private const val KEY_TEXT = "fortune_text"

        /**
         * 是否已经抽过签（控制小组件显示状态）
         * - false：显示“未抽签”的竹筒图，不显示文字
         * - true ：显示“已抽签”的竹筒图，并显示签文文字
         */
        private const val KEY_DRAWN = "fortune_drawn"

        // ---------------------------
        // ✅ 给外部（设置页）调用的公开方法
        // ---------------------------

        /**
         * 强制刷新所有该小组件实例
         * 常见用途：
         * - 设置页改完选项后，想立刻刷新桌面显示
         */
        fun forceUpdateAll(context: Context) {
            updateAllWidgets(context)
        }

        /**
         * 仅重置“抽签状态”，不影响别的设置
         * - 把 drawn 设回 false
         * - 清空签文
         * - 然后刷新小组件
         */
        fun resetWidgetStateOnly(context: Context) {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean(KEY_DRAWN, false)
                .putString(KEY_TEXT, "")
                .apply()
            updateAllWidgets(context)
        }

        /**
         * 清空点击次数计数器（共用计数器在 AppSettings 里）
         */
        fun clearClickCount(context: Context) {
            // ✅ 统一用 AppSettings 的 key
            AppSettings.clearClickCount(context)
        }

        /**
         * 获取当前点击次数（用于彩蛋或调试显示）
         */
        fun getClickCount(context: Context): Int {
            return AppSettings.prefs(context).getInt(AppSettings.KEY_CLICK_COUNT, 0)
        }

        // ---------------------------
        // 内部更新逻辑（刷新小组件 UI）
        // ---------------------------

        /**
         * 刷新所有实例（一个桌面可能放多个同款 widget）
         */
        private fun updateAllWidgets(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, FortuneBambooWidget::class.java))
            ids.forEach { updateOneWidget(context, mgr, it) }
        }

        /**
         * 刷新某一个 widget 实例的 UI
         *
         * 这里会：
         * 1) 从 prefs 读取是否已抽签、抽到的文本
         * 2) 根据 drawn 状态切换图片/文字显示
         * 3) 给 widget_root 绑定点击事件（PendingIntent 广播给自己）
         * 4) 调用 mgr.updateAppWidget() 更新界面
         */
        private fun updateOneWidget(context: Context, mgr: AppWidgetManager, id: Int) {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val drawn = prefs.getBoolean(KEY_DRAWN, false)
            val text = prefs.getString(KEY_TEXT, "") ?: ""

            // RemoteViews：用于更新桌面小组件的布局（不能直接用 Compose/View 操作）
            val views = RemoteViews(context.packageName, R.layout.widget_fortune_bamboo)

            // 根据是否抽过签，切换显示
            if (drawn) {
                // 已抽签：换成“抽出状态”的图片，并显示文本
                views.setImageViewResource(R.id.img_bamboo, R.drawable.fortune_bamboo1)
                views.setTextViewText(R.id.txt_fortune, text)
                views.setViewVisibility(R.id.txt_fortune, View.VISIBLE)
            } else {
                // 未抽签：显示默认竹筒图片，隐藏文本
                views.setImageViewResource(R.id.img_bamboo, R.drawable.fortune_bamboo)
                views.setViewVisibility(R.id.txt_fortune, View.GONE)
            }

            // 点击事件：发送广播给 FortuneBambooWidget 自己（onReceive 里处理）
            val tap = Intent(context, FortuneBambooWidget::class.java).apply {
                action = ACTION_TAP
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)

                // data 用于区分不同 widget 的 PendingIntent（避免被系统当成同一个复用）
                data = Uri.parse("fortune://tap/$id")
            }

            // PendingIntent：小组件只能通过 PendingIntent 来触发点击事件
            val pi = PendingIntent.getBroadcast(
                context,
                id, // requestCode 用 widgetId 区分不同实例
                tap,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 把点击事件绑定到根布局
            views.setOnClickPendingIntent(R.id.widget_root, pi)

            // 提交更新
            mgr.updateAppWidget(id, views)
        }

        // ✅ 计数统一用 AppSettings.KEY_CLICK_COUNT
        /**
         * 点击次数 +1（用于彩蛋或统计）
         * 注意：这个计数器是“共用计数器”，也就是和星星瓶等 widget 可能共用同一套计数。
         */
        private fun incClickCount(context: Context): Int {
            val p = AppSettings.prefs(context)
            val now = p.getInt(AppSettings.KEY_CLICK_COUNT, 0) + 1
            p.edit().putInt(AppSettings.KEY_CLICK_COUNT, now).apply()
            return now
        }

        /**
         * 彩蛋文本：
         * - 当点击次数达到某些“特殊数字”时，返回固定文本
         * - 否则返回 null，外层就会走随机签文
         */
        private fun easterEggText(clickCount: Int): String? {
            return when (clickCount) {
                99 -> "友谊天长地久" // 这里可以放一些彩蛋语录
                //    520 -> "爱你❤"
                //    521 -> "爱你❤"
                //    1314 -> "一生一世我爱你"
                else -> null
            }
        }
    }

    /**
     * 系统刷新 widget 时回调（例如：添加到桌面 / 系统定时刷新 / 手动刷新等）
     * 这里的做法：对每个 widgetId 单独 updateOneWidget
     */
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateOneWidget(context, appWidgetManager, it) }
    }

    /**
     * 接收各种广播（包括系统的 APPWIDGET_UPDATE，以及我们自定义的 ACTION_TAP）
     *
     * 我们这里只处理 ACTION_TAP（用户点击小组件）
     * 流程：
     * 1) 点击次数 +1，并判断是否触发彩蛋
     * 2) 写入抽签结果到 prefs（drawn=true + text）
     * 3) 刷新桌面显示
     * 4) 如果动画开关开启，则启动 FortuneAnimActivity 播放动画
     */
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_TAP) return

        // ① 计数 + 彩蛋/随机签文
        val count = incClickCount(context)
        val pickedText = easterEggText(count) ?: FORTUNES.random()

        // ② 保存抽签结果（下次刷新还能显示）
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_DRAWN, true)
            .putString(KEY_TEXT, pickedText)
            .apply()

        // ③ 刷新桌面显示（把新图/新文字更新到 RemoteViews）
        updateAllWidgets(context)

        // ④ 动画开关：开了就跳转到动画 Activity（透明主题的短暂浮层效果）
        if (AppSettings.isAnimEnabled(context)) {
            val anim = Intent(context, FortuneAnimActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("fortune_text", pickedText)
            }
            context.startActivity(anim)
        }
    }
}
