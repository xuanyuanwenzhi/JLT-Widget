package com.example.myapplication

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * DeveloperNoteActivity
 * - 一个纯展示页面：放“使用说明 / 开发者留言 / 开源地址 / 许可说明 / 版本号”等文本
 * - 用 Jetpack Compose 渲染 UI
 *
 * 设计要点：
 * - 内容比较长，所以使用 verticalScroll
 * - 只提供“返回”按钮，不做复杂交互
 */
class DeveloperNoteActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 让内容绘制到系统状态栏/导航栏区域（沉浸式边到边）
        enableEdgeToEdge()

        // 设置 Compose 内容：把 Activity 的 finish() 作为返回回调传入
        setContent {
            DeveloperNoteScreen(onBack = { finish() })
        }
    }
}

/**
 * 开发者留言 UI
 * @param onBack 点击“返回”时调用（由 Activity 传入，一般是 finish()）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeveloperNoteScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    // 保存滚动状态：避免重组时滚动位置丢失
    val scroll = rememberScrollState()

    // 你的 GitHub 仓库地址（统一放在这里，后面改链接也方便）
    val githubUrl = "https://github.com/xuanyuanwenzhi/JLT-Widget"

    /**
     * 页面展示的长文本内容
     * 备注：
     * - 这里直接写死在代码中，方便快速改动，但不利于多语言/后续维护
     * - 若未来要国际化或让内容可配置，可以迁移到 string resource / markdown 文件等
     */
    val noteText = """
使用教程：
长按主页面壁纸空白处，点击“卡片”&“插件”&“小组件”&“widget”，里面找一下就好了，找到拖到屏幕上就行，点击就能用
注：第一次点击可能会进软件，点一下复原就好了
再注：连点复原会清除次数,慎重
再再注：我那个天气的widget没加点击次数统计，懒得加了，后续版本会加


开发者留言版
嗯……我还没有完善好，暂时能用
赶时间，就先这样吧
我还没测试有没有什么 bug，好像有一些但是我没改好
不过能用就行
我对 Java 和 Kotlin 一窍不通，自学第一次写这么多快给我写崩了
那个开关后台6小时自动获取天气的那个按钮关了就行，我也不知道是权限问题还是什么，测试发现根本没用，唉（我现在新版本把那个功能删除掉了）
不过好在点击widget可以触发获取天气
已经在 github 上面开源，
$githubUrl
✅ 允许复制、修改、分发该作品
✅ 但是请署名原作者
❌ 未经允许禁止将作品用于任何商业目的

对了，github库的代码注释是我用chatgpt加的，没细看

不知道再写点啥
该软件由 jlt 提出设想

版本：1.2 测试版
""".trimIndent()

    /**
     * Scaffold：标准页面结构
     * - topBar：顶部标题栏 + 返回按钮
     * - content：正文区域（可滚动）
     */
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("开发者留言") },
                // 这里使用 TextButton 当返回按钮（简单直观）
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("返回") }
                }
            )
        }
    ) { pad ->
        // 正文：一列布局，整体可滚动，避免长文被截断
        Column(
            modifier = Modifier
                .padding(pad)                 // Scaffold 留出的内边距（避免被 topBar 覆盖）
                .fillMaxSize()
                .verticalScroll(scroll)       // 让长文支持滚动
                .padding(16.dp),              // 页面统一留白
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 用 OutlinedCard 包住文本，让视觉上像“说明卡片”
            OutlinedCard(Modifier.fillMaxWidth()) {
                Text(
                    modifier = Modifier.padding(14.dp),
                    text = noteText,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // ✅ 新增：打开 GitHub 的按钮
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(githubUrl)).apply {
                        // 用新任务打开浏览器更稳（有些机型/环境更兼容）
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            ) {
                Text("打开 GitHub 仓库")
            }
        }
    }
}
