package com.example.myapplication

import android.content.Context
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.max

/**
 * WeatherApi
 *
 * 这个文件是“天气获取 + 缓存策略 + 解析展示文本”的核心实现。
 *
 * 主要职责：
 * 1) 根据用户设置的数据源（WTTR / WTTR_V2 / JUHE）去请求天气 JSON
 * 2) 做 6 小时缓存（避免频繁联网、避免免费接口限流）
 * 3) 支持 force 强制刷新（忽略缓存）
 * 4) 提供 parseToText(rawJson) 给测试页面直接显示可读文本
 *
 * 注意：
 * - Widget 判断“白天/雨雪雾风”等，另外一个文件（WeatherTimeWidget）里会从缓存 JSON 里再做一层判断
 * - 这里 parseToText 只是“展示用”，不是用于决定场景的最终逻辑
 */
object WeatherApi {

    /** Juhe（聚合数据）接口地址：simpleWeather/query */
    // 建议用 https，更稳一些；如果你那边聚合只支持 http，再改回去
    private const val JUHE_URL = "http://apis.juhe.cn/simpleWeather/query"

    /** 缓存有效期：6 小时（毫秒） */
    private const val CACHE_MS = 6L * 60L * 60L * 1000L // 6小时

    /**
     * 网络超时时间（毫秒）
     * - WTTR 在国内有时比较慢，超时设置稍微大一点更友好
     */
    private const val CONNECT_TIMEOUT_MS = 12_000
    private const val READ_TIMEOUT_MS = 12_000

    /**
     * WTTR 轻量重试次数
     * - 只对 WTTR/WTTR_V2 做重试（Juhe 通常不需要）
     * - 这里是“失败后额外再试 1 次”
     */
    private const val WTTR_RETRY_COUNT = 1

    /**
     * 支持的数据源枚举
     * - WTTR：wttr.in
     * - WTTR_V2：v2.wttr.in（备用域名）
     * - JUHE：聚合数据
     */
    enum class Provider { WTTR, WTTR_V2, JUHE }

    /**
     * fetchWeatherSmartResult 的返回结果：
     * - rawJson：最终拿到的 JSON（来自缓存或网络）
     * - usedCache：是否使用了缓存
     * - cacheRemainingMs：缓存剩余可用时间（usedCache=true 时才有意义）
     * - lastFetchMs：缓存写入时刻（或缓存本身记录的 lastFetch）
     * - provider：本次请求使用的数据源（按设置读取）
     */
    data class SmartResult(
        val rawJson: String,
        val usedCache: Boolean,
        val cacheRemainingMs: Long,
        val lastFetchMs: Long,
        val provider: Provider
    )

    /**
     * 从 AppSettings 读取当前数据源设置，并转换为 Provider
     * - 兜底：未知字符串 -> WTTR
     */
    private fun readProvider(context: Context): Provider {
        val raw = AppSettings.getWeatherProvider(context).trim().uppercase(Locale.getDefault())
        return when (raw) {
            "JUHE" -> Provider.JUHE
            "WTTR_V2" -> Provider.WTTR_V2
            "WTTR" -> Provider.WTTR
            else -> Provider.WTTR
        }
    }

    /**
     * 根据 Provider 返回 wttr 的 baseUrl
     * - WTTR_V2 使用 v2.wttr.in
     * - 其他默认 wttr.in
     */
    private fun wttrBase(provider: Provider): String {
        return when (provider) {
            Provider.WTTR_V2 -> "https://v2.wttr.in"
            else -> "https://wttr.in"
        }
    }

    /**
     * 简单的 HTTP GET 工具函数（返回响应文本）
     *
     * 这里做的事：
     * - 设置 connect/read 超时
     * - 加 UA / Accept / Accept-Language（有些网络或服务对 UA 很敏感）
     * - 读取 2xx 用 inputStream，非 2xx 优先用 errorStream
     */
    private fun httpGet(urlStr: String): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
            useCaches = false

