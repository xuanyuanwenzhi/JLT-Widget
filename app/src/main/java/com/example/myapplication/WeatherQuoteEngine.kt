package com.example.myapplication

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * WeatherQuoteEngine
 *
 * 作用：
 * - 从缓存的 rawJson 中“尽量”解析出：今日/明日天气描述、温度区间、风力等级、AQI
 * - 按时间段 + 是否今天首次点击，选择一条文案（早安/晚安/日常/天气类）
 *
 * 兼容性：
 * - ✅ 支持 Juhe（聚合）JSON：root.error_code + root.result.realtime/future
 * - ✅ 支持 WTTR JSON：root.current_condition + root.weather（你的缓存里就是这个结构）
 *
 * 重要策略：
 * - 大风阈值：✅改成 5 级及以上算大风（原来是 4）
 * - 雾霾策略：需要“雾/霾”字样 或 AQI>=100 才认为是雾霾
 *   - 但 WTTR 本身不提供 AQI，这种情况下只能靠文字判断（雾/霾）
 */
object WeatherQuoteEngine {

    /** ✅ 大风判定阈值：5 级及以上算大风 */
    private const val WIND_STRONG_LEVEL = 5

    /** WTTR 风速 km/h -> 风力等级（近似换算），用于大风判断 */
    private fun kmphToWindLevel(kmph: Int?): Int? {
        if (kmph == null) return null
        // 参考蒲福风级（近似）：用 km/h 范围映射到 0~12 级
        return when {
            kmph < 1 -> 0
            kmph < 6 -> 1
            kmph < 12 -> 2
            kmph < 20 -> 3
            kmph < 29 -> 4
            kmph < 39 -> 5
            kmph < 50 -> 6
            kmph < 62 -> 7
            kmph < 75 -> 8
            kmph < 89 -> 9
            kmph < 103 -> 10
            kmph < 118 -> 11
            else -> 12
        }
    }

    /** 温度上下限：例如 min=1 max=17 */
    data class TempPair(val min: Int, val max: Int) {
        /** 平均温（四舍五入） */
        val avg: Int get() = ((min + max) / 2.0).roundToInt()
    }

    /**
     * Snap：一次“天气快照”
     * - 只存我们挑选文案时会用到的字段
     */
    data class Snap(
        val aqi: Int?,
        val todayInfo: String?,
        val tomorrowInfo: String?,
        val todayTemp: TempPair?,
        val tomorrowTemp: TempPair?,
        val windLevelToday: Int?,
        val windLevelTomorrow: Int?
    )

    /**
     * 对外入口：从 rawJson 里挑一句文案
     *
     * @param rawJson 缓存的天气原始 JSON（可能来自 Juhe 或 WTTR）
     * @param isFirstToday 今天是否首次点击（用于早安文案触发）
     */
    fun pickQuote(rawJson: String, isFirstToday: Boolean): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val snap = parse(rawJson)

        // 早安：6~8 且“今天第一次点击”
        if (hour in 6..8 && isFirstToday) {
            return WeatherQuotes.MORNING.random()
        }

