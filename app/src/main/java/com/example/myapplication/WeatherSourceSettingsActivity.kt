package com.example.myapplication

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/**
 * WeatherSourceSettingsActivity
 * - 天气数据源设置页（Compose UI）
 * - 主要功能：
 *   1) 切换天气数据源：WTTR / WTTR_V2 / JUHE
 *   2) 配置 JUHE Key（仅 JUHE 使用）
 *   3) 手动测试天气接口（走缓存策略，支持显示缓存）
 *   4) 复制调试信息到剪贴板（包含原始 JSON）
 */
class WeatherSourceSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WeatherSourceSettingsScreen() }
    }
}

// 数据源字符串常量（写入 prefs 用）
private const val PROVIDER_JUHE = "JUHE"
private const val PROVIDER_WTTR = "WTTR"
private const val PROVIDER_WTTR_V2 = "WTTR_V2" // 备用域名 v2.wttr.in

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeatherSourceSettingsScreen() {
    // Compose 环境里的 Context（这里实际是 Activity）
    val context = LocalContext.current
    val activity = context as? Activity

    // 协程作用域：用于点击按钮后启动异步任务
    val scope = rememberCoroutineScope()

    // SnackBar：用于轻提示（保存成功、限频提示等）
    val snackbarHostState = remember { SnackbarHostState() }

    // 页面滚动：内容比较长
    val scroll = rememberScrollState()

    /**
     * 页面打开时同步一次：
     * - 如果 prefs 里开启了自动更新就安排 WorkManager
     * - 否则就取消（避免你手动改 prefs 后调度没跟上）
     */
    LaunchedEffect(Unit) {
        WeatherAutoUpdater.syncWithPrefs(context)
    }

    // -------------------------
    // 1) 数据源 + JUHE Key 区域
    // -------------------------

    /**
     * provider：当前数据源（状态）
     * - 从 prefs 读出后做一次 normalize（trim + uppercase）
     * - 如果读到奇怪值就默认 WTTR
     */
    var provider by remember {
        mutableStateOf(
            AppSettings.getWeatherProvider(context)
                .trim()
                .uppercase(Locale.getDefault())
                .let {
                    when (it) {
                        PROVIDER_JUHE, PROVIDER_WTTR, PROVIDER_WTTR_V2 -> it
                        else -> PROVIDER_WTTR
                    }
                }
        )
    }

    // JUHE Key 输入框的内容（直接写 prefs）
    var juheKey by remember { mutableStateOf(AppSettings.getJuheKey(context)) }

    // Key 是否隐藏（显示/隐藏切换按钮控制）
    var keyHidden by remember { mutableStateOf(false) }

    // -------------------------
    // 2) 天气测试区
    // -------------------------

    // 城市输入框：同步写入 prefs
    var city by remember { mutableStateOf(AppSettings.getWeatherCity(context)) }

    // 展示解析后的天气文本（WeatherApi.parseToText）
    var weatherText by remember { mutableStateOf("") }

    // loading：拉取中禁用按钮 + 改文字
    var loading by remember { mutableStateOf(false) }

    // 最近一次联网时间（显示用）
    var lastFetchMs by remember { mutableLongStateOf(AppSettings.getWeatherLastFetchMs(context)) }

    // 本次操作状态：比如“使用缓存/已联网更新/失败...”
    var cacheInfo by remember { mutableStateOf("未获取") }

    // -------------------------
    // 3) 轻度限频（防止疯狂点触发接口封禁）
    // -------------------------

    /**
     * 限频策略：10 秒最多 4 次
     * - windowStartMs：当前窗口起始时间
     * - windowCount：窗口内计数
     */
    var windowStartMs by remember { mutableLongStateOf(0L) }
    var windowCount by remember { mutableLongStateOf(0L) }
    val limitWindowMs = 10_000L
    val limitMaxCount = 4L

    // -------------------------
    // 工具方法（本地函数）
    // -------------------------

    /** 把毫秒时间戳格式化成可读的时间字符串 */
    fun formatTime(ms: Long): String {
        if (ms <= 0L) return "从未获取"
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(ms))
    }

    /** 把剩余毫秒数格式化成 "xh xm xs"（用于显示缓存剩余时间） */
    fun formatRemain(ms: Long): String {
        val s = max(0L, ms) / 1000L
        val h = s / 3600L
        val m = (s % 3600L) / 60L
        val sec = s % 60L
        return "${h}h ${m}m ${sec}s"
    }

    /** 数据源标题：用于 UI 显示更友好 */
    fun providerTitle(p: String): String = when (p) {
        PROVIDER_JUHE -> "JUHE（聚合数据）"
        PROVIDER_WTTR -> "WTTR（wttr.in）"
        PROVIDER_WTTR_V2 -> "WTTR 备用（v2.wttr.in）"
        else -> p
    }

    /** 数据源说明：用于提示用户各自的优缺点 */
    fun providerHint(p: String): String = when (p) {
        PROVIDER_WTTR ->
            "免费接口，国内可能慢/偶尔超时。适合不想申请 Key 的情况。"
        PROVIDER_WTTR_V2 ->
            "WTTR 的备用域名，有些网络下更稳定；同样免费。"
        PROVIDER_JUHE ->
            "需要你自己去聚合数据官网申请天气 API Key；免费版通常有每日次数限制。"
        else -> ""
    }

    /**
     * 把异常信息翻译成更“人话”的提示，方便用户排查：
     * - 网络
     * - WTTR 国内不稳
     * - JUHE key 为空/次数限制
     * - 城市名不规范
     * - 频繁点击限流
     */
    fun friendlyReason(e: Exception, providerNow: String, key: String): String {
        val msg = e.message.orEmpty()
        val p = providerNow.uppercase(Locale.getDefault())

        return buildString {
            append("请求失败：")
            append(e.javaClass.simpleName)
            if (msg.isNotBlank()) append("（$msg）")
            append("\n\n可能原因：\n")
            append("1) 当前网络连不上目标接口（校园网/公司网/代理/VPN/DNS/系统时间不准都可能影响）。\n")
            when (p) {
                PROVIDER_WTTR ->
                    append("2) WTTR（wttr.in）在国内可能会慢、超时，甚至直接连不上。\n")
                PROVIDER_WTTR_V2 ->
                    append("2) WTTR 备用（v2.wttr.in）仍可能慢/超时；你也可以试试切回 WTTR 或改用 JUHE。\n")
                PROVIDER_JUHE -> {
                    append("2) JUHE 需要填写 Key；免费版有次数限制。\n")
                    if (key.isBlank()) append("   - 你当前 Key 为空，所以必然失败。\n")
                }
            }
            append("3) 城市名不规范也可能导致接口返回空数据（尽量用中文城市名，如“武汉”“合肥”）。\n")
            append("4) 频繁点击可能触发限流/封禁，所以这里做了轻度限频（10 秒最多 4 次）。\n")
        }
    }

    /**
     * 一个小的 UI 组件：用于三个数据源切换按钮
     * selected 时把按钮文案前面加上“当前：”
     */
    @Composable
    fun ProviderButton(
        text: String,
        selected: Boolean,
        onClick: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        Button(
            onClick = onClick,
            modifier = modifier,
            enabled = true
        ) {
            Text(if (selected) "当前：$text" else text)
        }
    }

    /** 是否存在缓存：用于“显示缓存”按钮置灰判断 */
    fun hasCache(): Boolean = AppSettings.getWeatherCacheJson(context).isNotBlank()

    /**
     * 复制调试信息到剪贴板：
     * - 尽可能多地 dump 当前状态 + 缓存 JSON
     * - 方便你或别人提 issue / 排查 bug
     */
    fun copyDebugToClipboard() {
        val cacheJson = AppSettings.getWeatherCacheJson(context)
        val cacheCity = AppSettings.getWeatherCacheCity(context)
        val cacheProvider = AppSettings.getWeatherCacheProvider(context)
        val lastFetch = AppSettings.getWeatherLastFetchMs(context)

        val nowCity = AppSettings.getWeatherCity(context)
        val nowProvider = AppSettings.getWeatherProvider(context)
        val nowJuheKey = AppSettings.getJuheKey(context)

        val autoEnabled = AppSettings.isWeatherAutoEnabled(context)
        val autoLastRun = AppSettings.getWeatherAutoLastRunMs(context)

        val debugEnabled = AppSettings.isWeatherDebugEnabled(context)
        val debugScene = AppSettings.getWeatherDebugScene(context)

        val text = buildString {
            append("provider=").append(nowProvider).append("\n")
            append("juheKey=").append(nowJuheKey).append("\n")
            append("city=").append(nowCity).append("\n")
            append("lastFetchMs=").append(lastFetch).append("\n")
            append("autoEnabled=").append(autoEnabled).append("\n")
            append("autoLastRunMs=").append(autoLastRun).append("\n")
            append("cacheCity=").append(cacheCity).append("\n")
            append("cacheProvider=").append(cacheProvider).append("\n")
            append("cacheJsonLen=").append(cacheJson.length).append("\n")
            append("weatherDebugEnabled=").append(debugEnabled).append("\n")
            append("weatherDebugScene=").append(debugScene).append("\n")
            append("\n")
            append("cacheJson=\n")
            append(cacheJson)
        }

        val cm = context.getSystemService(ClipboardManager::class.java)
        cm?.setPrimaryClip(ClipData.newPlainText("weather_debug_dump", text))
    }

    // -------------------------
    // UI：Scaffold + 内容区
    // -------------------------
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("天气数据源设置") },
                navigationIcon = {
                    // 返回按钮：直接 finish
                    IconButton(onClick = { activity?.finish() }) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors()
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // =========================
            // 卡片 1：数据源选择 + Key
            // =========================
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("数据源", style = MaterialTheme.typography.titleMedium)
                    Text("当前：${providerTitle(provider)}", style = MaterialTheme.typography.bodySmall)
                    Text(providerHint(provider), style = MaterialTheme.typography.bodySmall)

                    // 三个数据源切换按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ProviderButton(
                            text = "WTTR",
                            selected = provider == PROVIDER_WTTR,
                            onClick = {
                                provider = PROVIDER_WTTR
                                AppSettings.setWeatherProvider(context, provider)
                                scope.launch { snackbarHostState.showSnackbar("已切换到 WTTR（wttr.in）") }
                            },
                            modifier = Modifier.weight(1f)
                        )

                        ProviderButton(
                            text = "WTTR_V2",
                            selected = provider == PROVIDER_WTTR_V2,
                            onClick = {
                                provider = PROVIDER_WTTR_V2
                                AppSettings.setWeatherProvider(context, provider)
                                scope.launch { snackbarHostState.showSnackbar("已切换到 WTTR 备用（v2.wttr.in）") }
                            },
                            modifier = Modifier.weight(1f)
                        )

                        ProviderButton(
                            text = "JUHE",
                            selected = provider == PROVIDER_JUHE,
                            onClick = {
                                provider = PROVIDER_JUHE
                                AppSettings.setWeatherProvider(context, provider)
                                scope.launch { snackbarHostState.showSnackbar("已切换到 JUHE（聚合数据）") }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // JUHE Key 输入框：实时写 prefs
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = juheKey,
                        onValueChange = { v ->
                            juheKey = v
                            AppSettings.setJuheKey(context, v)
                        },
                        singleLine = true,
                        label = { Text("聚合 Juhe Key") },
                        placeholder = { Text("仅 JUHE 需要填写 Key") },
                        visualTransformation = if (keyHidden) PasswordVisualTransformation() else VisualTransformation.None,
                        supportingText = {
                            // 根据当前 provider 给提示
                            if (provider == PROVIDER_JUHE) {
                                Text("提示：未填写 Key 会请求失败。免费 Key 通常有每日次数限制。")
                            } else {
                                Text("当前未使用 JUHE 时，可不填。")
                            }
                        }
                    )

                    // Key 显示/隐藏 + 保存刷新 widget
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(onClick = { keyHidden = !keyHidden }) {
                            Text(if (keyHidden) "显示 Key" else "隐藏 Key")
                        }

                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                // 这里简单粗暴：刷新天气 Widget，让它按新 provider/city 重新展示
                                WeatherTimeWidget.forceUpdateAll(context)
                                scope.launch { snackbarHostState.showSnackbar("已保存并刷新小组件") }
                            }
                        ) {
                            Text("保存并刷新小组件")
                        }
                    }

                    // 说明卡片：把常见问题写清楚，减少用户疑惑
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("备注", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "• WTTR（wttr.in）和 WTTR 备用（v2.wttr.in）都是免费接口，但在国内可能会慢、超时，甚至偶发连不上。\n" +
                                        "• WTTR 备用（v2.wttr.in）只是换了一个域名，有些网络下会更稳定，你可以互相切换试试。\n" +
                                        "• JUHE（聚合数据）需要你自己去官网注册获取天气 API Key（免费版通常有每日次数限制）。\n" +
                                        "• 因为免费 Key 有次数限制，制作者不内置自己的 Key，请你自行申请并填写。",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // =========================
            // 卡片 2：天气接口测试
            // =========================
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("天气接口测试", style = MaterialTheme.typography.titleMedium)

                    // 城市输入：实时写 prefs
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = city,
                        onValueChange = { v ->
                            city = v
                            AppSettings.setWeatherCity(context, v)
                        },
                        singleLine = true,
                        label = { Text("城市（手动输入）") },
                        placeholder = { Text("例如：合肥 / 北京 / 上海 / 武汉") }
                    )

                    // 展示最近一次联网时间 + 当前状态
                    Text("最近一次联网获取：${formatTime(lastFetchMs)}", style = MaterialTheme.typography.bodySmall)
                    Text("本次状态：$cacheInfo", style = MaterialTheme.typography.bodySmall)

                    // 两个按钮：手动获取天气 / 显示缓存
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            modifier = Modifier.weight(1f),
                            enabled = !loading && city.isNotBlank(),
                            onClick = {
                                val now = System.currentTimeMillis()

                                // ✅ 轻度限频：10 秒最多 4 次
                                if (windowStartMs == 0L || (now - windowStartMs) > limitWindowMs) {
                                    windowStartMs = now
                                    windowCount = 0L
                                }
                                windowCount += 1L
                                if (windowCount > limitMaxCount) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("点太快了：10 秒最多 $limitMaxCount 次，稍等再试")
                                    }
                                    return@Button
                                }

                                loading = true

                                // 开协程拉取（IO 放到 Dispatchers.IO）
                                scope.launch {
                                    val (text, info, fetchMs) = try {
                                        val result = withContext(Dispatchers.IO) {
                                            // force=false：优先走缓存（6h），除非城市/provider 变化
                                            WeatherApi.fetchWeatherSmartResult(context, city.trim(), force = false)
                                        }

                                        val parsed = WeatherApi.parseToText(result.rawJson)

                                        val status = if (result.usedCache) {
                                            "使用缓存（剩余 ${formatRemain(result.cacheRemainingMs)}）"
                                        } else {
                                            "已联网更新（6小时内将走缓存）"
                                        }

                                        Triple(parsed, status, result.lastFetchMs)
                                    } catch (e: Exception) {
                                        // 失败：给用户更友好的原因提示
                                        Triple(
                                            friendlyReason(e, provider, juheKey),
                                            "失败（未更新缓存）",
                                            AppSettings.getWeatherLastFetchMs(context)
                                        )
                                    }

                                    // 更新 UI 状态
                                    weatherText = text
                                    cacheInfo = info
                                    lastFetchMs = fetchMs
                                    loading = false
                                }
                            }
                        ) {
                            Text(if (loading) "获取中..." else "手动获取天气")
                        }

                        Button(
                            modifier = Modifier.weight(1f),
                            enabled = !loading && hasCache(),
                            onClick = {
                                // ✅ 只展示本地缓存，不联网
                                val cached = AppSettings.getWeatherCacheJson(context)
                                weatherText = WeatherApi.parseToText(cached)
                                cacheInfo = "显示本地缓存（不联网）"
                                lastFetchMs = AppSettings.getWeatherLastFetchMs(context)
                                scope.launch { snackbarHostState.showSnackbar("已显示缓存") }
                            }
                        ) {
                            Text("显示缓存")
                        }
                    }

                    // 展示“解析后的天气文本”
                    if (weatherText.isNotBlank()) {
                        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                modifier = Modifier.padding(12.dp),
                                text = weatherText
                            )
                        }
                    }
                }
            }

            // =========================
            // 卡片 3：调试工具（复制 dump）
            // =========================
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("调试工具", style = MaterialTheme.typography.titleMedium)

                    Text(
                        "复制内容包含：当前 provider / city / juheKey / 自动更新开关 / lastFetchMs / cacheCity / cacheProvider / cacheJson（原始 JSON）。",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        // 有缓存或曾经获取过就允许复制
                        enabled = hasCache() || AppSettings.getWeatherLastFetchMs(context) > 0L,
                        onClick = {
                            copyDebugToClipboard()
                            scope.launch { snackbarHostState.showSnackbar("已复制调试信息到剪贴板") }
                        }
                    ) {
                        Text("复制调试信息（含原始 JSON）")
                    }
                }
            }
        }
    }
}
