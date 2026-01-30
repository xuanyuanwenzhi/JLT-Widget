package com.example.myapplication

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * WeatherDebugActivity
 * - 天气调试面板入口 Activity（Compose UI）
 * - 用于“强制覆盖”天气 Widget 的显示场景，方便测试各种动画/图标
 * - 例如：不等真实天气，直接切到“白天-下雨/雾霾/大风”来预览
 */
class WeatherDebugActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 设置 Compose 内容
        setContent { WeatherDebugScreen() }
    }
}

/**
 * DebugSceneUi
 * - UI层展示用的“场景枚举”
 * - key：存进 SharedPreferences 的字符串（AppSettings.KEY_WEATHER_DEBUG_SCENE）
 * - label：下拉菜单里显示给用户看的文字
 */
private enum class DebugSceneUi(val key: String, val label: String) {
    AUTO("AUTO", "自动（不覆盖）"),          // 不覆盖：走真实时间段 + 缓存判断
    SUNRISE("SUNRISE", "日出"),             // 强制日出
    NIGHT("NIGHT", "夜晚"),                 // 强制夜晚
    DAY_NORMAL("DAY_NORMAL", "白天-晴"),     // 强制白天默认（晴/普通）
    DAY_HAZE("DAY_HAZE", "白天-雾霾"),       // 强制雾霾
    DAY_RAIN("DAY_RAIN", "白天-下雨"),       // 强制下雨
    DAY_SNOW("DAY_SNOW", "白天-下雪"),       // 强制下雪
    DAY_WIND("DAY_WIND", "白天-大风"),       // 强制大风
}

/**
 * WeatherDebugScreen
 * - 调试面板的 Compose UI
 * - 能开关“调试覆盖”+ 选择场景 + 刷新 Widget + 打开预览 Activity
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeatherDebugScreen() {
    val context = LocalContext.current
    val activity = context as? Activity

    // SnackbarHostState：用于未来需要弹提示（你现在没用 showSnackbar，但先留着）
    val snackbar = remember { SnackbarHostState() }

    // enabled：是否启用调试覆盖（存到 AppSettings.KEY_WEATHER_DEBUG_ENABLED）
    var enabled by remember { mutableStateOf(AppSettings.isWeatherDebugEnabled(context)) }

    // sceneKey：当前选中的场景 key（存到 AppSettings.KEY_WEATHER_DEBUG_SCENE）
    var sceneKey by remember { mutableStateOf(AppSettings.getWeatherDebugScene(context)) }

    // expand：控制 DropdownMenu 是否展开
    var expand by remember { mutableStateOf(false) }

    /**
     * 根据当前 sceneKey 找到对应的枚举项
     * - 如果找不到（比如老版本存了未知 key），就回退 AUTO
     */
    fun currentScene(): DebugSceneUi {
        return DebugSceneUi.entries.firstOrNull { it.key == sceneKey } ?: DebugSceneUi.AUTO
    }

    /**
     * 预览按钮要打开哪个 Activity
     * - AUTO / DAY_NORMAL：用 WeatherDayActivity
     * - 其他：按场景打开对应 Activity（雨/雪/雾霾/大风/日出/夜晚）
     */
    fun previewActivityFor(scene: DebugSceneUi): Class<*> {
        return when (scene) {
            DebugSceneUi.SUNRISE -> WeatherSunriseActivity::class.java
            DebugSceneUi.NIGHT -> WeatherNightActivity::class.java
            DebugSceneUi.DAY_HAZE -> WeatherHazeActivity::class.java
            DebugSceneUi.DAY_RAIN -> WeatherRainActivity::class.java
            DebugSceneUi.DAY_SNOW -> WeatherSnowActivity::class.java
            DebugSceneUi.DAY_WIND -> WeatherWindActivity::class.java
            DebugSceneUi.DAY_NORMAL, DebugSceneUi.AUTO -> WeatherDayActivity::class.java
        }
    }

    /**
     * Scaffold：标准页面结构
     * - TopAppBar：标题 + 返回
     * - snackbarHost：统一承载 SnackBar（可选）
     */
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("天气调试面板") },
                navigationIcon = {
                    IconButton(onClick = { activity?.finish() }) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { pad ->

        // 页面主体：一列布局，卡片里放开关、下拉选择、操作按钮
        Column(
            modifier = Modifier
                .padding(pad)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // 主卡片：调试覆盖设置
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {

                    // 第一行：启用调试覆盖 开关
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("启用调试覆盖", style = MaterialTheme.typography.titleMedium)
                            Text(
                                // 开启：Widget 的显示/点击都走你选的场景；关闭：走真实逻辑
                                if (enabled) "开启：Widget 显示/点击都按你选择的场景"
                                else "关闭：走真实时间 + 缓存判断",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        // Switch 只改本地状态，真正写入 prefs 在“保存并刷新Widget”按钮里做
                        Switch(
                            checked = enabled,
                            onCheckedChange = { enabled = it }
                        )
                    }

                    // 场景选择按钮：点了展开 DropdownMenu
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { expand = true }
                    ) {
                        Text("当前场景：${currentScene().label}")
                    }

                    // 下拉菜单：列出所有场景
                    DropdownMenu(
                        expanded = expand,
                        onDismissRequest = { expand = false }
                    ) {
                        DebugSceneUi.entries.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s.label) },
                                onClick = {
                                    // 只修改内存状态，真正写入 prefs 在“保存并刷新Widget”
                                    sceneKey = s.key
                                    expand = false
                                }
                            )
                        }
                    }

                    // 操作按钮：保存刷新 / 打开预览
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {

                        /**
                         * 保存并刷新 Widget：
                         * 1) 把 enabled/sceneKey 写入 AppSettings（SharedPreferences）
                         * 2) 给 WeatherTimeWidget 发广播，让它立即 updateAllWidgets
                         */
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                AppSettings.setWeatherDebugEnabled(context, enabled)
                                AppSettings.setWeatherDebugScene(context, sceneKey)

                                // 发送广播给 Widget，让它立刻刷新图（ACTION_REFRESH）
                                context.sendBroadcast(
                                    Intent(context, WeatherTimeWidget::class.java).apply {
                                        action = "com.example.myapplication.WEATHER_REFRESH"
                                    }
                                )
                            }
                        ) {
                            Text("保存并刷新Widget")
                        }

                        /**
                         * 打开预览：
                         * - 直接启动对应场景的 Activity，看动画效果
                         * - 不依赖桌面 Widget，也不需要真实天气满足条件
                         */
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val clz = previewActivityFor(currentScene())
                                context.startActivity(Intent(context, clz))
                            }
                        ) {
                            Text("打开预览")
                        }
                    }

                    // 简短提示文案：告诉用户这个面板的用途
                    Text(
                        "提示：把场景选成“白天-下雨/雾霾/大风”等，就能随时测试对应 Activity 动画，不用等真实天气。（主要是我做测试的时候我找不到对应天气的城市，加了这个调试页面，懒得删除了）",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
