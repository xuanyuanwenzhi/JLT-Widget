package com.example.myapplication

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * WeatherAutoUpdateWorker
 * - WorkManager 后台任务：负责“联网拉取天气 + 写入缓存 + 刷新天气小组件”
 * - 使用 CoroutineWorker：doWork() 本身就是挂起函数，适合网络/IO
 *
 * 触发来源：
 * - 周期任务（6小时一次）或一次性任务（enqueueUpdateNow）都会跑到这里
 */
class WeatherAutoUpdateWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    /**
     * doWork：WorkManager 执行入口
     * 返回：
     * - success()：本次任务成功完成
     * - retry()：本次失败，交给系统稍后重试
     */
    override suspend fun doWork(): ListenableWorker.Result = withContext(Dispatchers.IO) {
        try {
            // 读取当前设置的城市（用户在“天气数据源设置”里输入的）
            val city = AppSettings.getWeatherCity(applicationContext).trim()

            // 城市为空：不需要联网，直接结束（视为成功，避免无意义重试）
            if (city.isBlank()) return@withContext ListenableWorker.Result.success()

            // 拉取天气：force=false
            // - 缓存未过期且城市/provider 未变：走缓存
            // - 缓存过期或城市/provider 变更：会联网更新并写缓存
            WeatherApi.fetchWeatherSmartResult(applicationContext, city, force = false)

            // 记录本次后台任务执行时间（用于调试或页面展示）
            AppSettings.setWeatherAutoLastRunMs(applicationContext, System.currentTimeMillis())

            // 刷新桌面天气小组件：根据时间段/缓存内容决定显示哪张图
            WeatherTimeWidget.forceUpdateAll(applicationContext)

            // 正常结束
            ListenableWorker.Result.success()
        } catch (e: Exception) {
            // 网络波动/接口超时等：让 WorkManager 按策略稍后重试
            ListenableWorker.Result.retry()
        }
    }
}
