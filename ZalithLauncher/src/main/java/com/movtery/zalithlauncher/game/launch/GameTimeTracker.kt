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

package com.movtery.zalithlauncher.game.launch

import com.movtery.zalithlauncher.path.PathManager
import com.movtery.zalithlauncher.utils.logging.Logger.lInfo
import com.movtery.zalithlauncher.utils.logging.Logger.lWarning
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 游戏时间追踪器
 * 记录每个版本的游戏时间，支持按天统计
 */
object GameTimeTracker {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private var currentSession: GameSession? = null

    @Serializable
    data class GameSession(
        val versionName: String,
        val startTimeMillis: Long
    )

    @Serializable
    data class PlayTimeEntry(
        val date: String, // yyyy-MM-dd
        val durationSeconds: Long
    )

    @Serializable
    data class VersionPlayTime(
        val versionName: String,
        var totalSeconds: Long = 0L,
        val sessions: MutableList<PlayTimeEntry> = mutableListOf(),
        var lastPlayedMillis: Long = 0L
    )

    @Serializable
    data class PlayTimeData(
        val versions: MutableMap<String, VersionPlayTime> = mutableMapOf()
    )

    private fun getDataFile(): File = File(PathManager.DIR_DATA, "play_time.json")

    private fun loadData(): PlayTimeData {
        val file = getDataFile()
        if (!file.exists()) return PlayTimeData()
        return try {
            json.decodeFromString<PlayTimeData>(file.readText())
        } catch (e: Exception) {
            lWarning("Failed to load play time data", e)
            PlayTimeData()
        }
    }

    private fun saveData(data: PlayTimeData) {
        try {
            val file = getDataFile()
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(data))
        } catch (e: Exception) {
            lWarning("Failed to save play time data", e)
        }
    }

    /**
     * 开始记录游戏会话
     */
    fun startSession(versionName: String) {
        currentSession = GameSession(
            versionName = versionName,
            startTimeMillis = System.currentTimeMillis()
        )
        lInfo("Game time tracking started for version: $versionName")
    }

    /**
     * 结束当前会话并保存时间
     */
    fun endSession() {
        val session = currentSession ?: return
        currentSession = null

        val endTime = System.currentTimeMillis()
        val durationSeconds = (endTime - session.startTimeMillis) / 1000L

        if (durationSeconds < 5) {
            // 忽略过短的会话（可能是启动失败）
            return
        }

        val date = LocalDate.now().toString() // yyyy-MM-dd

        val data = loadData()
        val versionPlayTime = data.versions.getOrPut(session.versionName) {
            VersionPlayTime(versionName = session.versionName)
        }

        versionPlayTime.totalSeconds += durationSeconds
        versionPlayTime.lastPlayedMillis = endTime

        // 合并同一天的记录
        val existingEntry = versionPlayTime.sessions.find { it.date == date }
        if (existingEntry != null) {
            val index = versionPlayTime.sessions.indexOf(existingEntry)
            versionPlayTime.sessions[index] = existingEntry.copy(
                durationSeconds = existingEntry.durationSeconds + durationSeconds
            )
        } else {
            versionPlayTime.sessions.add(PlayTimeEntry(date = date, durationSeconds = durationSeconds))
        }

        // 保留最近30天的详细记录
        val cutoffDate = LocalDate.now().minusDays(30).toString()
        versionPlayTime.sessions.removeAll { it.date < cutoffDate }

        saveData(data)
        lInfo("Game session ended for ${session.versionName}: ${formatDuration(durationSeconds)}")
    }

    /**
     * 获取某个版本的总游戏时间
     */
    fun getTotalPlayTime(versionName: String): Long {
        return loadData().versions[versionName]?.totalSeconds ?: 0L
    }

    /**
     * 获取所有版本的游戏时间
     */
    fun getAllPlayTimes(): Map<String, VersionPlayTime> {
        return loadData().versions
    }

    /**
     * 获取某个版本今天的游戏时间
     */
    fun getTodayPlayTime(versionName: String): Long {
        val today = LocalDate.now().toString()
        return loadData().versions[versionName]
            ?.sessions?.find { it.date == today }
            ?.durationSeconds ?: 0L
    }

    /**
     * 获取某个版本本周的游戏时间
     */
    fun getWeekPlayTime(versionName: String): Long {
        val weekStart = LocalDate.now().minusDays(7).toString()
        return loadData().versions[versionName]
            ?.sessions
            ?.filter { it.date >= weekStart }
            ?.sumOf { it.durationSeconds } ?: 0L
    }

    /**
     * 格式化时长为人类可读字符串
     */
    fun formatDuration(totalSeconds: Long): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "${totalSeconds}s"
        }
    }
}
