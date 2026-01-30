package com.example.myapplication

import android.content.Context
import java.util.Calendar

/**
 * AppSettings：
 * - 项目内统一的 SharedPreferences 读写入口（相当于“轻量数据库”）
 * - 存放：小组件抽签状态、点击计数、动画开关、天气设置/缓存/调试开关等
 *
 * 维护建议：
 * - KEY_* 一旦发布后尽量不要改名（会导致旧用户数据读不到）
 * - 需要迁移时：新增新 key，保留旧 key 做兼容读取/写入
 */
object AppSettings {
    /** SharedPreferences 文件名（所有配置都存同一个 prefs） */
    const val PREF_NAME = "fortune_widget_prefs"

    // ---------------- 抽签（竹筒）相关 ----------------

    /** 竹筒：今天/当前周期是否已经抽过（避免重复抽） */
    const val KEY_DRAWN = "fortune_drawn"
    /** 竹筒：抽到的文本内容（展示给用户） */
    const val KEY_TEXT = "fortune_text"

    // ---------------- 全局点击计数（可能用于彩蛋/频率统计） ----------------

    /** 共用计数器：记录用户点击次数（不区分具体哪个 widget） */
    const val KEY_CLICK_COUNT = "fortune_click_count"

    // ---------------- 动画开关 ----------------

    /** 是否启用点击后动画（true=有动画，false=直接展示/跳转） */
    const val KEY_ANIM_ENABLED = "fortune_anim_enabled"

    // ---------------- 星星瓶相关 ----------------

    /** 星星瓶：是否已经抽过 */
    const val KEY_STAR_DRAWN = "star_drawn"
    /** 星星瓶：抽到的文本内容 */
    const val KEY_STAR_TEXT = "star_text"

    // ---------------- 天气：城市 & 缓存 ----------------
    // 说明：
    // - KEY_WEATHER_CITY：用户手动输入的城市（用于请求接口）
    // - 缓存：保存上一次请求得到的原始 JSON + 时间戳
    // - cacheCity/cacheProvider：用于判断“缓存是否还能用”（城市/数据源切换后缓存应该作废）

    /** 天气：用户设置的城市（默认合肥） */
    const val KEY_WEATHER_CITY = "weather_city"
    /** 天气：缓存的原始 JSON（WTTR/JUHE 原始返回） */
    const val KEY_WEATHER_CACHE_JSON = "weather_cache_json"
    /** 天气：最后一次联网成功获取的时间戳（ms） */
    const val KEY_WEATHER_LAST_FETCH_MS = "weather_last_fetch_ms"
    /** 天气：缓存对应的城市（用于判断城市变化时是否作废缓存） */
    const val KEY_WEATHER_CACHE_CITY = "weather_cache_city"

    // ---------------- WeatherTimeWidget 自动更新配置 ----------------
    // 只给 WeatherTimeWidget 的自动更新逻辑使用（比如 WorkManager/Alarm 等）
    // 注意：这里的“自动更新”是“后台定时拉天气并刷新 widget”，不是用户点 widget 那次。

    /** 天气：是否启用后台自动更新（仅 WeatherTimeWidget 用） */
    const val KEY_WEATHER_AUTO_ENABLED = "weather_auto_enabled"
    /** 天气：自动更新任务上次执行时间（便于调试/展示/节流） */
    const val KEY_WEATHER_AUTO_LAST_RUN_MS = "weather_auto_last_run_ms"

    // ---------------- 天气数据源配置 ----------------
    // provider：你现在支持 WTTR / WTTR_V2 / JUHE（旧注释写 WTTR/JUHE，这里建议后续同步）
    // JUHE_KEY：用户自行填写（因为免费次数限制，不内置作者 key）

    /** 天气：当前数据源（例如 WTTR / WTTR_V2 / JUHE） */
    const val KEY_WEATHER_PROVIDER = "weather_provider" // WTTR / JUHE
    /** 天气：聚合数据 Juhe 的 key（仅 JUHE 时需要） */
    const val KEY_WEATHER_JUHE_KEY = "weather_juhe_key"

    // ---------------- 缓存附带 provider ----------------
    // 用途：防止用户切换数据源后仍然使用旧缓存（比如 WTTR 缓存被 JUHE 误用）

    /** 天气：缓存对应的数据源（用于判断 provider 切换后缓存是否作废） */
    const val KEY_WEATHER_CACHE_PROVIDER = "weather_cache_provider"

    // ---------------- Weather 小组件点击记录（用于“今天第一次”判断） ----------------
    // 典型用途：每天第一次点击显示特别动画/提示/彩蛋等。

    /** 上一次点击发生在哪一天（yyyyMMdd，例如 20251218） */
    private const val KEY_WEATHER_LAST_TAP_DAY = "weather_last_tap_day"
    /** 上一次点击的时间戳（ms） */
    private const val KEY_WEATHER_LAST_TAP_MS = "weather_last_tap_ms"