        // 按时间段给不同类型的文案概率
        return when {
            // 白天：日常关心 80% + 今日天气 20%
            hour in 9..17 -> {
                if (Random.nextInt(100) < 20) pickWeatherLine(snap, showTomorrow = false)
                else WeatherQuotes.CARE.random()
            }

            // 18~19：明日天气 30% + 日常关心 70%
            hour in 18..19 -> {
                if (Random.nextInt(100) < 30) pickWeatherLine(snap, showTomorrow = true)
                else WeatherQuotes.CARE.random()
            }

            // 20~21：明日 30% + 晚安 30% + 日常 40%
            hour in 20..21 -> {
                val r = Random.nextInt(100)
                when {
                    r < 30 -> pickWeatherLine(snap, showTomorrow = true)
                    r < 60 -> WeatherQuotes.GOOD_NIGHT.random()
                    else -> WeatherQuotes.CARE.random()
                }
            }

            // 22~23 & 0~5：明日 30% + 晚安 50% + 日常 20%
            else -> {
                val r = Random.nextInt(100)
                when {
                    r < 30 -> pickWeatherLine(snap, showTomorrow = true)
                    r < 80 -> WeatherQuotes.GOOD_NIGHT.random()
                    else -> WeatherQuotes.CARE.random()
                }
            }
        }
    }

    /**
     * 选“天气类”文案：
     * - 可能同时满足多个条件（比如：下雨 + 大风 + 降温）
     * - 会把所有满足的类别塞进 pool，最后随机抽一条（让表现更自然）
     */
    private fun pickWeatherLine(s: Snap, showTomorrow: Boolean): String {
        val pool = mutableListOf<String>()

        val info = (if (showTomorrow) s.tomorrowInfo else s.todayInfo).orEmpty()
        val aqi = s.aqi
        val wind = if (showTomorrow) s.windLevelTomorrow else s.windLevelToday

        // 1) 雾霾：尽量要求 AQI（Juhe 有），WTTR 没有 AQI 就只能靠文字
        if (isHaze(info, aqi)) {
            val base = (if (showTomorrow) WeatherQuotes.TOMORROW_HAZE else WeatherQuotes.TODAY_HAZE).random()
            // AQI 为空时用 -1 占位（你的文案里一般会写“{AQI}”）
            pool += base.replace("{AQI}", (aqi ?: -1).toString())
        }

        // 2) 大风：✅ 5级以上算大风
        if ((wind ?: 0) >= WIND_STRONG_LEVEL) {
            pool += (if (showTomorrow) WeatherQuotes.TOMORROW_WIND else WeatherQuotes.TODAY_WIND).random()
        }

        // 3) 雨 / 晴（简单按关键字判断）
        if (info.contains("雨")) {
            pool += (if (showTomorrow) WeatherQuotes.TOMORROW_RAIN else WeatherQuotes.TODAY_RAIN).random()
        } else if (info.contains("晴")) {
            pool += (if (showTomorrow) WeatherQuotes.TOMORROW_SUNNY else WeatherQuotes.TODAY_SUNNY).random()
        }

        // 4) 温度趋势（只在 showTomorrow=true 时才有意义）
        if (showTomorrow && s.todayTemp != null && s.tomorrowTemp != null) {
            val coolMin = s.todayTemp.min - s.tomorrowTemp.min  // 正数=变冷
            val coolAvg = s.todayTemp.avg - s.tomorrowTemp.avg
            val warmAvg = s.tomorrowTemp.avg - s.todayTemp.avg

            if (coolMin >= 3) pool += WeatherQuotes.COOL_MIN.random().replace("{DELTA}", coolMin.toString())
            if (coolAvg >= 3) pool += WeatherQuotes.COOL_AVG.random().replace("{DELTA}", coolAvg.toString())
            if (warmAvg >= 3) pool += WeatherQuotes.WARM_AVG.random().replace("{DELTA}", warmAvg.toString())
        }

        // 满足多个条件 -> 从池子里随机抽一个
        if (pool.isNotEmpty()) return pool.random()

        // 都不满足：兜底给日常关心，避免空白
        return WeatherQuotes.CARE.random()
    }

    /**
     * 判断是否雾霾倾向：
     * - Juhe：AQI>=100 或 文本含“雾/霾”
     * - WTTR：无 AQI，只能靠文本含“雾/霾”
     */
    private fun isHaze(info: String, aqi: Int?): Boolean {
        val byText = info.contains("霾") || info.contains("雾")
        val byAqi = (aqi ?: 0) >= 100
        return byText || byAqi
    }

    /**
     * 统一解析入口：
     * - 先尝试识别 Juhe（有 error_code）
     * - 否则尝试识别 WTTR（有 current_condition / weather）
     * - 都失败则返回全空 Snap（上层会走兜底文案）
     */
    fun parse(rawJson: String): Snap {
        if (rawJson.isBlank()) return Snap(null, null, null, null, null, null, null)

        // 1) Juhe（聚合）
        runCatching {
            val root = JSONObject(rawJson)
            if (root.has("error_code")) {
                return parseJuhe(root)
            }
        }

        // 2) WTTR（wttr.in）
        return runCatching {
            val root = JSONObject(rawJson)
            if (root.has("current_condition") || root.has("weather")) {
                return parseWttr(root)
            }
            Snap(null, null, null, null, null, null, null)
        }.getOrElse {
            Snap(null, null, null, null, null, null, null)
        }
    }

    /**
     * 解析 Juhe JSON（只取我们需要的字段）
     *
     * 结构大致：
     * {
     *   "error_code":0,
     *   "result":{
     *     "realtime":{"info":"晴","aqi":"45","power":"3级", ...},
     *     "future":[
     *        {"temperature":"1/17℃","weather":"晴","direct":"3级", ...},   // 今天
     *        {"temperature":"0/12℃","weather":"小雨","direct":"4-5级", ...} // 明天
     *     ]
     *   }
     * }
     */
    private fun parseJuhe(root: JSONObject): Snap {
        val result = root.optJSONObject("result")
        val realtime = result?.optJSONObject("realtime")

        val aqi = realtime?.optString("aqi", null)?.toIntOrNull()
        val todayInfo = realtime?.optString("info", null)

        // realtime.power 常见："3级" / "4-5级"
        val windToday = parseWindLevel(realtime?.optString("power", null))

        val future = result?.optJSONArray("future")
        val day0 = future?.optJSONObject(0) // 今天
        val day1 = future?.optJSONObject(1) // 明天

        val todayTemp = parseTempPair(day0?.optString("temperature", null))
        val tomorrowTemp = parseTempPair(day1?.optString("temperature", null))

        val tomorrowInfo = day1?.optString("weather", null)

        // 注意：Juhe 的 day1.direct 有时只是方向（如“东北风”），也可能带级别（“4-5级”）
        val windTomorrow = parseWindLevel(day1?.optString("direct", null))

        return Snap(
            aqi = aqi,
            todayInfo = todayInfo,
            tomorrowInfo = tomorrowInfo,
            todayTemp = todayTemp,
            tomorrowTemp = tomorrowTemp,
            windLevelToday = windToday,
            windLevelTomorrow = windTomorrow
        )
    }

    /**
     * 解析 WTTR JSON（你贴出来的缓存就是这个结构）
     *
     * 结构大致：
     * {
     *   "current_condition":[{"temp_C":"3","windspeedKmph":"19","lang_zh-cn":[{"value":"毛毛雨"}], ...}],
     *   "weather":[
     *     {"date":"2026-01-30","mintempC":"4","maxtempC":"4","hourly":[{"time":"1200","lang_zh-cn":[{"value":"毛毛雨"}], ...}]},
     *     {"date":"2026-01-31",...},
     *   ]
     * }
     *
     * 注意：
     * - WTTR 没 AQI，所以 aqi 一律为 null
     * - 今日/明日描述：优先取每天 12:00 的描述（更像“白天代表”）
     * - 风力等级：用 current_condition 的 windspeedKmph 近似映射到风级；
     *            明天风力由于 hourly 太多，这里也取明天 12:00 的 windspeedKmph 来换算
     */
    private fun parseWttr(root: JSONObject): Snap {
        val cc = root.optJSONArray("current_condition")?.optJSONObject(0)

        // 当前描述：优先中文 lang_zh-cn，否则 weatherDesc 英文
        val todayInfoNow = pickWttrDesc(cc)

        // 当前风速 km/h -> 风级
        val windToday = kmphToWindLevel(cc?.optString("windspeedKmph", null)?.toIntOrNull())

        // 未来 3 天数组：0=今天，1=明天
        val weatherArr = root.optJSONArray("weather")
        val day0 = weatherArr?.optJSONObject(0)
        val day1 = weatherArr?.optJSONObject(1)

        // 今日/明日温度：用 mintempC/maxtempC
        val todayTemp = parseTempPairFromMinMaxC(day0)
        val tomorrowTemp = parseTempPairFromMinMaxC(day1)

        // 今日/明日描述：优先取 12:00 的 hourly 描述；取不到再退回 current_condition
        val todayInfo = pickWttrNoonDesc(day0) ?: todayInfoNow
        val tomorrowInfo = pickWttrNoonDesc(day1) ?: pickWttrAnyDesc(day1)

        // 明天风力：取 12:00 的 windspeedKmph 近似映射风级
        val windTomorrow = kmphToWindLevel(pickWttrNoonWindKmph(day1))

        return Snap(
            aqi = null,
            todayInfo = todayInfo,
            tomorrowInfo = tomorrowInfo,
            todayTemp = todayTemp,
            tomorrowTemp = tomorrowTemp,
            windLevelToday = windToday,
            windLevelTomorrow = windTomorrow
        )
    }

    /** 解析 Juhe 的温度区间字符串：形如 "1/17℃" 或 "0/12℃" */
    private fun parseTempPair(text: String?): TempPair? {
        if (text.isNullOrBlank()) return null
        val cleaned = text.replace("℃", "").trim()
        val parts = cleaned.split("/")
        if (parts.size != 2) return null
        val min = parts[0].trim().toIntOrNull() ?: return null
        val max = parts[1].trim().toIntOrNull() ?: return null
        return TempPair(min, max)
    }

    /** 解析 WTTR 的 min/max（mintempC / maxtempC） */
    private fun parseTempPairFromMinMaxC(dayObj: JSONObject?): TempPair? {
        if (dayObj == null) return null
        val min = dayObj.optString("mintempC", null)?.toIntOrNull() ?: return null
        val max = dayObj.optString("maxtempC", null)?.toIntOrNull() ?: return null
        return TempPair(min, max)
    }

    /**
     * 从文本里提取风力等级：
     * - 常见："3级" / "4-5级"
     * - 会把出现的数字都抓出来，取最大值作为“等级”
     */
    private fun parseWindLevel(text: String?): Int? {
        if (text.isNullOrBlank()) return null
        val digits = Regex("""\d+""").findAll(text).mapNotNull { it.value.toIntOrNull() }.toList()
        if (digits.isEmpty()) return null
        return digits.maxOrNull()
    }

    /** WTTR：优先中文 lang_zh-cn，否则英文 weatherDesc */
    private fun pickWttrDesc(obj: JSONObject?): String? {
        if (obj == null) return null

        // 你缓存里是 "lang_zh-cn"，有些示例代码里写 "lang_zh"（不要混）
        val zh = obj.optJSONArray("lang_zh-cn")?.optJSONObject(0)?.optString("value").orEmpty()
        if (zh.isNotBlank()) return zh

        val en = obj.optJSONArray("weatherDesc")?.optJSONObject(0)?.optString("value").orEmpty()
        return en.ifBlank { null }
    }

    /** WTTR：取某一天 12:00 的天气描述（更像“当天代表”） */
    private fun pickWttrNoonDesc(dayObj: JSONObject?): String? {
        val hourly = dayObj?.optJSONArray("hourly") ?: return null
        for (i in 0 until hourly.length()) {
            val h = hourly.optJSONObject(i) ?: continue
            if (h.optString("time", "") == "1200") {
                return pickWttrDesc(h)
            }
        }
        return null
    }

    /** WTTR：取某一天 12:00 的风速 km/h */
    private fun pickWttrNoonWindKmph(dayObj: JSONObject?): Int? {
        val hourly = dayObj?.optJSONArray("hourly") ?: return null
        for (i in 0 until hourly.length()) {
            val h = hourly.optJSONObject(i) ?: continue
            if (h.optString("time", "") == "1200") {
                return h.optString("windspeedKmph", null)?.toIntOrNull()
            }
        }
        return null
    }

    /** WTTR：兜底取第一条 hourly 的描述 */
    private fun pickWttrAnyDesc(dayObj: JSONObject?): String? {
        val hourly = dayObj?.optJSONArray("hourly") ?: return null
        val h0 = hourly.optJSONObject(0) ?: return null
        return pickWttrDesc(h0)
    }
}