            // ✅ 有些网络/服务对 UA 很敏感
            setRequestProperty("User-Agent", "Mozilla/5.0 (Android) WeatherWidget/1.0")
            setRequestProperty("Accept", "application/json,text/plain,*/*")
            setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.7")
        }

        return try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
            stream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * 请求 WTTR（或 WTTR_V2）原始 JSON
     *
     * 关键点：
     * - city 会进行 URL 编码（支持中文城市名）
     * - format=j1 返回 JSON
     * - lang=zh-cn：尽量让 wttr 返回中文字段（部分返回会是 lang_zh-cn 或 lang_zh）
     * - 加入轻量重试（失败后 sleep 350ms 再试一次）
     */
    private fun fetchRawWttr(provider: Provider, city: String): String {
        val cityEncoded = URLEncoder.encode(city.trim(), "UTF-8")

        // ✅ 尽量带上 lang，wttr 的 JSON 会给 lang_zh（如果服务端支持）
        val urlStr = "${wttrBase(provider)}/$cityEncoded?format=j1&lang=zh-cn"

        var lastErr: Exception? = null
        val totalTry = 1 + WTTR_RETRY_COUNT

        repeat(totalTry) { idx ->
            try {
                return httpGet(urlStr)
            } catch (e: Exception) {
                lastErr = e
                // 第一次失败后再试一次就行，不要疯狂重试
                if (idx < totalTry - 1) {
                    // 让下一次稍微“错峰”一下
                    try { Thread.sleep(350) } catch (_: Exception) {}
                }
            }
        }

        throw lastErr ?: IOException("WTTR 请求失败")
    }

    /**
     * 请求 Juhe 原始 JSON
     *
     * 关键点：
     * - Juhe 必须要 key，如果 key 为空，这里直接返回一个“伪 JSON 错误结构”
     *   让上层 parseToText 能显示出友好原因（而不是直接崩溃）
     */
    private fun fetchRawJuhe(context: Context, city: String): String {
        val key = AppSettings.getJuheKey(context).trim()
        if (key.isBlank()) {
            // 让上层显示原因
            return """{"error_code":-2,"reason":"Juhe key 为空，请在“天气数据源设置”里输入 key"}"""
        }

        val cityEncoded = URLEncoder.encode(city.trim(), "UTF-8")
        val urlStr = "$JUHE_URL?key=$key&city=$cityEncoded"
        return httpGet(urlStr)
    }

    /**
     * 统一入口：根据 Provider 决定走哪个请求
     */
    private fun fetchRaw(context: Context, provider: Provider, city: String): String {
        return when (provider) {
            Provider.JUHE -> fetchRawJuhe(context, city)
            Provider.WTTR, Provider.WTTR_V2 -> fetchRawWttr(provider, city)
        }
    }

    /**
     * 写入缓存（原始 JSON + fetch 时间 + city + provider）
     *
     * 为什么这里不直接调用 AppSettings.setWeatherCache(...)？
     * - 你前面提过参数签名可能不一致/旧代码还在调用旧方法
     * - 这里直接写 prefs，避免“调用错方法导致缓存写不进去”
     */
    private fun writeCache(context: Context, providerName: String, city: String, json: String, fetchMs: Long) {
        AppSettings.prefs(context).edit()
            .putString(AppSettings.KEY_WEATHER_CACHE_JSON, json)
            .putLong(AppSettings.KEY_WEATHER_LAST_FETCH_MS, fetchMs)
            .putString(AppSettings.KEY_WEATHER_CACHE_CITY, city.trim())
            .putString(AppSettings.KEY_WEATHER_CACHE_PROVIDER, providerName.trim())
            .apply()
    }

    /**
     * fetchWeatherSmartResult（核心方法）
     *
     * 功能：
     * - 判断是否可以用缓存
     * - 若可用：直接返回缓存 JSON（usedCache=true）
     * - 若不可用：联网请求并写缓存（usedCache=false）
     *
     * 缓存失效条件：
     * - force=true（强制刷新）
     * - 城市变了（cacheCity != reqCity）
     * - 数据源变了（cacheProvider != provider.name）
     * - 缓存为空 或 超过 6 小时
     */
    fun fetchWeatherSmartResult(context: Context, city: String, force: Boolean): SmartResult {
        val now = System.currentTimeMillis()
        val reqCity = city.trim()
        val provider = readProvider(context)

        // 读取缓存信息
        val cached = AppSettings.getWeatherCacheJson(context)
        val lastFetch = AppSettings.getWeatherLastFetchMs(context)
        val cacheCity = AppSettings.getWeatherCacheCity(context)
        val cacheProvider = AppSettings.getWeatherCacheProvider(context)

        // 判断是否变更
        val cityChanged = cacheCity.isNotBlank() && cacheCity != reqCity
        val providerChanged = cacheProvider.isNotBlank() && cacheProvider.uppercase(Locale.getDefault()) != provider.name
        val cacheValid = cached.isNotBlank() && (now - lastFetch) < CACHE_MS

        // 是否使用缓存（满足：不强制 + 城市没变 + provider 没变 + 缓存有效）
        val shouldUseCache = !force && !cityChanged && !providerChanged && cacheValid
        if (shouldUseCache) {
            return SmartResult(
                rawJson = cached,
                usedCache = true,
                cacheRemainingMs = max(0L, CACHE_MS - (now - lastFetch)),
                lastFetchMs = lastFetch,
                provider = provider
            )
        }

        // 需要联网：请求原始 JSON
        val raw = fetchRaw(context, provider, reqCity)

        // ✅ 写缓存（包含 provider）
        writeCache(context, provider.name, reqCity, raw, now)

        return SmartResult(
            rawJson = raw,
            usedCache = false,
            cacheRemainingMs = CACHE_MS,
            lastFetchMs = now,
            provider = provider
        )
    }

    /**
     * parseToText
     *
     * 用途：
     * - 给“测试页面/调试页面”直接显示可读文本
     * - 会自动识别 Juhe / wttr 的 JSON 结构
     *
     * 注意：
     * - 这只是“展示用解析”，不是决定 DayKind（雨雪雾风）的最终判断逻辑
     * - DayKind 的判断逻辑在 WeatherTimeWidget 里（从缓存中取 current_condition / realtime.info 等字段）
     */
    fun parseToText(rawJson: String): String {
        if (rawJson.isBlank()) return "无数据"

        // -------------------------
        // 1) Juhe：特征是包含 error_code
        // -------------------------
        runCatching {
            val root = JSONObject(rawJson)

            // Juhe 的响应一定会带 error_code（成功也有）
            if (root.has("error_code")) {
                val errorCode = root.optInt("error_code", -1)
                val reason = root.optString("reason", "")
                if (errorCode != 0) return "请求失败：error_code=$errorCode\nreason=$reason"

                // 成功结构：result -> realtime / future
                val result = root.optJSONObject("result") ?: return "解析失败：result 为空"
                val city = result.optString("city", "")
                val realtime = result.optJSONObject("realtime")
                val temp = realtime?.optString("temperature", "").orEmpty()
                val info = realtime?.optString("info", "").orEmpty()
                val humidity = realtime?.optString("humidity", "").orEmpty()
                val direct = realtime?.optString("direct", "").orEmpty()
                val power = realtime?.optString("power", "").orEmpty()
                val aqi = realtime?.optString("aqi", "").orEmpty()

                val sb = StringBuilder()
                sb.append("数据源：聚合（Juhe）\n")
                sb.append("城市：").append(city).append("\n")
                sb.append("当前：").append(info).append("  ").append(temp).append("℃")
                if (humidity.isNotBlank()) sb.append("  湿度").append(humidity)
                if (aqi.isNotBlank()) sb.append("  AQI ").append(aqi)
                sb.append("\n")
                if (direct.isNotBlank() || power.isNotBlank()) {
                    sb.append("风：").append(direct).append(" ").append(power).append("\n")
                }

                // 未来预报：这里最多展示 3 天（避免太长）
                val future = result.optJSONArray("future")
                if (future != null && future.length() > 0) {
                    sb.append("\n未来（取3天）：\n")
                    val days = minOf(3, future.length())
                    for (i in 0 until days) {
                        val d = future.optJSONObject(i) ?: continue
                        val date = d.optString("date", "")
                        val temperature = d.optString("temperature", "")
                        val weather = d.optString("weather", "")
                        val dir = d.optString("direct", "")
                        sb.append("• ").append(date).append("  ").append(weather)
                            .append("  ").append(temperature)
                        if (dir.isNotBlank()) sb.append("  ").append(dir)
                        sb.append("\n")
                    }
                }
                return sb.toString().trim()
            }
        }

        // -------------------------
        // 2) wttr：特征是包含 current_condition
        // -------------------------
        return runCatching {
            val obj = JSONObject(rawJson)
            val cc = obj.optJSONArray("current_condition")?.optJSONObject(0)
                ?: return "解析失败：current_condition 为空"

            val tempC = cc.optString("temp_C", "")
            val humidity = cc.optString("humidity", "")
            val windKmph = cc.optString("windspeedKmph", "")

            /**
             * ✅ 描述优先级：
             * - 先取中文字段（如果存在）
             * - 再取英文 weatherDesc
             *
             * 这里你之前遇到过一个坑：
             * - wttr 返回的是 "lang_zh-cn"（带 -cn）
             * - 但你旧代码用的是 "lang_zh"
             * 所以如果你发现中文取不到，多半就是字段名不一致导致的。
             */
            val zh = cc.optJSONArray("lang_zh")?.optJSONObject(0)?.optString("value").orEmpty()
            val en = cc.optJSONArray("weatherDesc")?.optJSONObject(0)?.optString("value").orEmpty()
            val desc = if (zh.isNotBlank()) zh else en

            val sb = StringBuilder()
            sb.append("数据源：wttr\n")
            sb.append("当前：").append(desc).append("  ").append(tempC).append("℃")
            if (humidity.isNotBlank()) sb.append("  湿度").append(humidity)
            if (windKmph.isNotBlank()) sb.append("  风速").append(windKmph).append("km/h")
            sb.append("\n")

            // 未来预报：wttr 的数组名是 weather
            val weatherArr = obj.optJSONArray("weather")
            if (weatherArr != null && weatherArr.length() > 0) {
                sb.append("\n未来（取3天）：\n")
                val days = minOf(3, weatherArr.length())
                for (i in 0 until days) {
                    val d = weatherArr.optJSONObject(i) ?: continue
                    val date = d.optString("date", "")
                    val minC = d.optString("mintempC", "")
                    val maxC = d.optString("maxtempC", "")
                    // 取 12:00 的描述作为当天代表（更接近“白天状态”）
                    val descDay = pickWttrNoonDesc(d) ?: ""
                    sb.append("• ").append(date)
                    if (descDay.isNotBlank()) sb.append("  ").append(descDay)
                    if (minC.isNotBlank() || maxC.isNotBlank()) {
                        sb.append("  ").append(minC).append("/").append(maxC).append("℃")
                    }
                    sb.append("\n")
                }
            }
            sb.toString().trim()
        }.getOrElse { "解析失败：${it.message ?: it.javaClass.simpleName}" }
    }

    /**
     * pickWttrNoonDesc
     *
     * 从 wttr 的某一天（weather[i]）中，挑一个“代表性的描述”：
     * - 优先找 hourly 里 time == "1200"（中午）
     * - 找不到就兜底用第一个 hourly
     *
     * 这样展示出来的“未来3天描述”会更贴近白天，而不是凌晨。
     */
    private fun pickWttrNoonDesc(dayObj: JSONObject): String? {
        val hourly = dayObj.optJSONArray("hourly") ?: return null

        // 优先取中午 12:00
        for (i in 0 until hourly.length()) {
            val h = hourly.optJSONObject(i) ?: continue
            if (h.optString("time", "") == "1200") {
                val zh = h.optJSONArray("lang_zh")?.optJSONObject(0)?.optString("value").orEmpty()
                val en = h.optJSONArray("weatherDesc")?.optJSONObject(0)?.optString("value").orEmpty()
                return if (zh.isNotBlank()) zh else en
            }
        }

        // 兜底：取第一个小时数据
        val h0 = hourly.optJSONObject(0) ?: return null
        val zh = h0.optJSONArray("lang_zh")?.optJSONObject(0)?.optString("value").orEmpty()
        val en = h0.optJSONArray("weatherDesc")?.optJSONObject(0)?.optString("value").orEmpty()
        return if (zh.isNotBlank()) zh else en
    }
}
