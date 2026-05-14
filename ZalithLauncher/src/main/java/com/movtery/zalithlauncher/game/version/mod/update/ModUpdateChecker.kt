/*
 * Zalith Launcher 2
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.movtery.zalithlauncher.game.version.mod.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.download.assets.platform.PlatformVersion
import com.movtery.zalithlauncher.game.download.assets.platform.getVersionByLocalFile
import com.movtery.zalithlauncher.game.version.installed.Version
import com.movtery.zalithlauncher.game.version.installed.VersionFolders
import com.movtery.zalithlauncher.game.version.installed.VersionsManager
import com.movtery.zalithlauncher.path.PathManager
import com.movtery.zalithlauncher.utils.file.computeSHA1
import com.movtery.zalithlauncher.utils.logging.Logger.lInfo
import com.movtery.zalithlauncher.utils.logging.Logger.lWarning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant

/**
 * 模组更新后台检查器
 * 定期检查已安装的模组是否有新版本，发送通知提醒用户
 */
object ModUpdateChecker {

    const val NOTIFICATION_CHANNEL_ID = "mod_updates"
    const val NOTIFICATION_ID = 2001

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    @Serializable
    data class ModUpdateCache(
        val lastCheckMillis: Long = 0L,
        val updatesAvailable: Int = 0,
        val checkedVersionName: String = "",
        val modUpdates: List<ModUpdateInfo> = emptyList()
    )

    @Serializable
    data class ModUpdateInfo(
        val fileName: String,
        val currentSha1: String,
        val latestVersionName: String?,
        val projectId: String?
    )

    private fun getCacheFile(): File = File(PathManager.DIR_CACHE, "mod_update_cache.json")

    private fun loadCache(): ModUpdateCache {
        val file = getCacheFile()
        if (!file.exists()) return ModUpdateCache()
        return try {
            json.decodeFromString<ModUpdateCache>(file.readText())
        } catch (e: Exception) {
            ModUpdateCache()
        }
    }

    private fun saveCache(cache: ModUpdateCache) {
        try {
            val file = getCacheFile()
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(cache))
        } catch (e: Exception) {
            lWarning("Failed to save mod update cache", e)
        }
    }

    /**
     * 创建通知渠道
     */
    fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Mod Updates",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications about available mod updates"
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    /**
     * 检查当前版本的模组更新
     * @return 有更新可用的模组数量
     */
    suspend fun checkForUpdates(context: Context): Int = withContext(Dispatchers.IO) {
        val currentVersion = VersionsManager.currentVersion.value ?: return@withContext 0
        val versionInfo = currentVersion.getVersionInfo() ?: return@withContext 0
        val mcVersion = versionInfo.minecraftVersion

        val modsDir = File(currentVersion.getGameDir(), VersionFolders.MOD.folderName)
        if (!modsDir.exists() || !modsDir.isDirectory) return@withContext 0

        val modFiles = modsDir.listFiles()?.filter {
            it.isFile && it.extension == "jar"
        } ?: return@withContext 0

        if (modFiles.isEmpty()) return@withContext 0

        lInfo("Checking ${modFiles.size} mods for updates...")

        var updatesFound = 0
        val updateInfos = mutableListOf<ModUpdateInfo>()

        // Проверяем каждый мод (максимум 30 за раз, чтобы не перегружать API)
        val modsToCheck = modFiles.take(30)

        for (modFile in modsToCheck) {
            try {
                val sha1 = modFile.computeSHA1()
                val platformVersion = getVersionByLocalFile(modFile, sha1)

                if (platformVersion != null) {
                    // Мод найден на платформе, проверяем есть ли более новая версия
                    // (Упрощённая проверка: если sha1 на платформе отличается от установленного)
                    val latestSha1 = platformVersion.platformSha1()
                    if (latestSha1 != null && latestSha1 != sha1) {
                        updatesFound++
                        updateInfos.add(ModUpdateInfo(
                            fileName = modFile.name,
                            currentSha1 = sha1,
                            latestVersionName = platformVersion.platformDisplayName(),
                            projectId = null
                        ))
                    }
                }
            } catch (e: Exception) {
                // Пропускаем мод при ошибке
                lWarning("Failed to check update for ${modFile.name}", e)
            }
        }

        // Сохраняем кэш
        saveCache(ModUpdateCache(
            lastCheckMillis = System.currentTimeMillis(),
            updatesAvailable = updatesFound,
            checkedVersionName = currentVersion.getVersionName(),
            modUpdates = updateInfos
        ))

        // Отправляем уведомление если есть обновления
        if (updatesFound > 0) {
            sendNotification(context, updatesFound, currentVersion.getVersionName())
        }

        lInfo("Mod update check complete: $updatesFound updates available")
        return@withContext updatesFound
    }

    /**
     * Получить количество доступных обновлений из кэша
     */
    fun getCachedUpdateCount(): Int = loadCache().updatesAvailable

    /**
     * Получить список обновлений из кэша
     */
    fun getCachedUpdates(): List<ModUpdateInfo> = loadCache().modUpdates

    /**
     * Время последней проверки (millis)
     */
    fun getLastCheckTime(): Long = loadCache().lastCheckMillis

    /**
     * Нужно ли выполнять проверку (раз в 12 часов)
     */
    fun shouldCheck(): Boolean {
        val lastCheck = getLastCheckTime()
        val twelveHours = 12 * 60 * 60 * 1000L
        return (System.currentTimeMillis() - lastCheck) > twelveHours
    }

    /**
     * Отправить уведомление о доступных обновлениях
     */
    private fun sendNotification(context: Context, updateCount: Int, versionName: String) {
        try {
            createNotificationChannel(context)

            val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Mod Updates Available")
                .setContentText("$updateCount mod(s) have updates for version $versionName")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()

            val manager = context.getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            lWarning("Failed to send mod update notification", e)
        }
    }

    /**
     * Очистить кэш обновлений
     */
    fun clearCache() {
        getCacheFile().delete()
    }
}
