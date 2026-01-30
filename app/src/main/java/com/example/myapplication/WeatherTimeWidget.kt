package com.example.myapplication

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.RemoteViews
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale
import kotlin.random.Random

/**
 * WeatherTimeWidget
 * - 一个“按时间段切图 + 点击弹出对应天气动画 Activity”的桌面小组件
 *
 * 核心目标：
 * 1) 早晨/白天/夜晚显示不同底图
 * 2) 白天根据天气缓存（Juhe / WTTR 的 JSON）判断：雾霾/雨/雪/风 等场景
 * 3) 点击时：
 *    - 如果白天同时满足多个场景，允许随机挑一个（更有趣）
 *    - 但要保证“图标显示”和“打开的 Activity”一致（同一次点击只随机一次）
 * 4) 点击时顺带检查缓存是否过期/无缓存/城市或数据源变更，必要时触发后台更新
 */
class WeatherTimeWidget : AppWidgetProvider() {

    companion object {
        private const val TAG = "WeatherTimeWidget"

        /** 小组件点击 action（广播） */
        private const val ACTION_TAP = "com.example.myapplication.WEATHER_TAP"

        /** 调试面板“保存并刷新 Widget”用的 action（广播） */
        private const val ACTION_REFRESH = "com.example.myapplication.WEATHER_REFRESH"

        /** 缓存有效期：6 小时（和 WeatherApi 的策略保持一致） */
        private const val CACHE_MS = 6L * 60L * 60L * 1000L // 6小时

        // ===== 你要求的“强风阈值” =====
        // WTTR：没有“风级”，只有 windspeedKmph（公里/小时），>=30km/h 才算风
        private const val WTTR_WIND_KMPH_THRESHOLD = 30

        // JUHE：通常 power 里会有“x级”或“x-y级”，这里取数字并用 >=5 级判定为风
        private const val JUHE_WIND_LEVEL_THRESHOLD = 5

        /** 时间段模式：日出 / 白天 / 夜晚（决定默认底图 & 打开的 Activity） */
        private enum class Mode { SUNRISE, DAY, NIGHT }

        /** 白天场景：正常 / 雾霾(雾+霾) / 雨 / 雪 / 风 */
        private enum class DayKind { NORMAL, FOG_HAZE, RAIN, SNOW, WIND }

        /**
         * 调试覆盖：
         * - 由 AppSettings.KEY_WEATHER_DEBUG_SCENE 控制
         * - 开启覆盖后：无视真实时间与缓存，强制指定场景（方便你测试动画）
         */
        private enum class DebugScene {
            AUTO,
            SUNRISE,
            NIGHT,
            DAY_NORMAL,
            DAY_HAZE,
            DAY_RAIN,
            DAY_SNOW,
            DAY_WIND
        }

        /**
         * 根据当前小时，决定 Mode
         * - 6~8：日出
         * - 9~17：白天
         * - 其余：夜晚
         */
        private fun currentMode(): Mode {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            return when (hour) {
                in 6..8 -> Mode.SUNRISE
                in 9..17 -> Mode.DAY
                else -> Mode.NIGHT
            }
        }

        /**
         * 读取调试覆盖：
         * - 必须开启 AppSettings.isWeatherDebugEnabled
         * - 且场景不为 AUTO 才生效
         */
        private fun getDebugOverride(context: Context): DebugScene? {
            if (!AppSettings.isWeatherDebugEnabled(context)) return null
            val raw = AppSettings.getWeatherDebugScene(context).trim().uppercase(Locale.getDefault())
            val scene = runCatching { DebugScene.valueOf(raw) }.getOrNull() ?: DebugScene.AUTO
            return if (scene == DebugScene.AUTO) null else scene
        }

        /**
         * ✅ 计算“最终用于显示/跳转”的 Mode + DayKind（白天才有 DayKind）
         *
         * 规则：
         * 1) 如果调试覆盖生效：直接返回固定结果
         * 2) 否则：
         *    - 非白天：直接按时间段返回（DayKind=null）
         *    - 白天：
         *        randomOnDay=false：用“确定性优先级”（系统刷新/定时刷新用）
         *        randomOnDay=true ：若多条件满足，随机挑一个（点击时用）
         *
         * 说明：seed 用于保证同一次点击只随机一次（避免图/跳转不一致）
         */
        private fun getEffectiveModeAndKind(
            context: Context,
            randomOnDay: Boolean,
            seed: Long = 0L
        ): Pair<Mode, DayKind?> {

            // 1) 调试覆盖优先
            val debug = getDebugOverride(context)
            if (debug != null) {
                return when (debug) {
                    DebugScene.SUNRISE -> Mode.SUNRISE to null
                    DebugScene.NIGHT -> Mode.NIGHT to null
                    DebugScene.DAY_NORMAL -> Mode.DAY to DayKind.NORMAL
                    DebugScene.DAY_HAZE -> Mode.DAY to DayKind.FOG_HAZE
                    DebugScene.DAY_RAIN -> Mode.DAY to DayKind.RAIN
                    DebugScene.DAY_SNOW -> Mode.DAY to DayKind.SNOW
                    DebugScene.DAY_WIND -> Mode.DAY to DayKind.WIND
                    DebugScene.AUTO -> currentMode() to null // 理论上不会走到（AUTO 会被当 null 处理）
                }
            }

            // 2) 非调试：先按时间段
            val mode = currentMode()
            if (mode != Mode.DAY) return mode to null

            // 3) 白天：决定 DayKind
            val kind = if (randomOnDay) {
                pickDayKindRandomFromCache(context, seed)
            } else {
                pickDayKindDeterministicFromCache(context)
            }
            return mode to kind
        }

        /**
         * ✅ 确定性策略（用于 onUpdate / 刷新）：
         * 同时满足多个条件时，用固定优先级选 1 个，保证“刷新后图标稳定”
         *
         * 优先级：雾霾 > 雪 > 雨 > 风 > 默认
         */
        private fun pickDayKindDeterministicFromCache(context: Context): DayKind {
            val candidates = detectDayKindCandidatesFromCache(context)
            return when {
                candidates.contains(DayKind.FOG_HAZE) -> DayKind.FOG_HAZE
                candidates.contains(DayKind.SNOW) -> DayKind.SNOW
                candidates.contains(DayKind.RAIN) -> DayKind.RAIN
                candidates.contains(DayKind.WIND) -> DayKind.WIND
                else -> DayKind.NORMAL
            }
        }

        /**
         * ✅ 随机策略（用于点击）：
         * 同时满足多个条件时，用 seed 固定随机结果
         * - 这样一次点击中：
         *   1) Widget 图标更新
         *   2) 打开的 Activity
         *   会保持一致
         */
        private fun pickDayKindRandomFromCache(context: Context, seed: Long): DayKind {
            val candidates = detectDayKindCandidatesFromCache(context)
            if (candidates.isEmpty()) return DayKind.NORMAL
            val rnd = Random(seed)
            return candidates[rnd.nextInt(candidates.size)]
        }

        /**
         * ✅ 从缓存 JSON 里“检测白天候选场景列表”
         *
         * 说明：
         * - 雾霾/雪/雨：看当前描述（中/英关键字）
         * - 风：按阈值判断
         *   - WTTR：windspeedKmph >= 30
         *   - JUHE：power >= 5级
         * - 返回不包含 NORMAL；无命中则 emptyList()
         */
        private fun detectDayKindCandidatesFromCache(context: Context): List<DayKind> {
            val raw = AppSettings.getWeatherCacheJson(context)
            if (raw.isBlank()) return emptyList()

            // 取“当前天气描述”（兼容 Juhe / WTTR）
            val desc = extractCurrentDesc(raw)
            val lower = desc.lowercase(Locale.getDefault())

            val set = LinkedHashSet<DayKind>()

            // 1) 雾霾/雾：中文（霾/雾）或英文（haze/smog/mist/fog）
            if (containsAny(desc, listOf("霾", "雾")) || containsAny(lower, listOf("haze", "smog", "mist", "fog"))) {
                set.add(DayKind.FOG_HAZE)
            }

            // 2) 雪：中文（雪）或英文（snow/...）
            if (containsAny(desc, listOf("雪")) || containsAny(lower, listOf("snow", "blizzard", "sleet", "ice pellets"))) {
                set.add(DayKind.SNOW)
            }

            // 3) 雨：中文（雨）或英文（rain/...）
            if (containsAny(desc, listOf("雨")) || containsAny(lower, listOf("rain", "drizzle", "shower", "freezing rain"))) {
                set.add(DayKind.RAIN)
            }

            // 4) 风：只按“强风阈值”判断（避免普通“微风”也触发）
            val windByWttr = parseMaxWindKmphFromWttr(raw)
            if (windByWttr != null && windByWttr >= WTTR_WIND_KMPH_THRESHOLD) {
                set.add(DayKind.WIND)
            }

            val windLevelByJuhe = parseWindLevelFromJuhe(raw)
            if (windLevelByJuhe != null && windLevelByJuhe >= JUHE_WIND_LEVEL_THRESHOLD) {
                set.add(DayKind.WIND)
            }

            /**
             * 可选增强：如果文本里出现明显强风词，也判 WIND
             * - 这段是“补充兜底”，防止某些数据源缺数值但描述有“强风/大风”
             * - 你觉得太敏感就删掉
             */
            if (
                containsAny(desc, listOf("大风", "强风", "狂风", "劲风")) ||
                containsAny(lower, listOf("gale", "storm", "tornado"))
            ) {
                set.add(DayKind.WIND)
            }

            val result = set.toList()
            Log.d(TAG, "detectDayKindCandidates: desc=$desc, candidates=$result, wttrWind=$windByWttr, juheLevel=$windLevelByJuhe")
            return result
        }

        /**
         * 从 raw JSON 提取“当前天气描述”
         * - WTTR：current_condition[0].lang_zh[0].value（优先中文）/ weatherDesc[0].value（英文）
         * - JUHE：error_code==0 -> result.realtime.info
         */
        private fun extractCurrentDesc(raw: String): String {
            return try {
                val obj = JSONObject(raw)

                // 1) WTTR
                val cc = obj.optJSONArray("current_condition")?.optJSONObject(0)
                if (cc != null) {
                    val zh = cc.optJSONArray("lang_zh")?.optJSONObject(0)?.optString("value").orEmpty()
                    if (zh.isNotBlank()) return zh

                    val en = cc.optJSONArray("weatherDesc")?.optJSONObject(0)?.optString("value").orEmpty()
                    if (en.isNotBlank()) return en
                }

                // 2) JUHE
                val errorCode = obj.optInt("error_code", Int.MIN_VALUE)
                if (errorCode == 0) {
                    val info = obj.optJSONObject("result")
                        ?.optJSONObject("realtime")
                        ?.optString("info", "")
                        .orEmpty()
                    if (info.isNotBlank()) return info
                }

                ""
            } catch (_: Exception) {
                ""
            }
        }

        /**
         * 简单包含判断：
         * - 这里 text 可能已经是 lower/原始中文
         * - 所以不要 ignoreCase=true，避免中文大小写无意义的额外成本
         */
        private fun containsAny(text: String, keys: List<String>): Boolean {
            for (k in keys) if (text.contains(k, ignoreCase = false)) return true
            return false
        }

        /**
         * WTTR：从原始 JSON 中抓取所有 windspeedKmph，然后取最大值
         * - 你之所以取最大值：避免某些字段/天数里重复出现时漏掉“更大值”
         * - 这里用正则是为了快速，不依赖具体 JSON 结构（但也更脆弱一点）
         */
        private fun parseMaxWindKmphFromWttr(raw: String): Int? {
            val matches = Regex(""""windspeedKmph"\s*:\s*"(\d+)"""")
                .findAll(raw)
                .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
                .toList()
            return matches.maxOrNull()
        }

        /**
         * JUHE：从 raw JSON 里解析 realtime.power 的风级
         * 示例：
         * - "3级"
         * - "4-5级"
         * 这里取第一个数字（或 power 中出现的第一个数字）
         */
        private fun parseWindLevelFromJuhe(raw: String): Int? {
            val power = Regex(""""power"\s*:\s*"([^"]*)"""")
                .find(raw)
                ?.groupValues
                ?.getOrNull(1)

            if (power.isNullOrBlank()) return null
            return Regex("""(\d+)""").find(power)?.groupValues?.getOrNull(1)?.toIntOrNull()
        }

        /** 外部调用：强制刷新所有该 Widget 实例 */
        fun forceUpdateAll(context: Context) = updateAllWidgets(context)

        /**
         * 刷新所有 Widget（系统刷新/手动刷新会走这个）
         * - randomOnDay=false：保证白天场景“稳定”
         */
        private fun updateAllWidgets(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, WeatherTimeWidget::class.java))
            val (mode, kind) = getEffectiveModeAndKind(context, randomOnDay = false)
            ids.forEach { updateOneWidget(context, mgr, it, mode, kind) }
        }

        /**
         * 点击时用：
         * - 把“同一次点击决定的 mode/kind”同步更新给所有实例
         * - 避免桌面上多个 Widget 之间出现“有的变了、有的没变”的割裂感
         */
        private fun updateAllWidgetsWithChosen(context: Context, chosenMode: Mode, chosenKind: DayKind?) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, WeatherTimeWidget::class.java))
            ids.forEach { updateOneWidget(context, mgr, it, chosenMode, chosenKind) }
        }

        /**
         * 更新某一个 Widget 实例（根据 mode/kind 设置图，并挂点击 PendingIntent）
         */
        private fun updateOneWidget(context: Context, mgr: AppWidgetManager, id: Int, mode: Mode, kind: DayKind?) {
            val views = RemoteViews(context.packageName, R.layout.widget_weather_time)

            // 根据当前模式/场景选择图
            val imgRes = when (mode) {
                Mode.SUNRISE -> R.drawable.weather_widget_sunrise
                Mode.NIGHT -> R.drawable.weather_widget_night
                Mode.DAY -> {
                    when (kind ?: DayKind.NORMAL) {
                        DayKind.FOG_HAZE -> R.drawable.weather_widget_haze
                        DayKind.RAIN -> R.drawable.weather_widget_rain
                        DayKind.SNOW -> R.drawable.weather_widget_snowman
                        DayKind.WIND -> R.drawable.weather_tornado_01
                        DayKind.NORMAL -> R.drawable.weather_widget_day
                    }
                }
            }
            views.setImageViewResource(R.id.img_weather, imgRes)

            /**
             * 给 widget_root 绑定点击广播：
             * - action=ACTION_TAP
             * - data=Uri 唯一化，防止桌面复用 PendingIntent 导致点错/串号
             */
            val tap = Intent(context, WeatherTimeWidget::class.java).apply {
                action = ACTION_TAP
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                data = Uri.parse("weather://tap/$id")
            }

            val pi = PendingIntent.getBroadcast(
                context,
                id, // requestCode 用 widgetId，保证不同实例互不干扰
                tap,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pi)

            mgr.updateAppWidget(id, views)
        }

        /**
         * 点击 Widget 时顺带检查：是否需要触发一次后台更新（WorkManager）
         *
         * 触发条件：
         * - 没缓存
         * - 缓存过期
         * - 城市变了（cacheCity != 当前 city）
         * - 数据源变了（cacheProvider != 当前 provider）
         *
         * 注意：这里只是 enqueue；真正联网获取由 Worker 执行
         */
        private fun maybeTriggerUpdateOnTap(context: Context) {
            val now = System.currentTimeMillis()
            val last = AppSettings.getWeatherLastFetchMs(context)
            val cached = AppSettings.getWeatherCacheJson(context)

            val cacheCity = AppSettings.getWeatherCacheCity(context)
            val city = AppSettings.getWeatherCity(context)

            val cacheProvider = AppSettings.getWeatherCacheProvider(context)
            val provider = AppSettings.getWeatherProvider(context)

            val cityChanged = cacheCity.isNotBlank() && cacheCity != city

            val providerChanged =
                cacheProvider.isNotBlank() &&
                        cacheProvider.uppercase(Locale.getDefault()) != provider.trim().uppercase(Locale.getDefault())

            val expired = (now - last) >= CACHE_MS
            val empty = cached.isBlank()

            if (empty || expired || cityChanged || providerChanged) {
                WeatherAutoUpdater.enqueueUpdateNow(context)
            }
        }

        /**
         * 根据 mode/kind 决定点击后要打开哪个 Activity（对应你的动效页）
         */
        private fun targetActivityFor(mode: Mode, kind: DayKind?): Class<*> {
            return when (mode) {
                Mode.SUNRISE -> WeatherSunriseActivity::class.java
                Mode.NIGHT -> WeatherNightActivity::class.java
                Mode.DAY -> when (kind ?: DayKind.NORMAL) {
                    DayKind.FOG_HAZE -> WeatherHazeActivity::class.java
                    DayKind.RAIN -> WeatherRainActivity::class.java
                    DayKind.SNOW -> WeatherSnowActivity::class.java
                    DayKind.WIND -> WeatherWindActivity::class.java
                    DayKind.NORMAL -> WeatherDayActivity::class.java
                }
            }
        }
    }

    /**
     * 第一次把 Widget 加到桌面时调用
     * - 这里尝试“如果用户开启了自动更新，就确保任务存在”
     */
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WeatherAutoUpdater.ensureScheduledIfEnabled(context)
    }

    /**
     * 系统定期刷新/桌面触发刷新时调用
     * - randomOnDay=false：保证白天场景稳定（雾霾>雪>雨>风>默认）
     */
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WeatherAutoUpdater.ensureScheduledIfEnabled(context)
        val (mode, kind) = getEffectiveModeAndKind(context, randomOnDay = false)
        appWidgetIds.forEach { updateOneWidget(context, appWidgetManager, it, mode, kind) }
    }

    /**
     * 接收点击广播 / 调试刷新广播
     */
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        // 1) 调试页“保存并刷新 Widget”用
        if (intent.action == ACTION_REFRESH) {
            updateAllWidgets(context)
            return
        }

        // 2) 只处理点击 action
        if (intent.action != ACTION_TAP) return

        // 记录“今天是否第一次点击”（用于早安/晚安文案逻辑）
        val isFirstToday = AppSettings.markWeatherTapAndIsFirstToday(context)

        /**
         * ✅ 一次点击只随机一次：
         * - seed 用当前时间
         * - getEffectiveModeAndKind(randomOnDay=true) 会用这个 seed 选 DayKind
         */
        val seed = System.currentTimeMillis()
        val (mode, kind) = getEffectiveModeAndKind(context, randomOnDay = true, seed = seed)

        // ✅ 用同一个结果更新所有 widget（避免图和跳转不一致）
        updateAllWidgetsWithChosen(context, mode, kind)

        // ✅ 点击时也检查是否要触发一次后台更新（体验更像“点一下就会帮你更新”）
        maybeTriggerUpdateOnTap(context)

        // 打开对应动效 Activity（需要 NEW_TASK，因为这里是 BroadcastReceiver 场景）
        val target = targetActivityFor(mode, kind)
        context.startActivity(Intent(context, target).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("is_first_today", isFirstToday)
        })
    }
}