    /** 获取 prefs 实例（统一入口，避免各处重复写文件名） */
    fun prefs(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /**
     * 仅重置“竹筒抽签状态”，不影响星星瓶/天气/计数等其它设置。
     * 常用于：需要让用户重新抽签，但不想清空全部数据。
     */
    fun resetDrawStateOnly(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_DRAWN, false)
            .putString(KEY_TEXT, "")
            .apply()
    }

    /**
     * 同时重置竹筒 + 星星瓶的抽取状态（但仍不影响天气/计数/开关等）。
     */
    fun resetAllWidgetsDrawStateOnly(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_DRAWN, false)
            .putString(KEY_TEXT, "")
            .putBoolean(KEY_STAR_DRAWN, false)
            .putString(KEY_STAR_TEXT, "")
            .apply()
    }

    /**
     * 点击计数 +1 并返回最新计数。
     * 备注：如果未来要做“按 widget 分开计数”，需要新增 key，而不是复用这个 key。
     */
    fun incClickCount(context: Context): Int {
        val p = prefs(context)
        val n = p.getInt(KEY_CLICK_COUNT, 0) + 1
        p.edit().putInt(KEY_CLICK_COUNT, n).apply()
        return n
    }

    /** 清空点击计数（用于调试或重置彩蛋触发条件） */
    fun clearClickCount(context: Context) {
        prefs(context).edit().putInt(KEY_CLICK_COUNT, 0).apply()
    }

    /** 是否启用动画（默认 true） */
    fun isAnimEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ANIM_ENABLED, true)

    /** 设置动画开关 */
    fun setAnimEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ANIM_ENABLED, enabled).apply()
    }

    // ---------------- 天气相关 ----------------

    /**
     * 获取城市（默认：合肥）
     * 备注：这里返回的是“用户输入城市”，不保证是缓存的城市。
     */
    fun getWeatherCity(context: Context): String =
        prefs(context).getString(KEY_WEATHER_CITY, "合肥") ?: "合肥"

    /** 保存城市（会 trim，避免用户输入前后空格导致缓存判断异常） */
    fun setWeatherCity(context: Context, city: String) {
        prefs(context).edit().putString(KEY_WEATHER_CITY, city.trim()).apply()
    }

    /** 读取缓存的原始 JSON（用于：展示缓存/判断场景/减少网络请求） */
    fun getWeatherCacheJson(context: Context): String =
        prefs(context).getString(KEY_WEATHER_CACHE_JSON, "") ?: ""

    /** 最后一次联网成功获取的时间（用于：6小时缓存策略、调试页展示） */
    fun getWeatherLastFetchMs(context: Context): Long =
        prefs(context).getLong(KEY_WEATHER_LAST_FETCH_MS, 0L)

    /** 缓存对应的城市（城市变了 -> 缓存应视为不可用） */
    fun getWeatherCacheCity(context: Context): String =
        prefs(context).getString(KEY_WEATHER_CACHE_CITY, "") ?: ""

    /**
     * 当前选中的数据源（默认 WTTR）
     * 注意：这里是“当前设置”，不等于“缓存的 provider”。
     */
    fun getWeatherProvider(context: Context): String =
        prefs(context).getString(KEY_WEATHER_PROVIDER, "WTTR") ?: "WTTR"

    /** 保存数据源（建议统一 uppercase 存储，减少比较时大小写问题） */
    fun setWeatherProvider(context: Context, provider: String) {
        prefs(context).edit().putString(KEY_WEATHER_PROVIDER, provider.trim()).apply()
    }

    /** 获取 JUHE key（用户自行填写，默认空） */
    fun getJuheKey(context: Context): String =
        prefs(context).getString(KEY_WEATHER_JUHE_KEY, "") ?: ""

    /** 保存 JUHE key（trim 避免粘贴带空格导致请求失败） */
    fun setJuheKey(context: Context, key: String) {
        prefs(context).edit().putString(KEY_WEATHER_JUHE_KEY, key.trim()).apply()
    }

    /** 缓存对应的数据源（provider 切换后用于判定缓存作废） */
    fun getWeatherCacheProvider(context: Context): String =
        prefs(context).getString(KEY_WEATHER_CACHE_PROVIDER, "") ?: ""

    /**
     * ✅ 新版缓存写入：
     * 同时写入 provider + city + json + fetch 时间。
     * 用途：防止“切换数据源/城市后仍使用旧缓存”造成显示/判断错误。
     */
    fun setWeatherCache(context: Context, provider: String, city: String, json: String, fetchMs: Long) {
        prefs(context).edit()
            .putString(KEY_WEATHER_CACHE_JSON, json)
            .putLong(KEY_WEATHER_LAST_FETCH_MS, fetchMs)
            .putString(KEY_WEATHER_CACHE_CITY, city.trim())
            .putString(KEY_WEATHER_CACHE_PROVIDER, provider.trim())
            .apply()
    }

    /**
     * ✅ 旧版缓存写入（兼容保留）：
     * 仅写 city/json/fetchMs，不写 provider。
     *
     * 目的：避免其他旧文件仍调用旧签名导致编译错误；
     * 风险：如果只调用旧方法，缓存不会带 provider 信息，切换数据源时更容易误用缓存。
     */
    fun setWeatherCache(context: Context, city: String, json: String, fetchMs: Long) {
        prefs(context).edit()
            .putString(KEY_WEATHER_CACHE_JSON, json)
            .putLong(KEY_WEATHER_LAST_FETCH_MS, fetchMs)
            .putString(KEY_WEATHER_CACHE_CITY, city.trim())
            .apply()
    }

    // ---------------- 自动更新开关（只给 WeatherTimeWidget 用） ----------------

    /** 是否启用后台自动更新（默认 false） */
    fun isWeatherAutoEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_WEATHER_AUTO_ENABLED, false)

    /** 设置后台自动更新开关（通常会触发 schedule/取消 schedule） */
    fun setWeatherAutoEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_WEATHER_AUTO_ENABLED, enabled).apply()
    }

    /** 记录自动更新任务上次执行时间（用于调试/状态展示/节流） */
    fun setWeatherAutoLastRunMs(context: Context, ms: Long) {
        prefs(context).edit().putLong(KEY_WEATHER_AUTO_LAST_RUN_MS, ms).apply()
    }

    /** 读取自动更新任务上次执行时间 */
    fun getWeatherAutoLastRunMs(context: Context): Long =
        prefs(context).getLong(KEY_WEATHER_AUTO_LAST_RUN_MS, 0L)

    // ---------------- 点击记录：判断今天是否第一次 ----------------

    /** 上次点击时间戳（ms） */
    fun getWeatherLastTapMs(context: Context): Long =
        prefs(context).getLong(KEY_WEATHER_LAST_TAP_MS, 0L)

    /** 上次点击日期（yyyyMMdd） */
    fun getWeatherLastTapDay(context: Context): Int =
        prefs(context).getInt(KEY_WEATHER_LAST_TAP_DAY, 0)

    /**
     * ✅ 记录本次点击，并返回：是否“今天第一次点击”
     *
     * 实现方式：把当前日期转成 yyyyMMdd 的整数，与上次记录对比。
     * 备注：依赖系统时间；如果用户手动改时间/时区，判定可能出现跳变（通常可接受）。
     */
    fun markWeatherTapAndIsFirstToday(context: Context): Boolean {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val dayKey =
            cal.get(Calendar.YEAR) * 10000 +
                    (cal.get(Calendar.MONTH) + 1) * 100 +
                    cal.get(Calendar.DAY_OF_MONTH)

        val lastDay = getWeatherLastTapDay(context)
        val isFirst = lastDay != dayKey

        prefs(context).edit()
            .putInt(KEY_WEATHER_LAST_TAP_DAY, dayKey)
            .putLong(KEY_WEATHER_LAST_TAP_MS, now)
            .apply()

        return isFirst
    }

    // ---------------- 天气调试功能（强制场景覆盖） ----------------
    // 用途：开发/截图时强制显示某个场景（白天/夜晚/雾霾/雨/雪/风），不受真实天气影响。

    /** 调试：是否启用“强制场景覆盖” */
    const val KEY_WEATHER_DEBUG_ENABLED = "weather_debug_enabled"
    /** 调试：当前强制场景（字符串枚举） */
    const val KEY_WEATHER_DEBUG_SCENE = "weather_debug_scene"

    /** 是否开启天气调试覆盖（默认 false） */
    fun isWeatherDebugEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_WEATHER_DEBUG_ENABLED, false)
    }

    /** 设置调试覆盖开关 */
    fun setWeatherDebugEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_WEATHER_DEBUG_ENABLED, enabled).apply()
    }

    /**
     * scene 建议存字符串：
     * AUTO / SUNRISE / NIGHT / DAY_NORMAL / DAY_HAZE / DAY_RAIN / DAY_SNOW / DAY_WIND
     *
     * 备注：这里返回的是“设置值”，具体如何解释（比如 AUTO 的含义）由 Widget/判断逻辑负责。
     */
    fun getWeatherDebugScene(context: Context): String {
        return prefs(context).getString(KEY_WEATHER_DEBUG_SCENE, "AUTO") ?: "AUTO"
    }

    /** 保存调试场景字符串（建议 trim/uppercase，避免用户输入导致匹配失败） */
    fun setWeatherDebugScene(context: Context, scene: String) {
        prefs(context).edit().putString(KEY_WEATHER_DEBUG_SCENE, scene).apply()
    }
}
