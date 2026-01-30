package com.example.myapplication

import android.content.Intent
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * MainActivity（主界面 / 设置页入口）
 *
 * 这个 Activity 是应用的 Launcher Activity（Manifest 里设为 MAIN/LAUNCHER）。
 * 设计上它不做复杂逻辑，直接用 Jetpack Compose 渲染一个“设置页”：
 * - 动画开关（控制点击小组件时是否弹动画 Activity）
 * - 教程与开发者留言入口
 * - 天气相关入口（数据源设置、调试面板）
 * - “复原”按钮（重置小组件抽签/抽星状态）
 * - 点击计数显示（每秒轮询刷新）
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Compose 的入口：把整个页面交给 SettingsScreen()
        setContent { SettingsScreen() }
    }
}

/**
 * SettingsScreen（主设置页面 UI）
 *
 * Compose 页面结构：
 * - Scaffold：提供 SnackbarHost 等基础页面骨架
 * - Column：纵向滚动布局，放多个设置卡片
 *
 * 这里的状态主要来自 SharedPreferences（AppSettings），并通过 Compose state 显示/更新。
 */
@Composable
private fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val scroll = rememberScrollState()

    // -------------------------
    // 1) 动画开关（持久化到 AppSettings）
    // -------------------------
    // 页面初始化时，从 prefs 读当前开关状态
    var animEnabled by remember { mutableStateOf(AppSettings.isAnimEnabled(context)) }

    /**
     * 页面打开时：同步一次 WeatherAutoUpdater 的任务状态
     * 目的：
     * - 防止用户在别的页面改了 prefs，但自动更新调度没跟上（例如 WorkManager/定时任务）
     * 备注：
     * - 这是一次性 effect（Unit 作为 key）
     */
    LaunchedEffect(Unit) {
        WeatherAutoUpdater.syncWithPrefs(context)
    }

    // -------------------------
    // 2) 点击计数（每秒刷新一次显示）
    // -------------------------
    /**
     * clickCount 用于 UI 展示：
     * - 你这里选择“每秒轮询一次 SharedPreferences”
     * - 好处：实现简单，不需要额外的 Flow/LiveData
     * - 代价：一直有一个协程在跑（但每秒一次很轻量）
     */
    var clickCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            clickCount = AppSettings.prefs(context).getInt(AppSettings.KEY_CLICK_COUNT, 0)
            delay(1000)
        }
    }

    // -------------------------
    // 3) “复原”按钮：隐藏连点逻辑
    // -------------------------
    /**
     * windowOpen：是否处在“隐藏操作窗口期”
     * taps：窗口期内已经点了多少次
     * remainingMs：倒计时展示（每 100ms 更新一次）
     *
     * 目标效果：
     * - 第一次点“复原”：执行复原，并提示“1秒内连点6次可清空计数”
     * - 接下来 1 秒内连续点到 6 次：清空点击计数
     * - 超过 1 秒：窗口关闭，计数归零
     */
    var windowOpen by remember { mutableStateOf(false) }
    var taps by remember { mutableIntStateOf(0) }
    var remainingMs by remember { mutableIntStateOf(0) }

    /**
     * 当 windowOpen 变为 true 时启动倒计时：
     * - 初始 1000ms
     * - 每 100ms 减 100
     * - 结束后自动关闭 windowOpen，并清空 taps
     */
    LaunchedEffect(windowOpen) {
        if (!windowOpen) return@LaunchedEffect

        remainingMs = 1000
        while (remainingMs > 0 && windowOpen) {
            delay(100)
            remainingMs -= 100
        }

        // 时间到且窗口仍开着：自动复位
        if (windowOpen) {
            windowOpen = false
            taps = 0
        }
    }

    // -------------------------
    // UI：Scaffold + 页面内容
    // -------------------------
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)                 // Scaffold 留出来的 padding（避免被系统栏/顶部遮挡）
                .fillMaxSize()
                .verticalScroll(scroll)       // 设置页内容多，允许滚动
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // 页面标题
            Text("设置", style = MaterialTheme.typography.titleLarge)

            // -------------------------
            // 卡片：动画开关
            // -------------------------
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("动画显示", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (animEnabled) "开启：点击小组件会弹出动画" else "关闭：只更新小组件，不弹动画",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    // Switch：切换后立即写入 prefs，并弹 snackbar 提示
                    Switch(
                        checked = animEnabled,
                        onCheckedChange = { checked ->
                            animEnabled = checked
                            AppSettings.setAnimEnabled(context, checked)
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (checked) "已开启动画显示" else "已关闭动画显示"
                                )
                            }
                        }
                    )
                }
            }

            // -------------------------
            // 卡片：其他入口（教程与开发者留言）
            // -------------------------
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("其他", style = MaterialTheme.typography.titleMedium)

                    // 跳转到 DeveloperNoteActivity（纯文本说明页面）
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            context.startActivity(Intent(context, DeveloperNoteActivity::class.java))
                        }
                    ) {
                        Text("教程与开发者留言")
                    }
                }
            }

            // -------------------------
            // 卡片：天气入口
            // -------------------------
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("天气", style = MaterialTheme.typography.titleMedium)

                    // 天气数据源设置：城市输入 / key / 手动获取 / 显示缓存 等
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            context.startActivity(Intent(context, WeatherSourceSettingsActivity::class.java))
                        }
                    ) {
                        Text("天气数据源设置")
                    }

                    // 天气调试面板：用于强制切换场景、验证显示逻辑等
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            context.startActivity(Intent(context, WeatherDebugActivity::class.java))
                        }
                    ) {
                        Text("打开天气调试面板")
                    }

                    Text(
                        "提示：城市输入 / 手动获取天气 / key 设置 都在「天气数据源设置」里",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // -------------------------
            // 按钮：复原（带隐藏连点清空计数）
            // -------------------------
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    // 复原两类小组件的“已抽取状态”（让它们回到初始图）
                    AppSettings.resetAllWidgetsDrawStateOnly(context)
                    FortuneBambooWidget.forceUpdateAll(context)
                    StarJarWidget.forceUpdateAll(context)

                    // 第一次点：开启 1 秒隐藏窗口，并提示
                    if (!windowOpen) {
                        windowOpen = true
                        taps = 1
                        scope.launch {
                            snackbarHostState.showSnackbar("已复原。隐藏操作：1秒内连点6次可清空计数")
                        }
                    } else {
                        // 窗口期内继续点：累计 taps
                        taps += 1

                        // 达到阈值：清空点击计数并退出窗口
                        if (taps >= 6) {
                            AppSettings.clearClickCount(context)
                            windowOpen = false
                            taps = 0
                            remainingMs = 0
                            scope.launch { snackbarHostState.showSnackbar("点击计数已清空") }
                        }
                    }
                }
            ) {
                // 按钮文字：窗口期内显示剩余次数和倒计时
                if (!windowOpen) Text("复原")
                else Text("复原（隐藏计数：${8 - taps} 次，${remainingMs}ms）")
            }

            // -------------------------
            // 卡片：点击计数展示
            // -------------------------
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("当前点击计数：$clickCount")
                    Text("说明：桌面小组件每点一次 +1", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
