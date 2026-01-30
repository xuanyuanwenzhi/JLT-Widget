package com.example.myapplication

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * WeatherAutoUpdater
 *
 * 这个文件负责“是否启用后台自动更新天气”的调度管理（使用 WorkManager）。
 *
 * 它不直接请求天气，也不直接解析天气；
 * 它只做两件事：
 * 1) 根据 AppSettings 的开关，创建/取消一个 6 小时一次的周期任务
 * 2) 提供一个“立刻更新一次”的一次性任务入口（调试/测试用）
 *
 *
 *
 * 相关文件：
 * - WeatherAutoUpdateWorker：真正执行联网获取 & 写入缓存的 Worker（核心逻辑在 Worker 里）
 * - AppSettings：保存 weather_auto_enabled / last_run_ms 等偏好设置
 * - WeatherTimeWidget：桌面小组件会读缓存展示；也可能触发 syncWithPrefs 让任务跟上设置
 */
object WeatherAutoUpdater {

    /**
     * 周期任务的唯一名称（6小时一次）
     * - 用 “unique” 的好处：不会重复创建很多份任务
     * - 后续更新策略可以通过 ExistingPeriodicWorkPolicy 控制
     */
    private const val UNIQUE_PERIODIC_NAME = "weather_auto_update_6h"

    /**
     * 一次性立即更新的唯一名称
     * - 用于调试/手动触发：enqueueUpdateNow()
     */
    private const val UNIQUE_ONETIME_NAME = "weather_update_now"

    /**
     * ✅ 兼容旧接口：WeatherTimeWidget 里可能还在调用 ensureScheduledIfEnabled()
     * 这里直接转到 syncWithPrefs()，避免你改名后其它地方编译报错。
     */
    fun ensureScheduledIfEnabled(context: Context) {
        syncWithPrefs(context)
    }

    /**
     * 根据偏好设置同步任务状态：
     * - 如果开关开启：确保 6 小时任务已安排
     * - 如果开关关闭：取消 6 小时任务
     *
     * 常见调用时机：
     * - 设置页面打开时（防止你手动改了 prefs，但任务还没跟着改）
     * - App 启动时/Widget 更新时（确保状态一致）
     *
     *
     * 那个按钮开关被我删了
     *
     */
    fun syncWithPrefs(context: Context) {
        if (AppSettings.isWeatherAutoEnabled(context)) {
            schedule6h(context)
        } else {
            cancel(context)
        }
    }

    /**
     * 开启自动更新：
     * - 写入 AppSettings 开关
     * - 立刻安排 6 小时周期任务
     */
    fun enable(context: Context) {
        AppSettings.setWeatherAutoEnabled(context, true)
        schedule6h(context)
    }

    /**
     * 关闭自动更新：
     * - 写入 AppSettings 开关
     * - 取消周期任务（不再后台跑）
     */
    fun disable(context: Context) {
        AppSettings.setWeatherAutoEnabled(context, false)
        cancel(context)
    }

    /**
     * 安排一个每 6 小时执行一次的周期任务：
     *
     * 约束（Constraints）：
     * - 需要联网（NetworkType.CONNECTED）
     *
     * 注意：
     * - WorkManager 的周期任务并不是“精确 6 小时整点触发”，系统会有一定的弹性调度
     * - ExistingPeriodicWorkPolicy.UPDATE：如果已存在同名任务，会用新的 request 替换更新
     */
    private fun schedule6h(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // 每 6 小时执行一次 WeatherAutoUpdateWorker
        val req = PeriodicWorkRequestBuilder<WeatherAutoUpdateWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            req
        )
    }

    /**
     * 取消周期任务：
     * - 只取消名为 UNIQUE_PERIODIC_NAME 的那一条
     * - 不影响一次性任务（enqueueUpdateNow）如果它已经在队列里
     */
    private fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_PERIODIC_NAME)
    }

    /**
     * 立刻触发一次天气更新（一次性任务）：
     *
     * 典型用途：
     * - 调试面板“立刻跑一次 Worker”
     * - 你想验证 Worker 是否能正常联网/写缓存
     *
     * 策略：
     * - ExistingWorkPolicy.REPLACE：如果之前已经排队了一个同名的“立即更新”，就替换它
     */
    fun enqueueUpdateNow(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val req = OneTimeWorkRequestBuilder<WeatherAutoUpdateWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_ONETIME_NAME,
            ExistingWorkPolicy.REPLACE,
            req
        )
    }
}
