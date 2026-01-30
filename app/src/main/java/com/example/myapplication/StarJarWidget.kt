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
 * StarJarWidget（星星瓶桌面小组件）
 *
 * 功能概述：
 * - 点击小组件后随机抽取一条“祝福语”，显示在小组件上
 * - 抽取结果会写入 SharedPreferences（持久化），重启手机/桌面后仍可显示
 * - 如果“动画显示”开关开启，会弹出 StarAnimActivity 播放动画
 *
 * 关键点：
 * - 和 FortuneBambooWidget 共用同一个 prefs 文件（fortune_widget_prefs），但使用自己的 KEY
 * - “点击计数”是全局共用的（竹筒 + 星星瓶共用一个计数器），用于彩蛋触发
 */
class StarJarWidget : AppWidgetProvider() {

    companion object {

        /**
         * 点击小组件触发的广播 Action（只处理这个 action）
         * - 在 onReceive 里判断 intent.action 是否等于它
         */
        private const val ACTION_TAP = "com.example.myapplication.STAR_TAP"

        /**
         * 星星瓶的祝福语池（随机抽取）
         * - 你可以随意增删改
         * - BLESSINGS.random() 会从这里随机选一条
         */
        private val BLESSINGS = arrayOf(
            // 星光治愈系
            "星光落满怀", "星河皆温柔", "岁岁有星光", "星途皆坦荡",
            "晚风揽星河", "星河藏温柔", "星光照前路", "心有星河暖",
            // 日常小美好系
            "日常有星光", "日子闪着光", "温柔漫山海", "欢喜藏心底",
            "美好正发生", "温柔待日常", "生活有回甘", "小美好常在",
            // 轻甜治愈系
            "今天超闪耀", "快乐值满星", "好运亮晶晶", "心动有回音",
            "温柔又坚定", "万事皆明朗", "步履皆生光", "温柔抵岁月",
            // 诗意氛围感
            "星芒照归途", "山海皆可平", "清风绕星辰", "人间小圆满",
            "朝暮有清欢", "岁岁见星河", "温柔赴山海", "星光不负赶路人"
        )

        /**
         * SharedPreferences 文件名
         * - 这里与竹筒 FortuneBambooWidget 共用（便于统一管理）
         */
        private const val PREF_NAME = "fortune_widget_prefs"

        /**
         * 星星瓶专属的持久化 Key：
         * - KEY_DRAWN：是否已经抽过（决定显示“空瓶子”还是“已抽签状态”）
         * - KEY_TEXT：当前显示的祝福语文本
         */
        private const val KEY_TEXT = "star_text"
        private const val KEY_DRAWN = "star_drawn"

        // ---------------------------
        // ✅ 公开方法：给外部（设置页/复原按钮等）调用
        // ---------------------------

        /**
         * 强制刷新所有 StarJarWidget（所有实例）
         * - 桌面可以放多个同款 widget，所以要遍历 appWidgetIds
         */
        fun forceUpdateAll(context: Context) = updateAllWidgets(context)

        /**
         * 仅复原星星瓶小组件状态（不动竹筒、不动其他功能）
         * - 清掉抽取状态和文本，然后立刻刷新桌面显示
         */
        fun resetWidgetStateOnly(context: Context) {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean(KEY_DRAWN, false)
                .putString(KEY_TEXT, "")
                .apply()
            updateAllWidgets(context)
        }

        // ---------------------------
        // 内部刷新逻辑
        // ---------------------------

        /**
         * 刷新桌面上所有星星瓶小组件实例
         */
        private fun updateAllWidgets(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, StarJarWidget::class.java))
            ids.forEach { updateOneWidget(context, mgr, it) }
        }

        /**
         * 刷新单个小组件实例（id 对应桌面上的某一个 widget）
         * - 根据 prefs 里是否 drawn 决定：
         *   - 未抽：显示 idle 图，隐藏文字
         *   - 已抽：显示 drawn 图，显示文字
         * - 同时绑定点击事件（PendingIntent -> 广播回自己）
         */
        private fun updateOneWidget(context: Context, mgr: AppWidgetManager, id: Int) {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val drawn = prefs.getBoolean(KEY_DRAWN, false)
            val text = prefs.getString(KEY_TEXT, "") ?: ""

            // RemoteViews：AppWidget 必须用 RemoteViews 来更新 UI（不能直接 findViewById）
            val views = RemoteViews(context.packageName, R.layout.widget_star_jar)

            if (drawn) {
                // 已抽取：显示“亮瓶子 + 文本”
                views.setImageViewResource(R.id.img_jar, R.drawable.star_widget_jar_drawn)
                views.setTextViewText(R.id.txt_bless, text)
                views.setViewVisibility(R.id.txt_bless, View.VISIBLE)
            } else {
                // 未抽取：显示“空瓶子”，隐藏文本
                views.setImageViewResource(R.id.img_jar, R.drawable.star_widget_jar_idle)
                views.setViewVisibility(R.id.txt_bless, View.GONE)
            }

            /**
             * 绑定点击事件：
             * - ACTION_TAP：点击时 onReceive 才会处理
             * - putExtra(EXTRA_APPWIDGET_ID)：告诉接收方是哪个 widget 被点了（有时调试有用）
             * - data = Uri.parse("star://tap/$id")：用 id 唯一化 intent，避免 PendingIntent 被系统复用
             */
            val tap = Intent(context, StarJarWidget::class.java).apply {
                action = ACTION_TAP
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                data = Uri.parse("star://tap/$id")
            }

            /**
             * PendingIntent.getBroadcast：
             * - 让桌面点击小组件时发送广播回 StarJarWidget
             * - FLAG_UPDATE_CURRENT：如果已存在同 requestCode 的 PendingIntent，更新其 extras/data
             * - FLAG_IMMUTABLE：Android 12+ 推荐，表示 intent 内容不可被外部修改
             */
            val pi = PendingIntent.getBroadcast(
                context,
                id, // requestCode 也用 id，进一步避免多个 widget 冲突
                tap,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 点击 widget_root 时触发 pi
            views.setOnClickPendingIntent(R.id.widget_root, pi)

            // 更新该 widget
            mgr.updateAppWidget(id, views)
        }

        // ---------------------------
        // 点击计数（全局共享）与彩蛋逻辑
        // ---------------------------

        /**
         * 共用计数器（竹筒 + 星星瓶共用）
         * - 用 AppSettings.incClickCount 统一维护
         */
        private fun incSharedClickCount(context: Context): Int {
            return AppSettings.incClickCount(context)
        }

        /**
         * 彩蛋文案：达到特定点击次数时返回彩蛋字符串
         * - 返回 null 表示不触发彩蛋，走随机祝福语
         */
        private fun easterEggText(clickCount: Int): String? {
            return when (clickCount) {
                99 -> "友谊天长地久" // 这里可以继续加更多彩蛋
                // 520 -> "爱你❤"
                // 521 -> "爱你❤"
                // 1314 -> "一生一世我爱你"
                else -> null
            }
        }
    }

    /**
     * 系统定期/桌面触发的刷新回调
     * - 例如：添加到桌面、尺寸改变、系统要求更新等
     * - 这里直接按现有 prefs 状态刷新 UI
     */
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateOneWidget(context, appWidgetManager, it) }
    }

    /**
     * 接收广播
     * - 我们只处理 ACTION_TAP（点击事件）
     * - 其他 action 交给父类处理
     */
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_TAP) return

        // ① 共用计数器 + 彩蛋/随机
        val count = incSharedClickCount(context)
        val pickedText = easterEggText(count) ?: BLESSINGS.random()

        // ② 保存星星瓶自己的抽取结果（持久化：用于 widget 显示和重启后恢复）
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_DRAWN, true)
            .putString(KEY_TEXT, pickedText)
            .apply()

        // ③ 立刻刷新桌面 UI（确保点击后马上看到文字）
        updateAllWidgets(context)

        // ④ 如果设置里开启了“动画显示”，则弹出动画 Activity
        if (AppSettings.isAnimEnabled(context)) {
            val anim = Intent(context, StarAnimActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("blessing_text", pickedText)
            }
            context.startActivity(anim)
        }
    }
}
